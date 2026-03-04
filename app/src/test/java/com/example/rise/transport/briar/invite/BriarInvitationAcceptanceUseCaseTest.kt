package com.example.rise.transport.briar.invite

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
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
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.TransportRouter
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
    fun `accept persists invitation identity mapping for stable lookup`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val identityStore = FakeIdentityRegistryStore()
        val identityRegistry = IdentityRegistryImpl(identityStore)
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            identityRegistry = identityRegistry,
        )
        val rawLink = "arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42"

        val result = useCase.accept(rawLink)

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        val record = identityRegistry.identitiesSnapshot().single()
        assertEquals("invite:inv-42", record.canonicalIdentity.id)
        assertEquals("Alice", record.canonicalIdentity.displayName)
        assertEquals(validLink(), record.aliases[TransportId.BRIAR])
        assertTrue(identityStore.state.records.containsKey("invite:inv-42"))
    }

    @Test
    fun `accept does not set invited contact as current identity when registry has no current user`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val contactRepository = BriarContactRepository(fakeBridge(service))
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
        val useCase = BriarInvitationAcceptanceUseCase(
            contactRepository = contactRepository,
            identityRegistry = identityRegistry,
        )

        val result = useCase.accept("arise://briar/invite?link=${encode(validLink())}&alias=${encode("Alice")}&inviteId=INV-42")

        assertTrue(result is BriarInvitationAcceptanceResult.Accepted)
        assertEquals(null, identityRegistry.currentIdentitySnapshot())
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

    private class FakeIdentityRegistryStore(
        initialState: IdentityRegistryStore.StoredState = IdentityRegistryStore.StoredState(emptyMap(), null),
    ) : IdentityRegistryStore {
        var state: IdentityRegistryStore.StoredState = initialState
            private set

        override fun load(): IdentityRegistryStore.StoredState = state

        override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
            val recordsCopy = records.mapValues { (_, record) ->
                IdentityRecord(
                    canonicalIdentity = record.canonicalIdentity,
                    aliases = record.aliases.toMap(),
                    profile = record.profile,
                )
            }
            state = IdentityRegistryStore.StoredState(recordsCopy, currentIdentityId)
        }
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
}
