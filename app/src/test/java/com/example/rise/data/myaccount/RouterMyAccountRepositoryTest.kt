package com.example.rise.data.myaccount

import android.net.Uri
import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.testutil.stubBriarChatGateway
import com.example.rise.testutil.stubBriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.data.people.PeopleSync
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.AccountConnector
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.ConnectorCapabilities
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.ConnectorRegistry
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouterMyAccountRepositoryTest {

    private val authService = RecordingAuthenticationService()
    private val transportBridge = FakeTransportRuntimeBridge()

    private fun TestScope.repository(connector: FakeAccountConnector = FakeAccountConnector()) = RouterMyAccountRepository(
        authService = authService,
        peopleSync = RecordingPeopleSync(),
        transportRouter = RecordingTransportRouter(),
        connectorRegistry = FakeConnectorRegistry(connector),
        transportBridge = transportBridge,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    ) to connector

    @Test
    fun `fetchCurrentUser delegates to connector`() = runTest {
        val (repository, connector) = repository()
        connector.profile = connector.profile.copy(name = "Briar", bio = "Hybrid ready")

        val result = repository.fetchCurrentUser()

        assertEquals(connector.profile, result)
    }

    @Test
    fun `updateCurrentUser skips connector when both fields blank`() = runTest {
        val (repository, connector) = repository()

        repository.updateCurrentUser(name = " ", bio = "")

        assertTrue(connector.updates.isEmpty())
    }

    @Test
    fun `updateCurrentUser forwards non blank values`() = runTest {
        val (repository, connector) = repository()

        repository.updateCurrentUser(name = "Nova", bio = "Explores transports")

        assertEquals(1, connector.updates.size)
        val captured = connector.updates.single()
        assertEquals("Nova", captured.name)
        assertEquals("Explores transports", captured.bio)
        assertEquals("Nova", connector.profile.name)
        assertEquals("Explores transports", connector.profile.bio)
    }

    @Test
    fun `signOut stops sync resets router and signs out`() = runTest {
        val peopleSync = RecordingPeopleSync()
        val router = RecordingTransportRouter()
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = peopleSync,
            transportRouter = router,
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = transportBridge,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.signOut()

        assertTrue(authService.signOutCalled)
        assertEquals(1, peopleSync.stopCalls)
        assertEquals(1, router.resetCalls)
    }

    private class FakeConnectorRegistry(
        private val primary: TransportConnector,
    ) : ConnectorRegistry {
        override val connectors: Set<TransportConnector> = setOf(primary)

        override fun connectorFor(transportId: TransportId): TransportConnector? =
            primary.takeIf { it.transport == transportId }

        override fun primaryFor(mode: BriarTransportMode): TransportConnector = primary

        override fun mirrorsFor(mode: BriarTransportMode): List<TransportConnector> = emptyList()
    }

    private class FakeAccountConnector : TransportConnector, AccountConnector {
        override val transport: TransportId = TransportId.FIRESTORE
        override val status: StateFlow<ConnectorStatus> = MutableStateFlow(ConnectorStatus.ACTIVE)
        override val lifecycle: StateFlow<ConnectorLifecycleState> = MutableStateFlow(ConnectorLifecycleState.READY)
        override val capabilities: StateFlow<ConnectorCapabilities> = MutableStateFlow(ConnectorCapabilities.EMPTY)
        var profile: User = User(name = "Ada", bio = "Bio", profilePicturePath = null, registrationTokens = mutableListOf())
        val updates = mutableListOf<AccountConnector.AccountProfileUpdate>()

        override suspend fun fetchAccountProfile(): User = profile

        override suspend fun updateAccountProfile(update: AccountConnector.AccountProfileUpdate) {
            updates += update
            update.name?.let { profile = profile.copy(name = it) }
            update.bio?.let { profile = profile.copy(bio = it) }
            update.profilePicturePath?.let { profile = profile.copy(profilePicturePath = it) }
        }

        override suspend fun currentIdentity(): CanonicalIdentity =
            throw UnsupportedOperationException("Not needed in test")

        override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId =
            throw UnsupportedOperationException("Not needed in test")

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> = emptyFlow()

        override fun observeContacts(): Flow<List<ConnectorContact>> = emptyFlow()

        override suspend fun sendMessage(message: ConnectorOutboundMessage) =
            throw UnsupportedOperationException("Not needed in test")
    }

    private class RecordingAuthenticationService : AuthenticationService {
        var signOutCalled = false

        override fun currentUser(): AuthenticationService.User? = null

        override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle =
            AuthStateHandle { }

        override suspend fun signInWithEmail(email: String, password: String) = unsupported()

        override suspend fun createUserWithEmail(email: String, password: String) = unsupported()

        override suspend fun signInWithCustomToken(customToken: String) = unsupported()

        override suspend fun updateProfile(displayName: String?, photoUrl: Uri?) = Unit

        override fun signOut() {
            signOutCalled = true
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Not needed in test")
    }

    private class RecordingPeopleSync : PeopleSync {
        var stopCalls = 0
        override val syncPeopleErrors: Flow<Throwable> = emptyFlow()
        override val currentUserCanonicalId: Flow<String?> = emptyFlow()
        override fun ensureStarted() = Unit
        override fun stop() {
            stopCalls++
        }
    }

    private class RecordingTransportRouter : TransportRouter {
        var resetCalls = 0
        override val currentIdentity: Flow<CanonicalIdentity> = emptyFlow()
        override suspend fun ensureCurrentIdentity(): CanonicalIdentity = throw UnsupportedOperationException()
        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation = throw UnsupportedOperationException()
        override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> = emptyFlow()
        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
        override suspend fun reset() {
            resetCalls++
        }
    }

    private class FakeTransportRuntimeBridge : TransportRuntimeBridge {
        override val currentMode: StateFlow<BriarTransportMode> = MutableStateFlow(BriarTransportMode.FIRESTORE)
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
        override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val briarChatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(stubBriarChatGateway())
        override val briarContactService: StateFlow<BriarContactService> = MutableStateFlow(stubBriarContactService())
        override fun requireFirestore(caller: String) = Unit
    }
}
