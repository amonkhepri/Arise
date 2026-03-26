package com.example.rise.briar.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.briarproject.bramble.api.Pair
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.contact.HandshakeLinkConstants
import org.briarproject.bramble.api.contact.PendingContact
import org.briarproject.bramble.api.contact.PendingContactState
import org.briarproject.bramble.api.contact.event.ContactAddedEvent
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent
import org.briarproject.bramble.api.db.PendingContactExistsException
import org.briarproject.bramble.api.event.Event
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.event.EventListener

/**
 * Thin adapter that mirrors contacts from the embedded Briar runtime into the
 * transport-facing [BriarContactService].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealBriarContactService(
    private val readiness: StateFlow<BriarReadinessStatus>,
    private val contactManager: ContactManager,
    private val eventBus: EventBus,
) : BriarContactService, EventListener {

    private val contacts = MutableStateFlow<List<BriarContact>>(emptyList())

    init {
        // Seed with the current roster and subscribe for changes.
        refreshContacts(reason = "init")
        eventBus.addListener(this)
        log("Listening for Briar contact events")
    }

    override val isAvailable: Boolean
        get() = readiness.value is BriarReadinessStatus.Ready

    override fun availability() =
        readiness
            .map { it is BriarReadinessStatus.Ready }
            .distinctUntilChanged()

    override fun observeContacts(): Flow<List<BriarContact>> =
        readiness.flatMapLatest { status ->
            if (status is BriarReadinessStatus.Ready) contacts.asStateFlow() else flowOf(emptyList())
        }

    override suspend fun addContactByLink(link: String, alias: String?) {
        if (readiness.value !is BriarReadinessStatus.Ready) {
            throw IllegalStateException("Briar runtime is not ready")
        }
        if (!HandshakeLinkConstants.LINK_REGEX.matcher(link).find()) {
            throw IllegalArgumentException("Invalid Briar handshake link")
        }
        val sanitizedAlias = alias?.trim()
        val aliasToUse = if (sanitizedAlias.isNullOrEmpty()) DEFAULT_ALIAS else sanitizedAlias
        log("Submitting contact link alias=$aliasToUse link=${link.abbreviated()}")
        try {
            val submittedPendingContact = contactManager.addPendingContact(link, aliasToUse)
            refreshContacts(
                reason = "addContactByLink(alias=$aliasToUse)",
                surfacedPendingContacts = listOf(submittedPendingContact),
            )
        } catch (error: PendingContactExistsException) {
            refreshContacts(
                reason = "addContactByLink(alias=$aliasToUse, pendingExists)",
                surfacedPendingContacts = listOf(error.pendingContact),
            )
            throw error
        }
    }

    override fun getHandshakeLink(): String {
        if (readiness.value !is BriarReadinessStatus.Ready) {
            throw IllegalStateException("Briar runtime is not ready")
        }
        return contactManager.getHandshakeLink()
    }

    override fun eventOccurred(e: Event) {
        if (readiness.value !is BriarReadinessStatus.Ready) return
        when (e) {
            is ContactAddedEvent ->
                refreshContacts(reason = "ContactAddedEvent(contactId=${e.contactId.int})")
            is ContactRemovedEvent ->
                refreshContacts(reason = "ContactRemovedEvent(contactId=${e.contactId.int})")
            is PendingContactAddedEvent ->
                refreshContacts(reason = "PendingContactAddedEvent(id=${e.pendingContact.id})")
            is PendingContactRemovedEvent ->
                refreshContacts(reason = "PendingContactRemovedEvent(id=${e.id})")
            is PendingContactStateChangedEvent ->
                refreshContacts(
                    reason = "PendingContactStateChangedEvent(id=${e.id}, state=${e.pendingContactState})"
                )
        }
    }

    fun close() {
        eventBus.removeListener(this)
        contacts.value = emptyList()
    }

    private fun refreshContacts(
        reason: String,
        surfacedPendingContacts: List<PendingContact> = emptyList(),
    ) {
        if (readiness.value !is BriarReadinessStatus.Ready) {
            contacts.value = emptyList()
            log("Cleared contacts because runtime is not ready ($reason)")
            return
        }
        contacts.value = try {
            val confirmedContacts = contactManager.contacts.map { it.toBriarContact() }
            val pendingContacts = contactManager.pendingContacts.map { it.toBriarContact() }
            val mergedPendingContacts =
                if (confirmedContacts.isEmpty()) {
                    pendingContacts.toMutableList().apply {
                        surfacedPendingContacts
                            .map { it.toPendingBriarContact() }
                            .forEach { surfaced ->
                                if (none { existing -> existing.canonicalId == surfaced.canonicalId }) {
                                    add(surfaced)
                                }
                            }
                        }
                } else {
                    pendingContacts
                }
            (confirmedContacts + mergedPendingContacts)
                .also { refreshed ->
                    log("Refreshed contacts from $reason -> ${refreshed.summary()}")
                }
        } catch (_: Exception) {
            log("Failed to refresh contacts from $reason")
            emptyList()
        }
    }

    private fun Contact.toBriarContact(): BriarContact {
        val name = alias ?: author.name
        return BriarContact(
            canonicalId = id.int.toString(),
            transportAlias = id.int.toString(),
            displayName = name,
            presence = BriarPresenceStatus.UNKNOWN,
        )
    }

    private fun Pair<PendingContact, PendingContactState>.toBriarContact(): BriarContact {
        return first.toPendingBriarContact()
    }

    private companion object {
        const val TAG = "RealBriarContactService"
        const val DEFAULT_ALIAS = "Contact"
        const val PENDING_CONTACT_ID_PREFIX = "pending:"
    }

    private fun log(message: String) {
        System.out.println("$TAG: $message")
    }
}

private fun PendingContact.toPendingBriarContact(): BriarContact {
    val pendingId = id.bytes.toHex()
    val canonicalId = "pending:$pendingId"
    return BriarContact(
        canonicalId = canonicalId,
        transportAlias = canonicalId,
        displayName = alias,
        presence = BriarPresenceStatus.UNKNOWN,
        isPending = true,
    )
}

private fun String.abbreviated(): String =
    if (length <= 24) this else "${take(24)}..."

private fun List<BriarContact>.summary(): String =
    if (isEmpty()) {
        "count=0"
    } else {
        "count=${size} ids=${joinToString(prefix = "[", postfix = "]") { it.canonicalId }}"
    }

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }
