package com.example.rise.briar.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactManager
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

    override fun observeContacts(): Flow<List<BriarContact>> =
        readiness.flatMapLatest { status ->
            if (status is BriarReadinessStatus.Ready) contacts.asStateFlow() else flowOf(emptyList())
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
            transportAlias = alias ?: "",
            displayName = name,
            presence = BriarPresenceStatus.UNKNOWN,
        )
    }
}
