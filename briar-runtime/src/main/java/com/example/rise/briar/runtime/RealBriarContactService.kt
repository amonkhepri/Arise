package com.example.rise.briar.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.contact.HandshakeLinkConstants
import org.briarproject.bramble.api.contact.event.ContactAddedEvent
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent
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
        refreshContacts()
        eventBus.addListener(this)
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
        contactManager.addPendingContact(link, aliasToUse)
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
            is ContactAddedEvent,
            is ContactRemovedEvent,
            is PendingContactRemovedEvent -> refreshContacts()
        }
    }

    fun close() {
        eventBus.removeListener(this)
        contacts.value = emptyList()
    }

    private fun refreshContacts() {
        if (readiness.value !is BriarReadinessStatus.Ready) {
            contacts.value = emptyList()
            return
        }
        contacts.value = try {
            contactManager.contacts.map { it.toBriarContact() }
        } catch (_: Exception) {
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

    private companion object {
        const val DEFAULT_ALIAS = "Contact"
    }
}
