package com.example.rise.data.myaccount

import android.net.Uri
import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.data.people.PeopleSync
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.models.User
import com.example.rise.testutil.stubBriarChatGateway
import com.example.rise.testutil.stubBriarContactService
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
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val runtimeManager = RecordingBriarRuntimeManager()

    private fun TestScope.repository(
        connector: FakeAccountConnector = FakeAccountConnector(),
        identityRegistry: IdentityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
    ) = RouterMyAccountRepository(
        authService = authService,
        peopleSync = RecordingPeopleSync(),
        transportRouter = RecordingTransportRouter(),
        connectorRegistry = FakeConnectorRegistry(connector),
        transportBridge = transportBridge,
        identityRegistry = identityRegistry,
        briarRuntimeManager = runtimeManager,
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
    fun `updateCurrentUser forwards profile picture path`() = runTest {
        val (repository, connector) = repository()

        repository.updateCurrentUser(
            name = "",
            bio = "",
            profilePicturePath = "/tmp/profile.png",
        )

        assertEquals(1, connector.updates.size)
        val captured = connector.updates.single()
        assertEquals(null, captured.name)
        assertEquals(null, captured.bio)
        assertEquals("/tmp/profile.png", captured.profilePicturePath)
        assertEquals("/tmp/profile.png", connector.profile.profilePicturePath)
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
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.signOut()

        assertTrue(authService.signOutCalled)
        assertEquals(1, peopleSync.stopCalls)
        assertEquals(1, router.resetCalls)
        assertEquals(1, runtimeManager.stopCalls)
        assertTrue(authService.signOutCalled)
    }

    @Test
    fun `signOut clears identity and stops runtime in briar only mode`() = runTest {
        val peopleSync = RecordingPeopleSync()
        val router = RecordingTransportRouter()
        val transportBridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        val canonicalIdentity = CanonicalIdentity(id = "briar-self", displayName = "Briar User")
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "bio"),
            setAsCurrent = true,
        )
        val runtimeManager = RecordingBriarRuntimeManager()

        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = peopleSync,
            transportRouter = router,
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = transportBridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.signOut()

        assertEquals(1, router.resetCalls)
        assertEquals(1, runtimeManager.stopCalls)
        assertTrue(identityRegistry.identitiesSnapshot().isEmpty())
        assertEquals(null, identityRegistry.currentIdentitySnapshot())
        assertTrue(authService.signOutCalled)
    }

    @Test
    fun `hybrid mode falls back to account-capable connector`() = runTest {
        val accountConnector = FakeAccountConnector().apply {
            profile = profile.copy(name = "Profile From Account Connector")
        }
        val briarLikeConnector = NonAccountConnector(TransportId.BRIAR)
        val registry = DualConnectorRegistry(primary = briarLikeConnector, fallback = accountConnector)
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.HYBRID)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.fetchCurrentUser()

        assertEquals(accountConnector.profile, result)
    }

    @Test
    fun `hybrid mode prefers non firestore account connector when primary lacks account capability`() = runTest {
        val briarAccountConnector = FakeAccountConnector(transport = TransportId.BRIAR).apply {
            profile = profile.copy(name = "Briar Account Connector")
        }
        val firestoreAccountConnector = FakeAccountConnector(transport = TransportId.FIRESTORE).apply {
            profile = profile.copy(name = "Firestore Account Connector")
        }
        val primaryConnector = NonAccountConnector(TransportId.BRIAR)
        val registry = OrderedFallbackConnectorRegistry(
            primary = primaryConnector,
            firstFallback = briarAccountConnector,
            secondFallback = firestoreAccountConnector,
        )
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.HYBRID)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.fetchCurrentUser()

        assertEquals(briarAccountConnector.profile, result)
    }

    @Test
    fun `hybrid mode prefers non firestore account connector even when primary is firestore account connector`() = runTest {
        val firestoreAccountConnector = FakeAccountConnector(transport = TransportId.FIRESTORE).apply {
            profile = profile.copy(name = "Firestore Primary Connector")
        }
        val briarAccountConnector = FakeAccountConnector(transport = TransportId.BRIAR).apply {
            profile = profile.copy(name = "Briar Account Connector")
        }
        val registry = OrderedFallbackConnectorRegistry(
            primary = firestoreAccountConnector,
            firstFallback = briarAccountConnector,
            secondFallback = NonAccountConnector(TransportId.BRIAR),
        )
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.HYBRID)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val fetched = repository.fetchCurrentUser()
        repository.updateCurrentUser(name = "Nova", bio = "")

        assertEquals("Briar Account Connector", fetched.name)
        assertEquals(1, briarAccountConnector.updates.size)
        assertTrue(firestoreAccountConnector.updates.isEmpty())
    }

    @Test
    fun `hybrid mode falls back to ready account connector when preferred connector is stopped`() = runTest {
        val unavailableBriarConnector = FakeAccountConnector(
            transport = TransportId.BRIAR,
            lifecycleState = ConnectorLifecycleState.STOPPED,
            fetchFailure = IllegalStateException("Briar account connector not ready"),
            updateFailure = IllegalStateException("Briar account connector not ready"),
        )
        val firestoreAccountConnector = FakeAccountConnector(transport = TransportId.FIRESTORE).apply {
            profile = profile.copy(name = "Firestore Account Connector")
        }
        val registry = OrderedFallbackConnectorRegistry(
            primary = unavailableBriarConnector,
            firstFallback = firestoreAccountConnector,
            secondFallback = NonAccountConnector(TransportId.BRIAR),
        )
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.HYBRID)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val fetched = repository.fetchCurrentUser()
        repository.updateCurrentUser(name = "Nova", bio = "")

        assertEquals("Firestore Account Connector", fetched.name)
        assertTrue(unavailableBriarConnector.updates.isEmpty())
        assertEquals(1, firestoreAccountConnector.updates.size)
    }

    @Test
    fun `firestore mode falls back when firestore account connector is not active`() = runTest {
        val briarAccountConnector = FakeAccountConnector(transport = TransportId.BRIAR).apply {
            profile = profile.copy(name = "Briar Account Connector")
        }
        val unavailableFirestoreConnector = FakeAccountConnector(
            transport = TransportId.FIRESTORE,
            lifecycleState = ConnectorLifecycleState.READY,
            statusState = ConnectorStatus.ERROR,
            fetchFailure = IllegalStateException("Firestore account connector unavailable"),
            updateFailure = IllegalStateException("Firestore account connector unavailable"),
        )
        val registry = OrderedFallbackConnectorRegistry(
            primary = NonAccountConnector(TransportId.BRIAR),
            firstFallback = briarAccountConnector,
            secondFallback = unavailableFirestoreConnector,
        )
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.FIRESTORE)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val fetched = repository.fetchCurrentUser()
        repository.updateCurrentUser(name = "Nova", bio = "")

        assertEquals("Briar Account Connector", fetched.name)
        assertEquals(1, briarAccountConnector.updates.size)
        assertTrue(unavailableFirestoreConnector.updates.isEmpty())
    }

    @Test
    fun `firestore mode fetchCurrentUser falls back when firestore connector fails at runtime`() = runTest {
        val briarAccountConnector = FakeAccountConnector(transport = TransportId.BRIAR).apply {
            profile = profile.copy(name = "Briar Account Connector")
        }
        val failingFirestoreConnector = FakeAccountConnector(
            transport = TransportId.FIRESTORE,
            lifecycleState = ConnectorLifecycleState.READY,
            statusState = ConnectorStatus.ACTIVE,
            fetchFailure = IllegalStateException("Firestore account connector runtime failure"),
        )
        val registry = OrderedFallbackConnectorRegistry(
            primary = NonAccountConnector(TransportId.BRIAR),
            firstFallback = briarAccountConnector,
            secondFallback = failingFirestoreConnector,
        )
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.FIRESTORE)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val fetched = repository.fetchCurrentUser()

        assertEquals("Briar Account Connector", fetched.name)
    }

    @Test
    fun `firestore mode updateCurrentUser falls back when firestore connector fails at runtime`() = runTest {
        val briarAccountConnector = FakeAccountConnector(transport = TransportId.BRIAR)
        val failingFirestoreConnector = FakeAccountConnector(
            transport = TransportId.FIRESTORE,
            lifecycleState = ConnectorLifecycleState.READY,
            statusState = ConnectorStatus.ACTIVE,
            updateFailure = IllegalStateException("Firestore account connector runtime failure"),
        )
        val registry = OrderedFallbackConnectorRegistry(
            primary = NonAccountConnector(TransportId.BRIAR),
            firstFallback = briarAccountConnector,
            secondFallback = failingFirestoreConnector,
        )
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.FIRESTORE)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.updateCurrentUser(name = "Nova", bio = "Fallback bio")

        assertEquals(1, briarAccountConnector.updates.size)
        assertTrue(failingFirestoreConnector.updates.isEmpty())
        val update = briarAccountConnector.updates.single()
        assertEquals("Nova", update.name)
        assertEquals("Fallback bio", update.bio)
    }

    @Test
    fun `firestore mode runtime fallback prefers registry primary connector`() = runTest {
        val mirrorBriarConnector = FakeAccountConnector(transport = TransportId.BRIAR).apply {
            profile = profile.copy(name = "Mirror Briar Connector")
        }
        val primaryBriarConnector = FakeAccountConnector(transport = TransportId.BRIAR).apply {
            profile = profile.copy(name = "Primary Briar Connector")
        }
        val failingFirestoreConnector = FakeAccountConnector(
            transport = TransportId.FIRESTORE,
            lifecycleState = ConnectorLifecycleState.READY,
            statusState = ConnectorStatus.ACTIVE,
            fetchFailure = IllegalStateException("Firestore account connector runtime failure"),
        )
        val registry = object : ConnectorRegistry {
            private val ordered = linkedSetOf(
                mirrorBriarConnector,
                primaryBriarConnector,
                failingFirestoreConnector,
            )

            override val connectors: Set<TransportConnector> = ordered

            override fun connectorFor(transportId: TransportId): TransportConnector? {
                return ordered.firstOrNull { it.transport == transportId }
            }

            override fun primaryFor(mode: BriarTransportMode): TransportConnector = primaryBriarConnector

            override fun mirrorsFor(mode: BriarTransportMode): List<TransportConnector> = emptyList()
        }
        val transportBridge = FakeTransportRuntimeBridge().apply {
            setMode(BriarTransportMode.FIRESTORE)
        }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = RecordingTransportRouter(),
            connectorRegistry = registry,
            transportBridge = transportBridge,
            identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore()),
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.fetchCurrentUser()

        assertEquals("Primary Briar Connector", result.name)
    }

    @Test
    fun `briar only mode returns identity registry profile`() = runTest {
        val canonicalIdentity = CanonicalIdentity(id = "briar-123", displayName = "Briar User")
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "Briar bio"),
            setAsCurrent = true,
        )
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val router = IdentityAwareTransportRouter(canonicalIdentity)
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = router,
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.fetchCurrentUser()

        assertEquals("Briar User", result.name)
        assertEquals("Briar bio", result.bio)
    }

    @Test
    fun `briar only mode fetchCurrentUser falls back to cached identity when router lookup fails`() = runTest {
        val canonicalIdentity = CanonicalIdentity(id = "briar-123", displayName = "Cached User")
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "Cached bio", profilePicturePath = "cached-path"),
            setAsCurrent = true,
        )
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = ThrowingIdentityTransportRouter(IllegalStateException("runtime not ready")),
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.fetchCurrentUser()

        assertEquals("Cached User", result.name)
        assertEquals("Cached bio", result.bio)
        assertEquals("cached-path", result.profilePicturePath)
    }

    @Test
    fun `briar only mode fetchCurrentUser rethrows cancellation from router lookup`() = runTest {
        val canonicalIdentity = CanonicalIdentity(id = "briar-123", displayName = "Cached User")
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "Cached bio", profilePicturePath = "cached-path"),
            setAsCurrent = true,
        )
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = ThrowingIdentityTransportRouter(CancellationException("cancelled")),
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val failure = runCatching { repository.fetchCurrentUser() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }

    @Test
    fun `briar only mode updateCurrentUser falls back to cached identity when router lookup fails`() = runTest {
        val canonicalIdentity = CanonicalIdentity(id = "briar-123", displayName = "Cached User")
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "Cached bio"),
            setAsCurrent = true,
        )
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = ThrowingIdentityTransportRouter(IllegalStateException("runtime not ready")),
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.updateCurrentUser(name = "", bio = "Updated bio")

        val updatedRecord = identityRegistry.identitiesSnapshot().single()
        assertEquals("Cached User", updatedRecord.canonicalIdentity.displayName)
        assertEquals("Updated bio", updatedRecord.profile.bio)
    }

    @Test
    fun `briar only mode fetchCurrentUser uses registry display name after local profile update`() = runTest {
        val canonicalIdentity = CanonicalIdentity(id = "briar-123", displayName = "Router Name")
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "Briar bio"),
            setAsCurrent = true,
        )
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val router = IdentityAwareTransportRouter(canonicalIdentity)
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = router,
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.updateCurrentUser(name = "Updated Name", bio = "")
        val result = repository.fetchCurrentUser()

        assertEquals("Updated Name", result.name)
        assertEquals("Briar bio", result.bio)
    }

    @Test
    fun `briar only mode keeps registry display name when updating bio with blank name`() = runTest {
        val registryIdentity = CanonicalIdentity(id = "briar-123", displayName = "Registry Name")
        val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = registryIdentity,
            aliases = mapOf(TransportId.BRIAR to registryIdentity.id),
            profile = IdentityProfile(bio = "Existing bio"),
            setAsCurrent = true,
        )
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val staleRouterIdentity = CanonicalIdentity(id = "briar-123", displayName = "Stale Router Name")
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = IdentityAwareTransportRouter(staleRouterIdentity),
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.updateCurrentUser(name = "", bio = "Updated bio")

        val updatedRecord = identityRegistry.identitiesSnapshot().single()
        assertEquals("Registry Name", updatedRecord.canonicalIdentity.displayName)
        assertEquals("Updated bio", updatedRecord.profile.bio)
    }

    @Test
    fun `briar only mode skips identity upsert when profile update is empty`() = runTest {
        val store = InMemoryIdentityRegistryStore()
        val identityRegistry = IdentityRegistryImpl(store)
        val canonicalIdentity = CanonicalIdentity(id = "briar-self", displayName = "Briar User")
        identityRegistry.upsertIdentity(
            identity = canonicalIdentity,
            aliases = mapOf(TransportId.BRIAR to canonicalIdentity.id),
            profile = IdentityProfile(bio = "Existing bio", profilePicturePath = "existing-path"),
            setAsCurrent = true,
        )
        store.resetPersistCount()
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val repository = RouterMyAccountRepository(
            authService = authService,
            peopleSync = RecordingPeopleSync(),
            transportRouter = IdentityAwareTransportRouter(canonicalIdentity),
            connectorRegistry = FakeConnectorRegistry(FakeAccountConnector()),
            transportBridge = bridge,
            identityRegistry = identityRegistry,
            briarRuntimeManager = runtimeManager,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.updateCurrentUser(name = " ", bio = "", profilePicturePath = " ")

        assertEquals(0, store.persistCount)
        val record = identityRegistry.identitiesSnapshot().single()
        assertEquals("Briar User", record.canonicalIdentity.displayName)
        assertEquals("Existing bio", record.profile.bio)
        assertEquals("existing-path", record.profile.profilePicturePath)
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

    private class FakeAccountConnector(
        override val transport: TransportId = TransportId.FIRESTORE,
        lifecycleState: ConnectorLifecycleState = ConnectorLifecycleState.READY,
        statusState: ConnectorStatus = ConnectorStatus.ACTIVE,
        private val fetchFailure: Throwable? = null,
        private val updateFailure: Throwable? = null,
    ) : TransportConnector, AccountConnector {
        override val status: StateFlow<ConnectorStatus> = MutableStateFlow(statusState)
        override val lifecycle: StateFlow<ConnectorLifecycleState> = MutableStateFlow(lifecycleState)
        override val capabilities: StateFlow<ConnectorCapabilities> = MutableStateFlow(ConnectorCapabilities.EMPTY)
        var profile: User = User(name = "Ada", bio = "Bio", profilePicturePath = null, registrationTokens = mutableListOf())
        val updates = mutableListOf<AccountConnector.AccountProfileUpdate>()

        override suspend fun fetchAccountProfile(): User {
            fetchFailure?.let { throw it }
            return profile
        }

        override suspend fun updateAccountProfile(update: AccountConnector.AccountProfileUpdate) {
            updateFailure?.let { throw it }
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

    private class RecordingBriarRuntimeManager : BriarRuntimeManager {
        var stopCalls = 0
        override val status: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
        override val diagnostics: SharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val chatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(stubBriarChatGateway())
        override val contactService: StateFlow<BriarContactService> = MutableStateFlow(stubBriarContactService())
        override suspend fun ensureStarted() = Unit
        override suspend fun createAccount(name: String, password: String): Boolean = true
        override suspend fun signIn(password: String): Boolean = true
        override suspend fun stop() { stopCalls += 1 }
    }

    private class FakeTransportRuntimeBridge : TransportRuntimeBridge {
        private val modeState = MutableStateFlow(BriarTransportMode.FIRESTORE)
        override val currentMode: StateFlow<BriarTransportMode> = modeState.asStateFlow()
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
        override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val briarChatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(stubBriarChatGateway())
        override val briarContactService: StateFlow<BriarContactService> = MutableStateFlow(stubBriarContactService())
        override fun requireFirestore(caller: String) = Unit

        fun setMode(mode: BriarTransportMode) {
            modeState.value = mode
        }
    }

    private class IdentityAwareTransportRouter(
        private val identity: CanonicalIdentity,
    ) : TransportRouter {
        override val currentIdentity: Flow<CanonicalIdentity> = MutableStateFlow(identity)
        override suspend fun ensureCurrentIdentity(): CanonicalIdentity = identity
        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation =
            throw UnsupportedOperationException()

        override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> = emptyFlow()
        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
        override suspend fun reset() = Unit
    }

    private class ThrowingIdentityTransportRouter(
        private val failure: Throwable,
    ) : TransportRouter {
        override val currentIdentity: Flow<CanonicalIdentity> = emptyFlow()
        override suspend fun ensureCurrentIdentity(): CanonicalIdentity = throw failure
        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation =
            throw UnsupportedOperationException()

        override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> = emptyFlow()
        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
        override suspend fun reset() = Unit
    }

    private class InMemoryIdentityRegistryStore(
        initialState: IdentityRegistryStore.StoredState = IdentityRegistryStore.StoredState(emptyMap(), null)
    ) : IdentityRegistryStore {
        private var state: IdentityRegistryStore.StoredState = initialState
        var persistCount: Int = 0
            private set

        override fun load(): IdentityRegistryStore.StoredState = state

        override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
            persistCount += 1
            state = IdentityRegistryStore.StoredState(records.toMap(), currentIdentityId)
        }

        fun resetPersistCount() {
            persistCount = 0
        }
    }

    private class DualConnectorRegistry(
        private val primary: TransportConnector,
        private val fallback: TransportConnector,
    ) : ConnectorRegistry {
        override val connectors: Set<TransportConnector> = setOf(primary, fallback)

        override fun connectorFor(transportId: TransportId): TransportConnector? {
            return connectors.firstOrNull { it.transport == transportId }
        }

        override fun primaryFor(mode: BriarTransportMode): TransportConnector {
            return if (mode == BriarTransportMode.HYBRID || mode == BriarTransportMode.BRIAR_ONLY) {
                primary
            } else {
                fallback
            }
        }

        override fun mirrorsFor(mode: BriarTransportMode): List<TransportConnector> = emptyList()
    }

    private class OrderedFallbackConnectorRegistry(
        private val primary: TransportConnector,
        private val firstFallback: TransportConnector,
        private val secondFallback: TransportConnector,
    ) : ConnectorRegistry {
        private val ordered = listOf(primary, firstFallback, secondFallback)
        override val connectors: Set<TransportConnector> = linkedSetOf(primary, firstFallback, secondFallback)

        override fun connectorFor(transportId: TransportId): TransportConnector? {
            return ordered.firstOrNull { it.transport == transportId }
        }

        override fun primaryFor(mode: BriarTransportMode): TransportConnector {
            return if (mode == BriarTransportMode.HYBRID || mode == BriarTransportMode.BRIAR_ONLY) {
                primary
            } else {
                secondFallback
            }
        }

        override fun mirrorsFor(mode: BriarTransportMode): List<TransportConnector> = emptyList()
    }

    private class NonAccountConnector(
        override val transport: TransportId,
    ) : TransportConnector {
        private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)
        private val lifecycleFlow = MutableStateFlow(ConnectorLifecycleState.READY)
        private val capabilityFlow = MutableStateFlow(ConnectorCapabilities.EMPTY)

        override val status: StateFlow<ConnectorStatus> = statusFlow
        override val lifecycle: StateFlow<ConnectorLifecycleState> = lifecycleFlow
        override val capabilities: StateFlow<ConnectorCapabilities> = capabilityFlow

        override suspend fun currentIdentity(): CanonicalIdentity =
            CanonicalIdentity(id = "self", displayName = "Self")

        override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId {
            return TransportConversationId(conversation.id, conversation.id)
        }

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> = emptyFlow()

        override fun observeContacts(): Flow<List<ConnectorContact>> = emptyFlow()

        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
    }
}
