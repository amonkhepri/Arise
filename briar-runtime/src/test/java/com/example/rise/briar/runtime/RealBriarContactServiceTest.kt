package com.example.rise.briar.runtime

import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.briarproject.bramble.api.Pair
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.contact.HandshakeLinkConstants
import org.briarproject.bramble.api.contact.PendingContact
import org.briarproject.bramble.api.contact.PendingContactId
import org.briarproject.bramble.api.contact.PendingContactState
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent
import org.briarproject.bramble.api.crypto.PublicKey
import org.briarproject.bramble.api.db.PendingContactExistsException
import org.briarproject.bramble.api.event.EventBus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealBriarContactServiceTest {

    private val readiness = MutableStateFlow<BriarReadinessStatus>(BriarReadinessStatus.NotReady)
    private val contactManager: ContactManager = mockk()
    private val eventBus: EventBus = mockk()

    @org.junit.Before
    fun setUp() {
        everyAddListener()
        everyContacts(emptyList())
        NoOpBriarContactService.reset()
    }

    @org.junit.Test
    fun `addContactByLink throws when runtime not ready`() = runTest {
        val service = buildService()
        val error = assertFailsWith<IllegalStateException> {
            service.addContactByLink(validLink(), "alias")
        }

        assertEquals("Briar runtime is not ready", error.message)
        verify(exactly = 0) { contactManager.addPendingContact(any(), any()) }
    }

    @org.junit.Test
    fun `addContactByLink rejects invalid links`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyAddPendingContactReturns(pendingContact())
        val service = buildService()

        assertFailsWith<IllegalArgumentException> {
            service.addContactByLink("not-a-handshake-link", "Alias")
        }
        verify(exactly = 0) { contactManager.addPendingContact(any(), any()) }
    }

    @org.junit.Test
    fun `addContactByLink delegates to contact manager when ready`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        val link = validLink()
        val alias = "Alice"
        everyAddPendingContactReturns(pendingContact())
        val service = buildService()

        service.addContactByLink(link, alias)

        verify { contactManager.addPendingContact(link, alias) }
    }

    @org.junit.Test
    fun `addContactByLink refreshes contacts immediately after submission`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyContacts(emptyList(), listOf(contact(id = 7, alias = "Alice")))
        everyPendingContacts(emptyList())
        everyAddPendingContactReturns(pendingContact())
        val service = buildService()

        service.addContactByLink(validLink(), "Alice")

        assertEquals(
            listOf(
                BriarContact(
                    canonicalId = "7",
                    transportAlias = "7",
                    displayName = "Alice",
                    presence = BriarPresenceStatus.UNKNOWN,
                )
            ),
            service.observeContacts().first(),
        )
    }

    @org.junit.Test
    fun `addContactByLink uses default alias when blank`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        val aliasSlot = slot<String>()
        everyPendingContacts(emptyList())
        everyAddPendingContactReturns(pendingContact(), aliasSlot)
        val service = buildService()

        service.addContactByLink(validLink(), "  ")

        assertEquals("Contact", aliasSlot.captured)
    }

    @org.junit.Test
    fun `addContactByLink surfaces pending contact when no confirmed contact exists yet`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyContacts(emptyList(), emptyList())
        everyPendingContacts(
            emptyList(),
            listOf(Pair(pendingContact(alias = "Pending Alice"), PendingContactState.WAITING_FOR_CONNECTION)),
        )
        everyAddPendingContactReturns(pendingContact(alias = "Pending Alice"))
        val service = buildService()

        service.addContactByLink(validLink(), "Pending Alice")

        val contact = service.observeContacts().first().single()
        assertEquals("Pending Alice", contact.displayName)
        assertEquals(contact.canonicalId, contact.transportAlias)
        assertTrue(contact.canonicalId.startsWith("pending:"))
        assertTrue(contact.isPending)
    }

    @org.junit.Test
    fun `addContactByLink surfaces returned pending contact when pending snapshot is stale`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyContacts(emptyList(), emptyList())
        val failedPending = pendingContact(alias = "Failed Alice", idByte = 1)
        val offlinePending = pendingContact(alias = "Offline Bob", idByte = 2)
        val freshPending = pendingContact(alias = "Fresh Carol", idByte = 3)
        everyPendingContacts(
            listOf(
                Pair(failedPending, PendingContactState.FAILED),
                Pair(offlinePending, PendingContactState.OFFLINE),
            ),
            listOf(
                Pair(failedPending, PendingContactState.FAILED),
                Pair(offlinePending, PendingContactState.OFFLINE),
            ),
        )
        everyAddPendingContactReturns(freshPending)
        val service = buildService()

        service.addContactByLink(validLink(), "Fresh Carol")

        val contacts = service.observeContacts().first()
        assertEquals(3, contacts.size)
        val surfaced = contacts.single { it.displayName == "Fresh Carol" }
        assertEquals("pending:${freshPending.id.bytes.toHex()}", surfaced.canonicalId)
        assertTrue(surfaced.isPending)
    }

    @org.junit.Test
    fun `addContactByLink surfaces pending contact from PendingContactExistsException when pending snapshot is stale`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyContacts(emptyList(), emptyList())
        val failedPending = pendingContact(alias = "Failed Alice", idByte = 1)
        val offlinePending = pendingContact(alias = "Offline Bob", idByte = 2)
        val freshPending = pendingContact(alias = "Fresh Carol", idByte = 3)
        everyPendingContacts(
            listOf(
                Pair(failedPending, PendingContactState.FAILED),
                Pair(offlinePending, PendingContactState.OFFLINE),
            ),
            listOf(
                Pair(failedPending, PendingContactState.FAILED),
                Pair(offlinePending, PendingContactState.OFFLINE),
            ),
        )
        io.mockk.every {
            contactManager.addPendingContact(any(), any())
        } throws PendingContactExistsException(freshPending)
        val service = buildService()

        val error = assertFailsWith<PendingContactExistsException> {
            service.addContactByLink(validLink(), "Fresh Carol")
        }

        assertEquals(freshPending, error.pendingContact)
        val contacts = service.observeContacts().first()
        assertEquals(3, contacts.size)
        val surfaced = contacts.single { it.displayName == "Fresh Carol" }
        assertEquals("pending:${freshPending.id.bytes.toHex()}", surfaced.canonicalId)
        assertTrue(surfaced.isPending)
    }

    @org.junit.Test
    fun `getHandshakeLink throws when runtime not ready`() {
        val service = buildService()

        val error = assertFailsWith<IllegalStateException> {
            service.getHandshakeLink()
        }

        assertEquals("Briar runtime is not ready", error.message)
        verify(exactly = 0) { contactManager.getHandshakeLink() }
    }

    @org.junit.Test
    fun `getHandshakeLink delegates to contact manager when ready`() {
        readiness.value = BriarReadinessStatus.Ready
        val expected = validLink()
        everyHandshakeLinkReturns(expected)
        val service = buildService()

        val actual = service.getHandshakeLink()

        assertEquals(expected, actual)
        verify(exactly = 1) { contactManager.getHandshakeLink() }
    }

    @org.junit.Test
    fun `no op getHandshakeLink throws when runtime not ready`() {
        val error = assertFailsWith<IllegalStateException> {
            NoOpBriarContactService.getHandshakeLink()
        }

        assertEquals("Briar runtime is not ready", error.message)
    }

    @org.junit.Test
    fun `pending contact added event refreshes contacts`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyContacts(emptyList(), listOf(contact(id = 9, alias = "Pending Alice")))
        everyPendingContacts(emptyList())
        val service = buildService()

        service.eventOccurred(PendingContactAddedEvent(pendingContact()))

        assertEquals(
            listOf(
                BriarContact(
                    canonicalId = "9",
                    transportAlias = "9",
                    displayName = "Pending Alice",
                    presence = BriarPresenceStatus.UNKNOWN,
                )
            ),
            service.observeContacts().first(),
        )
    }

    @org.junit.Test
    fun `pending contact state changed event refreshes pending contacts`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        everyContacts(emptyList(), emptyList())
        val pendingAlice = pendingContact(alias = "Pending Alice")
        everyPendingContacts(
            emptyList(),
            listOf(Pair(pendingAlice, PendingContactState.WAITING_FOR_CONNECTION)),
        )
        val service = buildService()

        service.eventOccurred(
            PendingContactStateChangedEvent(
                pendingAlice.id,
                PendingContactState.WAITING_FOR_CONNECTION,
            )
        )

        val contact = service.observeContacts().first().single()
        assertEquals("Pending Alice", contact.displayName)
        assertEquals(contact.canonicalId, contact.transportAlias)
        assertTrue(contact.canonicalId.startsWith("pending:"))
        assertTrue(contact.isPending)
    }

    @org.junit.Test
    fun `no op getHandshakeLink returns placeholder link when marked ready`() {
        NoOpBriarContactService.markReady()

        val actual = NoOpBriarContactService.getHandshakeLink()

        assertEquals(validLink(), actual)
    }

    @org.junit.Test
    fun `no op addContactByLink still throws when marked ready`() = runTest {
        NoOpBriarContactService.markReady()

        val error = assertFailsWith<IllegalStateException> {
            NoOpBriarContactService.addContactByLink(validLink(), "Alias")
        }

        assertEquals("Briar runtime is not ready", error.message)
    }

    private fun buildService(): RealBriarContactService {
        return RealBriarContactService(readiness.asStateFlow(), contactManager, eventBus)
    }

    private fun validLink(): String =
        "briar://${"a".repeat(HandshakeLinkConstants.BASE32_LINK_BYTES)}"

    private fun pendingContact(alias: String = "alias", idByte: Int = 1): PendingContact {
        val idBytes = ByteArray(PendingContactId.LENGTH) { idByte.toByte() }
        return PendingContact(
            PendingContactId(idBytes),
            mockk<PublicKey>(),
            alias,
            0L
        )
    }

    private fun everyAddListener() {
        io.mockk.every { eventBus.addListener(any()) } just Runs
        io.mockk.every { eventBus.removeListener(any()) } just Runs
    }

    private fun everyContacts(contacts: List<org.briarproject.bramble.api.contact.Contact>) {
        io.mockk.every { contactManager.contacts } returns contacts
    }

    private fun everyContacts(
        first: List<Contact>,
        second: List<Contact>,
    ) {
        io.mockk.every { contactManager.contacts } returns first andThen second
    }

    private fun everyPendingContacts(contacts: List<Pair<PendingContact, PendingContactState>>) {
        io.mockk.every { contactManager.pendingContacts } returns contacts
    }

    private fun everyPendingContacts(
        first: List<Pair<PendingContact, PendingContactState>>,
        second: List<Pair<PendingContact, PendingContactState>>,
    ) {
        io.mockk.every { contactManager.pendingContacts } returns first andThen second
    }

    private fun everyAddPendingContactReturns(
        contact: PendingContact,
        aliasSlot: CapturingSlot<String>? = null,
    ) {
        if (aliasSlot == null) {
            io.mockk.every { contactManager.addPendingContact(any(), any()) } returns contact
        } else {
            io.mockk.every { contactManager.addPendingContact(any(), capture(aliasSlot)) } returns contact
        }
    }

    private fun everyHandshakeLinkReturns(link: String) {
        io.mockk.every { contactManager.getHandshakeLink() } returns link
    }

    private fun contact(id: Int, alias: String): Contact {
        return mockk {
            io.mockk.every { this@mockk.id } returns ContactId(id)
            io.mockk.every { this@mockk.alias } returns alias
        }
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }
