package com.example.rise.transport.briar.invite

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarPresenceStatus
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class BriarInvitationAcceptanceUseCaseTest {

    @Test
    fun `accept adds contact when link parses successfully`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val useCase = BriarInvitationAcceptanceUseCase(contactRepository)
        val rawLink = "arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42"

        val result = useCase.accept(rawLink)

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals(validLink(), result.invitation.briarLink)
        assertEquals("Alice", result.invitation.alias)
        assertEquals("invite:inv-42", result.invitation.duplicateKey)
        assertEquals(listOf(validLink() to "Alice"), service.calls)
    }

    @Test
    fun `accept bootstraps canonical conversation when transport router is provided`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val transportRouter = RecordingTransportRouter(
            conversation = CanonicalConversation(
                id = "conversation-42",
                participants = setOf("self", "invite:inv-42"),
                title = "Alice",
            ),
        )
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals("conversation-42", result.conversationId)
        assertEquals("invite:inv-42", transportRouter.requestedIdentity?.id)
        assertEquals("Alice", transportRouter.requestedIdentity?.displayName)
    }

    @Test
    fun `accept restores current identity before adding contact when transport router is provided`() = runTest {
        val events = mutableListOf<String>()
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                events += "contact:$link"
            }

            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity {
                events += "identity"
                return CanonicalIdentity(id = "self", displayName = "Self")
            }

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                events += "conversation:${otherIdentity.id}"
                return CanonicalConversation(
                    id = "conversation-42",
                    participants = setOf("self", otherIdentity.id),
                    title = otherIdentity.displayName,
                )
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        assertEquals(
            listOf(
                "identity",
                "contact:${validLink()}",
                "conversation:invite:inv-42",
            ),
            events,
        )
    }

    @Test
    fun `accept fails before adding contact when current identity restoration fails`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val expectedError = IllegalStateException("Failed to load Briar identity")
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity = throw expectedError

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation =
                error("Should not reach conversation bootstrap")

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Failed)
        result as BriarInvitationAcceptanceResult.Failed
        assertEquals(expectedError, result.error)
        assertEquals(emptyList<Pair<String, String?>>(), service.calls)
    }

    @Test
    fun `accept defers bootstrap when transport router needs numeric briar contact id`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals(null, result.conversationId)
        assertEquals(listOf(validLink() to "Alice"), service.calls)
    }

    @Test
    fun `accept retries bootstrap with resolved numeric briar contact id after contact appears`() = runTest {
        val contacts = MutableStateFlow<List<BriarContact>>(emptyList())
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true
            val calls = mutableListOf<Pair<String, String?>>()

            override suspend fun addContactByLink(link: String, alias: String?) {
                calls += link to alias
                contacts.value = listOf(
                    BriarContact(
                        canonicalId = "42",
                        transportAlias = "42",
                        displayName = "Alice",
                        presence = BriarPresenceStatus.UNKNOWN,
                    )
                )
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                if (otherIdentity.id != "42") {
                    throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
                }
                return CanonicalConversation(
                    id = "conversation-42",
                    participants = setOf("self", otherIdentity.id),
                    title = otherIdentity.displayName,
                )
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 0L,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals("conversation-42", result.conversationId)
        assertEquals(listOf("invite:inv-42", "42"), requestedIds)
        assertEquals(listOf(validLink() to "Alice"), service.calls)
    }

    @Test
    fun `accept continues bootstrap when pending contact already exists after reauthentication`() = runTest {
        val contacts = MutableStateFlow<List<BriarContact>>(emptyList())
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                contacts.value = listOf(
                    BriarContact(
                        canonicalId = "42",
                        transportAlias = "42",
                        displayName = "Alice",
                        presence = BriarPresenceStatus.UNKNOWN,
                    )
                )
                throw PendingContactExistsException(pendingContact(alias = "Alice", idByte = 4))
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                if (otherIdentity.id != "42") {
                    throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
                }
                return CanonicalConversation(
                    id = "conversation-42",
                    participants = setOf("self", otherIdentity.id),
                    title = otherIdentity.displayName,
                )
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 0L,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals("conversation-42", result.conversationId)
        assertEquals("42", result.contactIdentity?.id)
        assertEquals("Alice", result.contactIdentity?.displayName)
        assertEquals(listOf("42"), requestedIds)
    }

    @Test
    fun `accept surfaces newly observed pending contact identity when pending contact already exists`() = runTest {
        val contacts = MutableStateFlow(
            listOf(
                BriarContact(
                    canonicalId = "pending:stale-1",
                    transportAlias = "pending:stale-1",
                    displayName = "Old pending one",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
                BriarContact(
                    canonicalId = "pending:stale-2",
                    transportAlias = "pending:stale-2",
                    displayName = "Old pending two",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
            )
        )
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                contacts.value = contacts.value + BriarContact(
                    canonicalId = "pending:fresh-3",
                    transportAlias = "pending:fresh-3",
                    displayName = "Alice",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                )
                throw PendingContactExistsException(pendingContact(alias = "Alice", idByte = 5))
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 0L,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals(null, result.conversationId)
        assertEquals("pending:fresh-3", result.contactIdentity?.id)
        assertEquals("Alice", result.contactIdentity?.displayName)
        assertTrue(requestedIds.isEmpty())
    }

    @Test
    fun `accept reuses pending contact identity from PendingContactExistsException when pending id already existed`() = runTest {
        val existingPending = pendingContact(alias = "Offline Bob", idByte = 2)
        val contacts = MutableStateFlow(
            listOf(
                BriarContact(
                    canonicalId = "pending:stale-1",
                    transportAlias = "pending:stale-1",
                    displayName = "Old pending one",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
                BriarContact(
                    canonicalId = "pending:${existingPending.id.bytes.toHex()}",
                    transportAlias = "pending:${existingPending.id.bytes.toHex()}",
                    displayName = existingPending.alias,
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
            )
        )
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                throw PendingContactExistsException(existingPending)
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 0L,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals(null, result.conversationId)
        assertEquals("pending:${existingPending.id.bytes.toHex()}", result.contactIdentity?.id)
        assertEquals("Offline Bob", result.contactIdentity?.displayName)
        assertTrue(requestedIds.isEmpty())
    }

    @Test
    fun `accept stops deferred bootstrap when existing pending identity never becomes a confirmed contact`() = runTest {
        val existingPending = pendingContact(alias = "Offline Bob", idByte = 2)
        val pendingId = "pending:${existingPending.id.bytes.toHex()}"
        val contacts = MutableStateFlow(
            listOf(
                BriarContact(
                    canonicalId = "pending:stale-1",
                    transportAlias = "pending:stale-1",
                    displayName = "Old pending one",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
                BriarContact(
                    canonicalId = pendingId,
                    transportAlias = pendingId,
                    displayName = existingPending.alias,
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
            )
        )
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                throw PendingContactExistsException(existingPending)
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 0L,
            maxDeferredConversationRetries = 3,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals(null, result.conversationId)
        assertEquals(pendingId, result.contactIdentity?.id)
        assertEquals("Offline Bob", result.contactIdentity?.displayName)
        assertTrue(requestedIds.isEmpty())
    }

    @Test
    fun `accept keeps retrying when a fresh pending identity resolves to a confirmed contact on a later retry`() = runTest {
        val contacts = MutableStateFlow(
            listOf(
                BriarContact(
                    canonicalId = "pending:stale-1",
                    transportAlias = "pending:stale-1",
                    displayName = "Old pending one",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                )
            )
        )
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                contacts.value = contacts.value + BriarContact(
                    canonicalId = "pending:fresh-3",
                    transportAlias = "pending:fresh-3",
                    displayName = "Alice",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                )
                backgroundScope.launch {
                    delay(125L)
                    contacts.value = listOf(
                        BriarContact(
                            canonicalId = "42",
                            transportAlias = "42",
                            displayName = "Alice",
                            presence = BriarPresenceStatus.UNKNOWN,
                        )
                    )
                }
                throw PendingContactExistsException(pendingContact(alias = "Alice", idByte = 5))
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                if (otherIdentity.id != "42") {
                    throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
                }
                return CanonicalConversation(
                    id = "conversation-42",
                    participants = setOf("self", otherIdentity.id),
                    title = otherIdentity.displayName,
                )
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 50L,
            maxDeferredConversationRetries = 5,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals("conversation-42", result.conversationId)
        assertEquals("42", result.contactIdentity?.id)
        assertEquals("Alice", result.contactIdentity?.displayName)
        assertEquals(listOf("42"), requestedIds)
    }

    @Test
    fun `accept stops retrying ensureConversation when a fresh pending identity appears after the first numeric-id failure`() = runTest {
        val contacts = MutableStateFlow(
            listOf(
                BriarContact(
                    canonicalId = "pending:stale-1",
                    transportAlias = "pending:stale-1",
                    displayName = "Old pending one",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
                BriarContact(
                    canonicalId = "pending:stale-2",
                    transportAlias = "pending:stale-2",
                    displayName = "Old pending two",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                ),
            )
        )
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                throw PendingContactExistsException(pendingContact = null)
            }

            override fun observeContacts(): Flow<List<BriarContact>> = contacts
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val requestedIds = mutableListOf<String>()
        val transportRouter = object : TransportRouter {
            override val currentIdentity: Flow<CanonicalIdentity> =
                flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

            override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
                CanonicalIdentity(id = "self", displayName = "Self")

            override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
                requestedIds += otherIdentity.id
                contacts.value = contacts.value + BriarContact(
                    canonicalId = "pending:fresh-3",
                    transportAlias = "pending:fresh-3",
                    displayName = "Alice",
                    presence = BriarPresenceStatus.UNKNOWN,
                    isPending = true,
                )
                throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
            }

            override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

            override suspend fun reset() = Unit
        }
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            transportRouter = transportRouter,
            deferredConversationRetryDelayMillis = 0L,
            maxDeferredConversationRetries = 3,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        result as BriarInvitationAcceptanceResult.Accepted
        assertEquals(null, result.conversationId)
        assertEquals("pending:fresh-3", result.contactIdentity?.id)
        assertEquals("Alice", result.contactIdentity?.displayName)
        assertEquals(listOf("invite:inv-42"), requestedIds)
    }

    @Test
    fun `accept returns invalid when link parsing fails`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val useCase = BriarInvitationAcceptanceUseCase(contactRepository)

        val result = useCase.accept("https://example.com/not-supported")

        assertTrue(result is BriarInvitationAcceptanceResult.InvalidLink)
        result as BriarInvitationAcceptanceResult.InvalidLink
        assertEquals(BriarInvitationLinkParseResult.InvalidReason.UNSUPPORTED_LINK, result.reason)
        assertEquals(emptyList<Pair<String, String?>>(), service.calls)
    }

    @Test
    fun `accept returns duplicate rejection when contact repository reports reused invitation`() = runTest {
        val expectedError = DuplicateInvitationException()
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                throw expectedError
            }

            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val useCase = BriarInvitationAcceptanceUseCase(contactRepository)

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Failed)
        result as BriarInvitationAcceptanceResult.Failed
        assertTrue(result.error is BriarInvitationClassifiedFailure)
        val classifiedError = result.error as BriarInvitationClassifiedFailure
        assertEquals(BriarInvitationRejectionReason.DUPLICATE, classifiedError.reason)
        assertEquals(expectedError, classifiedError.cause)
        assertEquals(validLink(), result.invitation.briarLink)
    }

    @Test
    fun `accept returns expired rejection when contact repository reports expired invitation`() = runTest {
        val expectedError = ExpiredInvitationException()
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                throw expectedError
            }

            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val useCase = BriarInvitationAcceptanceUseCase(contactRepository)

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Failed)
        result as BriarInvitationAcceptanceResult.Failed
        assertTrue(result.error is BriarInvitationClassifiedFailure)
        val classifiedError = result.error as BriarInvitationClassifiedFailure
        assertEquals(BriarInvitationRejectionReason.EXPIRED, classifiedError.reason)
        assertEquals(expectedError, classifiedError.cause)
        assertEquals(validLink(), result.invitation.briarLink)
    }

    @Test
    fun `accept returns invalid rejection when contact repository reports invalid invitation`() = runTest {
        val expectedError = InvalidInvitationException()
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true

            override suspend fun addContactByLink(link: String, alias: String?) {
                throw expectedError
            }

            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val useCase = BriarInvitationAcceptanceUseCase(contactRepository)

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Failed)
        result as BriarInvitationAcceptanceResult.Failed
        assertTrue(result.error is BriarInvitationClassifiedFailure)
        val classifiedError = result.error as BriarInvitationClassifiedFailure
        assertEquals(BriarInvitationRejectionReason.INVALID, classifiedError.reason)
        assertEquals(expectedError, classifiedError.cause)
        assertEquals(validLink(), result.invitation.briarLink)
    }

    @Test
    fun `accept returns failed when contact repository throws`() = runTest {
        val expectedError = IllegalStateException("runtime unavailable")
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true
            override suspend fun addContactByLink(link: String, alias: String?) {
                throw expectedError
            }

            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val useCase = BriarInvitationAcceptanceUseCase(contactRepository)

        val result = useCase.accept(validLink())

        assertTrue(result is BriarInvitationAcceptanceResult.Failed)
        result as BriarInvitationAcceptanceResult.Failed
        assertEquals(expectedError, result.error)
        assertEquals(validLink(), result.invitation.briarLink)
    }

    private fun validLink(): String = "briar://${"b".repeat(53)}"

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private fun pendingContact(alias: String = "alias", idByte: Int = 1): TestPendingContact {
        val idBytes = ByteArray(TEST_PENDING_CONTACT_ID_LENGTH) { idByte.toByte() }
        return TestPendingContact(
            id = TestPendingContactId(idBytes),
            alias = alias,
        )
    }

    private fun fakeBridge(service: BriarContactService): TransportRuntimeBridge {
        val mode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
        val status = MutableStateFlow(BriarRuntimeStatus(BriarRuntimePhase.RUNNING))
        val chat = MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway)
        val contacts = MutableStateFlow(service)
        return object : TransportRuntimeBridge {
            override val currentMode: StateFlow<BriarTransportMode> = mode
            override val runtimeStatus: StateFlow<BriarRuntimeStatus> = status
            override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
            override val briarChatGateway: StateFlow<BriarChatGateway> = chat
            override val briarContactService: StateFlow<BriarContactService> = contacts
            override fun requireFirestore(caller: String) = Unit
        }
    }

    private class RecordingContactService(
        override val isAvailable: Boolean,
    ) : BriarContactService {
        val calls = mutableListOf<Pair<String, String?>>()

        override suspend fun addContactByLink(link: String, alias: String?) {
            calls += link to alias
        }

        override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
    }

    private class RecordingTransportRouter(
        private val conversation: CanonicalConversation,
    ) : TransportRouter {
        var requestedIdentity: CanonicalIdentity? = null

        override val currentIdentity: Flow<CanonicalIdentity> =
            flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

        override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
            CanonicalIdentity(id = "self", displayName = "Self")

        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
            requestedIdentity = otherIdentity
            return conversation
        }

        override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> = flowOf(emptyList())

        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

        override suspend fun reset() = Unit
    }

    private class DuplicateInvitationException :
        IllegalStateException("Invitation already used for this contact")

    private class ExpiredInvitationException :
        IllegalStateException("Invitation link expired before it could be accepted")

    private class InvalidInvitationException :
        IllegalArgumentException("Invalid invitation link payload")

    private class PendingContactExistsException(
        val pendingContact: TestPendingContact? = TestPendingContact(
            id = TestPendingContactId(ByteArray(TEST_PENDING_CONTACT_ID_LENGTH)),
            alias = "alias",
        ),
    ) : IllegalStateException()

    private data class TestPendingContact(
        val id: TestPendingContactId,
        val alias: String,
    )

    private data class TestPendingContactId(
        val bytes: ByteArray,
    )

    private companion object {
        const val TEST_PENDING_CONTACT_ID_LENGTH = 32
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }
