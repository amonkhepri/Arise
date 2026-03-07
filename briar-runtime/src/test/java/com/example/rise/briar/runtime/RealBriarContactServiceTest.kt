package com.example.rise.briar.runtime

import io.mockk.Runs
import io.mockk.CapturingSlot
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.contact.HandshakeLinkConstants
import org.briarproject.bramble.api.contact.PendingContact
import org.briarproject.bramble.api.contact.PendingContactId
import org.briarproject.bramble.api.crypto.PublicKey
import org.briarproject.bramble.api.event.EventBus

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
    fun `addContactByLink uses default alias when blank`() = runTest {
        readiness.value = BriarReadinessStatus.Ready
        val aliasSlot = slot<String>()
        everyAddPendingContactReturns(pendingContact(), aliasSlot)
        val service = buildService()

        service.addContactByLink(validLink(), "  ")

        assertEquals("Contact", aliasSlot.captured)
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

    private fun pendingContact(): PendingContact {
        val idBytes = ByteArray(PendingContactId.LENGTH) { 1 }
        return PendingContact(
            PendingContactId(idBytes),
            mockk<PublicKey>(),
            "alias",
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
}
