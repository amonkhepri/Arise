package com.example.rise.data.people

import com.example.rise.testutil.stubBriarChatGateway
import com.example.rise.testutil.stubBriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorCapabilities
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.util.MainDispatcherRule
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePeopleSyncTest {

  @get:Rule
  val dispatcherRule = MainDispatcherRule()

  @Test
  fun `listener error should reach people view model`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())

      override fun requireFirestore(caller: String) = Unit
    }

    val job = SupervisorJob()
   val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
    )
    val repository = RouterPeopleRepositoryImpl(identityRegistry, sync)
    val viewModel = PeopleViewModel(repository)

    viewModel.start()
    advanceUntilIdle()

    val exception = FirebaseFirestoreException(
      "Permission denied",
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
    connector.emitError(exception)
    advanceUntilIdle()

    assertEquals("Permission denied", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `transient listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val transient = FirebaseFirestoreException(
      "Service unavailable",
      FirebaseFirestoreException.Code.UNAVAILABLE,
    )
    harness.connector.emitError(transient)
    advanceUntilIdle()

    assertTrue("Expected transient errors to stay in retry path", harness.surfacedErrors.isEmpty())
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped transient listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      FirebaseFirestoreException(
        "Service unavailable",
        FirebaseFirestoreException.Code.UNAVAILABLE,
      ),
    )
    harness.connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped transient errors to stay in retry path",
      harness.surfacedErrors.isEmpty(),
    )
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped socket timeout listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketTimeoutException("connection timed out"),
    )
    harness.connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped SocketTimeoutException to stay in retry path",
      harness.surfacedErrors.isEmpty(),
    )
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped unavailable listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      RuntimeException("UNAVAILABLE: io exception"),
    )
    harness.connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped UNAVAILABLE message to stay in retry path",
      harness.surfacedErrors.isEmpty(),
    )
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped ENOTFOUND listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      RuntimeException("getaddrinfo ENOTFOUND example.com"),
    )
    harness.connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENOTFOUND message to stay in retry path",
      harness.surfacedErrors.isEmpty(),
    )
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped network unreachable listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Network is unreachable"),
    )
    harness.connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped network unreachable SocketException to stay in retry path",
      harness.surfacedErrors.isEmpty(),
    )
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped no address is associated with this host name listener error retries without surfacing sync error`() = runTest {
    val harness = startFirestoreRetryHarness()

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No address is associated with this host name"),
    )
    harness.connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no address is associated with this host name SocketException to stay in retry path",
      harness.surfacedErrors.isEmpty(),
    )
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  @Test
  fun `wrapped temporary name resolution listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Temporary failure in name resolution"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped name resolution SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped name or service not known listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Name or service not known"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped name or service not known SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped no such host is known listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No such host is known"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no such host is known SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped no such host listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No such host"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no such host SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped nodename nor servname listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("nodename nor servname provided, or not known"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped nodename nor servname SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped unknown host no address listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      UnknownHostException("No address associated with hostname"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped unknown host no address error to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped no address associated socket listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No address associated with hostname"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no address associated SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped no address associated with host name socket listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("No address associated with host name"),
      expectedNoSurfaceMessage =
        "Expected wrapped no address associated with host name SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped unable to resolve host socket listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Unable to resolve host"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped unable to resolve host SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped network is down listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Network is down"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped network down SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped no route to host listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No route to host"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no route to host SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped host is down listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Host is down"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped host down SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped connection reset listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Connection reset by peer"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped connection reset SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped ECONNRESET listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ECONNRESET"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ECONNRESET SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped connection reset runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("Connection reset by peer"),
      expectedNoSurfaceMessage = "Expected wrapped connection reset runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped ENETRESET runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("ENETRESET"),
      expectedNoSurfaceMessage = "Expected wrapped ENETRESET runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped network dropped connection on reset listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Network dropped connection on reset"),
      expectedNoSurfaceMessage =
        "Expected wrapped network dropped connection on reset SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped EAI_AGAIN runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("getaddrinfo EAI_AGAIN example.com"),
      expectedNoSurfaceMessage = "Expected wrapped EAI_AGAIN runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped ETIMEDOUT runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("ETIMEDOUT"),
      expectedNoSurfaceMessage = "Expected wrapped ETIMEDOUT runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped EHOSTDOWN listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("EHOSTDOWN"),
      expectedNoSurfaceMessage = "Expected wrapped EHOSTDOWN SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped ECONNREFUSED runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("ECONNREFUSED"),
      expectedNoSurfaceMessage = "Expected wrapped ECONNREFUSED runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped EPIPE runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("EPIPE"),
      expectedNoSurfaceMessage = "Expected wrapped EPIPE runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped no such host is known runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("No such host is known"),
      expectedNoSurfaceMessage = "Expected wrapped no such host runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped no route to host runtime listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = RuntimeException("No route to host"),
      expectedNoSurfaceMessage = "Expected wrapped no route to host runtime error to stay in retry path",
    )
  }

  @Test
  fun `wrapped connection lost listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Connection lost"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped connection lost SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped connection closed listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Connection closed"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped connection closed SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped connection terminated listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Connection terminated"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped connection terminated SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped socket closed listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Socket closed"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped socket closed SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped socket is closed listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Socket is closed"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped socket is closed SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped connection refused listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Connection refused"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped connection refused SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped ECONNREFUSED listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ECONNREFUSED"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ECONNREFUSED SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped failed to connect listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      ConnectException("Failed to connect to firestore backend"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped failed-to-connect ConnectException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped broken pipe listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Broken pipe"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped broken pipe SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped ENETUNREACH listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ENETUNREACH"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENETUNREACH SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped EHOSTUNREACH listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("EHOSTUNREACH"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped EHOSTUNREACH SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped ETIMEDOUT listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ETIMEDOUT"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ETIMEDOUT SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped ENETDOWN listener error retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ENETDOWN"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENETDOWN SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped connection abort listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Software caused connection abort"),
      expectedNoSurfaceMessage =
        "Expected wrapped connection abort SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped windows connection aborted listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("An established connection was aborted by the software in your host machine"),
      expectedNoSurfaceMessage =
        "Expected wrapped Windows connection-aborted SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped ECONNABORTED listener error retries without surfacing sync error`() = runTest {
    assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("ECONNABORTED"),
      expectedNoSurfaceMessage =
        "Expected wrapped ECONNABORTED SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped connect exception without message retries without surfacing sync error`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      ConnectException(),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ConnectException without message to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped auth listener error surfaces sync error even when transient firestore error appears first`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val unavailableError = FirebaseFirestoreException(
      "Service unavailable",
      FirebaseFirestoreException.Code.UNAVAILABLE,
    ).apply {
      initCause(
        FirebaseFirestoreException(
          "User unauthenticated",
          FirebaseFirestoreException.Code.UNAUTHENTICATED,
        ),
      )
    }
    val wrappedAuth = IllegalStateException("Listener wrapper", unavailableError)
    connector.emitError(wrappedAuth)
    advanceUntilIdle()

    assertEquals(1, surfacedErrors.size)
    assertTrue(surfacedErrors.single() is IllegalStateException)
    assertEquals("Listener wrapper", surfacedErrors.single().message)
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `briar transient listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val transient = IllegalStateException("Briar runtime is not ready")
    connector.emitError(transient)
    advanceUntilIdle()

    assertTrue("Expected transient Briar errors to stay in retry path", surfacedErrors.isEmpty())
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `briar connectivity listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val transient = IllegalStateException("Briar connection lost")
    connector.emitError(transient)
    advanceUntilIdle()

    assertTrue("Expected transient Briar connectivity errors to stay in retry path", surfacedErrors.isEmpty())
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `briar unavailable listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val transient = IllegalStateException("Briar transport unavailable")
    connector.emitError(transient)
    advanceUntilIdle()

    assertTrue("Expected unavailable Briar errors to stay in retry path", surfacedErrors.isEmpty())
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `briar timeout listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val transient = IllegalStateException("Briar request timed out")
    connector.emitError(transient)
    advanceUntilIdle()

    assertTrue("Expected timeout Briar errors to stay in retry path", surfacedErrors.isEmpty())
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar transient listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      IllegalStateException("Briar connection lost"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped transient Briar connectivity errors to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar socket timeout retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketTimeoutException("connection timed out"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped SocketTimeoutException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar socket timeout without message retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketTimeoutException(),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped message-less SocketTimeoutException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar ENETRESET runtime listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      RuntimeException("ENETRESET"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENETRESET runtime error to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar network dropped connection on reset retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Network dropped connection on reset"),
      expectedNoSurfaceMessage =
        "Expected wrapped network dropped connection on reset SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar EAI_AGAIN runtime listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      RuntimeException("getaddrinfo EAI_AGAIN example.com"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped EAI_AGAIN runtime error to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar ENOTFOUND runtime listener error retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      RuntimeException("getaddrinfo ENOTFOUND example.com"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENOTFOUND runtime error to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar network unreachable retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Network is unreachable"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped network unreachable SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar ENETUNREACH retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ENETUNREACH"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENETUNREACH SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar EHOSTUNREACH retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("EHOSTUNREACH"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped EHOSTUNREACH SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar network is down retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Network is down"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped network down SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar ENETDOWN retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ENETDOWN"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ENETDOWN SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar ETIMEDOUT retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ETIMEDOUT"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ETIMEDOUT SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar no route to host retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No route to host"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no route to host SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar temporary name resolution failure retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Temporary failure in name resolution"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped temporary name resolution SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar no such host is known retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No such host is known"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no such host is known SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar no such host retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No such host"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no such host SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar nodename nor servname provided retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("nodename nor servname provided, or not known"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped nodename nor servname SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar unknown host retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      UnknownHostException("No address associated with hostname"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped UnknownHostException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar no address associated with hostname retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No address associated with hostname"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no address associated with hostname SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar no address associated with host name retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No address associated with host name"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no address associated with host name SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar no address is associated with this host name retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("No address is associated with this host name"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped no address is associated with this host name SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar unable to resolve host retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Unable to resolve host"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped unable to resolve host SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar host is down retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Host is down"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped host down SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar EHOSTDOWN retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("EHOSTDOWN"),
      expectedNoSurfaceMessage =
        "Expected wrapped EHOSTDOWN SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar broken pipe retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Broken pipe"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped broken pipe SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar ECONNRESET retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ECONNRESET"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ECONNRESET SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar connection abort retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("Connection abort"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped connection abort SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar windows connection aborted retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("An established connection was aborted by the software in your host machine"),
      expectedNoSurfaceMessage =
        "Expected wrapped Windows connection-aborted SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar ECONNABORTED retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      SocketException("ECONNABORTED"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped ECONNABORTED SocketException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar connection closed retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Connection closed"),
      expectedNoSurfaceMessage =
        "Expected wrapped connection closed SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar connection terminated retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Connection terminated"),
      expectedNoSurfaceMessage =
        "Expected wrapped connection terminated SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar socket closed retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Socket closed"),
      expectedNoSurfaceMessage =
        "Expected wrapped socket closed SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar socket is closed retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("Socket is closed"),
      expectedNoSurfaceMessage =
        "Expected wrapped socket is closed SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar ECONNREFUSED retries without surfacing sync error`() = runTest {
    assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
      wrappedCause = SocketException("ECONNREFUSED"),
      expectedNoSurfaceMessage =
        "Expected wrapped ECONNREFUSED SocketException to stay in retry path",
    )
  }

  @Test
  fun `wrapped briar failed to connect retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      ConnectException("Failed to connect to briar daemon"),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped failed-to-connect ConnectException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `wrapped briar connect exception without message retries without surfacing sync error`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val wrappedTransient = IllegalStateException(
      "Listener wrapper",
      ConnectException(),
    )
    connector.emitError(wrappedTransient)
    advanceUntilIdle()

    assertTrue(
      "Expected wrapped message-less ConnectException to stay in retry path",
      surfacedErrors.isEmpty(),
    )
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `briar non transient connection error surfaces sync error and retries`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    val nonTransient = IllegalStateException("Connection permission denied")
    connector.emitError(nonTransient)
    advanceUntilIdle()

    assertEquals(1, surfacedErrors.size)
    val surfaced = surfacedErrors.single()
    assertEquals(IllegalStateException::class.java, surfaced::class.java)
    assertEquals("Connection permission denied", surfaced.message)
    assertEquals(2, connector.observeContactsCalls)
    errorsJob.cancel()
  }

  @Test
  fun `briar only mode skips firestore sync`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY) {
      error("should not be called")
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
    )

    sync.ensureStarted()
    advanceUntilIdle()

    assertEquals(0, connector.observeContactsCalls)
  }

  @Test
  fun `firestore mode skips briar sync`() = runTest {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.FIRESTORE)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
    )

    sync.ensureStarted()
    advanceUntilIdle()

    assertEquals(0, connector.observeContactsCalls)
  }

  private fun testTransportBridge(
    mode: BriarTransportMode,
    onRequireFirestore: (String) -> Unit = {},
  ): TransportRuntimeBridge = object : TransportRuntimeBridge {
    override val currentMode = MutableStateFlow(mode)
    override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
    override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
    override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
    override val briarContactService = MutableStateFlow(stubBriarContactService())

    override fun requireFirestore(caller: String) = onRequireFirestore(caller)
  }

  private data class FirestoreRetryHarness(
    val connector: FakeFirestoreConnector,
    val surfacedErrors: MutableList<Throwable>,
    val errorsJob: Job,
  ) {
    fun cancel() {
      errorsJob.cancel()
    }
  }

  private fun TestScope.startFirestoreRetryHarness(): FirestoreRetryHarness {
    val connector = FakeFirestoreConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.FIRESTORE)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)
    return FirestoreRetryHarness(
      connector = connector,
      surfacedErrors = surfacedErrors,
      errorsJob = errorsJob,
    )
  }

  private suspend fun TestScope.assertWrappedFirestoreListenerErrorRetriesWithoutSurfacingSyncError(
    wrappedCause: Throwable,
    expectedNoSurfaceMessage: String,
  ) {
    val harness = startFirestoreRetryHarness()
    harness.connector.emitError(IllegalStateException("Listener wrapper", wrappedCause))
    advanceUntilIdle()

    assertTrue(expectedNoSurfaceMessage, harness.surfacedErrors.isEmpty())
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  private data class BriarRetryHarness(
    val connector: FakeBriarConnector,
    val surfacedErrors: MutableList<Throwable>,
    val errorsJob: Job,
  ) {
    fun cancel() {
      errorsJob.cancel()
    }
  }

  private fun TestScope.startBriarRetryHarness(): BriarRetryHarness {
    val connector = FakeBriarConnector()
    val transportBridge = testTransportBridge(BriarTransportMode.BRIAR_ONLY)
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { },
    )
    val surfacedErrors = mutableListOf<Throwable>()
    val errorsJob = launch {
      sync.syncPeopleErrors.collect { surfacedErrors += it }
    }

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)
    return BriarRetryHarness(
      connector = connector,
      surfacedErrors = surfacedErrors,
      errorsJob = errorsJob,
    )
  }

  private suspend fun TestScope.assertWrappedBriarListenerErrorRetriesWithoutSurfacingSyncError(
    wrappedCause: Throwable,
    expectedNoSurfaceMessage: String,
  ) {
    val harness = startBriarRetryHarness()
    harness.connector.emitError(IllegalStateException("Listener wrapper", wrappedCause))
    advanceUntilIdle()

    assertTrue(expectedNoSurfaceMessage, harness.surfacedErrors.isEmpty())
    assertEquals(2, harness.connector.observeContactsCalls)
    harness.cancel()
  }

  private class InMemoryIdentityRegistryStore : IdentityRegistryStore {
    private var state = IdentityRegistryStore.StoredState(emptyMap(), null)

    override fun load(): IdentityRegistryStore.StoredState = state

    override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
      state = IdentityRegistryStore.StoredState(records.toMap(), currentIdentityId)
    }
  }

  private class FakeFirestoreConnector : TransportConnector {
    private val contacts = MutableSharedFlow<List<ConnectorContact>>(extraBufferCapacity = Int.MAX_VALUE)
    private val errors = MutableSharedFlow<Throwable>(extraBufferCapacity = Int.MAX_VALUE)
    private val _status = MutableStateFlow(ConnectorStatus.ACTIVE)
    private val _lifecycle = MutableStateFlow(ConnectorLifecycleState.READY)

    override val status = _status
    override val lifecycle: StateFlow<ConnectorLifecycleState> = _lifecycle
    override val capabilities: StateFlow<ConnectorCapabilities> = MutableStateFlow(ConnectorCapabilities.EMPTY)
    override val transport: TransportId = TransportId.FIRESTORE
    var observeContactsCalls: Int = 0

    override suspend fun currentIdentity(): CanonicalIdentity {
      throw UnsupportedOperationException("Not needed in test")
    }

    override suspend fun ensureConversation(conversation: com.example.rise.transport.router.CanonicalConversation): TransportConversationId {
      throw UnsupportedOperationException("Not needed in test")
    }

    override fun observeMessages(conversationId: String): Flow<List<com.example.rise.transport.router.ConnectorInboundMessage>> {
      throw UnsupportedOperationException("Not needed in test")
    }

    override fun observeContacts(): Flow<List<ConnectorContact>> = callbackFlow {
      observeContactsCalls += 1
      val contactsJob = launch {
        contacts.collect { entries ->
          trySend(entries).isSuccess
        }
      }
      val errorsJob = launch {
        errors.collect { error ->
          close(error)
        }
      }
      awaitClose {
        contactsJob.cancel()
        errorsJob.cancel()
      }
    }

    override suspend fun sendMessage(message: com.example.rise.transport.router.ConnectorOutboundMessage) {
      throw UnsupportedOperationException("Not needed in test")
    }

    suspend fun emitContacts(entries: List<ConnectorContact>) {
      contacts.emit(entries)
    }

    suspend fun emitError(error: Throwable) {
      errors.emit(error)
    }
  }

  private class FakeBriarConnector : TransportConnector {
    private val contacts = MutableSharedFlow<List<ConnectorContact>>(extraBufferCapacity = Int.MAX_VALUE)
    private val errors = MutableSharedFlow<Throwable>(extraBufferCapacity = Int.MAX_VALUE)
    private val _status = MutableStateFlow(ConnectorStatus.ACTIVE)
    private val _lifecycle = MutableStateFlow(ConnectorLifecycleState.READY)

    override val status = _status
    override val lifecycle: StateFlow<ConnectorLifecycleState> = _lifecycle
    override val capabilities: StateFlow<ConnectorCapabilities> = MutableStateFlow(ConnectorCapabilities.EMPTY)
    override val transport: TransportId = TransportId.BRIAR
    var observeContactsCalls: Int = 0

    override suspend fun currentIdentity(): CanonicalIdentity =
      CanonicalIdentity(id = "self", displayName = "Self")

    override suspend fun ensureConversation(conversation: com.example.rise.transport.router.CanonicalConversation): TransportConversationId {
      throw UnsupportedOperationException("Not needed in test")
    }

    override fun observeMessages(conversationId: String): Flow<List<com.example.rise.transport.router.ConnectorInboundMessage>> {
      throw UnsupportedOperationException("Not needed in test")
    }

    override fun observeContacts(): Flow<List<ConnectorContact>> = callbackFlow {
      observeContactsCalls += 1
      val contactsJob = launch {
        contacts.collect { entries ->
          trySend(entries).isSuccess
        }
      }
      val errorsJob = launch {
        errors.collect { error ->
          close(error)
        }
      }
      awaitClose {
        contactsJob.cancel()
        errorsJob.cancel()
      }
    }

    override suspend fun sendMessage(message: com.example.rise.transport.router.ConnectorOutboundMessage) {
      throw UnsupportedOperationException("Not needed in test")
    }

    suspend fun emitError(error: Throwable) {
      errors.emit(error)
    }
  }

  @Test
  fun `listener error retries with exponential backoff`() = runTest {
    val connector = FakeFirestoreConnector()
    val delays = mutableListOf<Long>()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val error = FirebaseFirestoreException(
      "Permission denied",
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )

    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 4_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { delayMillis ->
        delays += delayMillis
        testScheduler.advanceTimeBy(delayMillis)
      },
    )

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    repeat(3) { index ->
      connector.emitError(error)
      advanceUntilIdle()
      val expectedDelay = when (index) {
        0 -> 1_000L
        1 -> 2_000L
        else -> 4_000L
      }
      assertEquals(expectedDelay, delays[index])
      assertEquals(index + 2, connector.observeContactsCalls)
    }
  }

  @Test
  fun `successful snapshot resets retry delay`() = runTest {
    val connector = FakeFirestoreConnector()
    val delays = mutableListOf<Long>()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val error = FirebaseFirestoreException(
      "Permission denied",
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )

    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 4_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.0,
      retryRandomProvider = { 0.5 },
      delayProvider = { delayMillis ->
        delays += delayMillis
        testScheduler.advanceTimeBy(delayMillis)
      },
    )

    sync.ensureStarted()
    advanceUntilIdle()
    connector.emitError(error)
    advanceUntilIdle()

    connector.emitError(error)
    advanceUntilIdle()

    connector.emitContacts(emptyList())
    advanceUntilIdle()

    connector.emitError(error)
    advanceUntilIdle()

    assertEquals(listOf(1_000L, 2_000L, 1_000L), delays)
  }

  @Test
  fun `error after restart should still trigger listener re-registration`() = runTest {
    val connector = FakeFirestoreConnector()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val error = FirebaseFirestoreException(
      "Permission denied",
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )

    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { "self" },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      delayProvider = { },
    )

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    sync.stop()
    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(2, connector.observeContactsCalls)

    connector.emitError(error)
    advanceUntilIdle()

    assertEquals("Expected listener to re-register after error even after restart", 3, connector.observeContactsCalls)
  }

  @Test
  fun `listener retry applies jitter within bounds and still re-registers`() = runTest {
    val connector = FakeFirestoreConnector()
    val delays = mutableListOf<Long>()
    val randomValues = ArrayDeque(listOf(0.0, 1.0, 0.5))
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val error = FirebaseFirestoreException(
      "Permission denied",
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
    val expectedBaseDelays = listOf(1_000L, 2_000L, 4_000L)

    val sync = FirestorePeopleSync(
      firebaseAuth = null,
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      currentUserIdProvider = { null },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 4_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.1,
      retryRandomProvider = { randomValues.removeFirst() },
      delayProvider = { delayMillis ->
        delays += delayMillis
        testScheduler.advanceTimeBy(delayMillis)
      },
    )

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    repeat(expectedBaseDelays.size) { index ->
      connector.emitError(error)
      advanceUntilIdle()
      val baseDelay = expectedBaseDelays[index]
      val minDelay = (baseDelay * 0.9).toLong()
      val maxDelay = (baseDelay * 1.1).toLong()
      val actualDelay = delays[index]
      assertTrue(
        "Expected jittered delay in [$minDelay, $maxDelay] but was $actualDelay",
        actualDelay in minDelay..maxDelay,
      )
      assertEquals(index + 2, connector.observeContactsCalls)
    }

    assertEquals(listOf(900L, 2_200L, 4_000L), delays)
  }

  @Test
  fun `briar listener retry applies jitter within bounds and still re-registers`() = runTest {
    val connector = FakeBriarConnector()
    val delays = mutableListOf<Long>()
    val randomValues = ArrayDeque(listOf(0.0, 1.0, 0.5))
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(stubBriarChatGateway())
      override val briarContactService = MutableStateFlow(stubBriarContactService())
      override fun requireFirestore(caller: String) = Unit
    }
    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val expectedBaseDelays = listOf(1_000L, 2_000L, 4_000L)

    val sync = BriarPeopleSync(
      transportConnector = connector,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      syncSupervisorJob = job,
      scope = scope,
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 4_000,
      backoffMultiplier = 2.0,
      retryJitterRatio = 0.1,
      retryRandomProvider = { randomValues.removeFirst() },
      delayProvider = { delayMillis ->
        delays += delayMillis
        testScheduler.advanceTimeBy(delayMillis)
      },
    )

    sync.ensureStarted()
    advanceUntilIdle()
    assertEquals(1, connector.observeContactsCalls)

    repeat(expectedBaseDelays.size) { index ->
      connector.emitError(IllegalStateException("retry me"))
      advanceUntilIdle()
      val baseDelay = expectedBaseDelays[index]
      val minDelay = (baseDelay * 0.9).toLong()
      val maxDelay = (baseDelay * 1.1).toLong()
      val actualDelay = delays[index]
      assertTrue(
        "Expected jittered delay in [$minDelay, $maxDelay] but was $actualDelay",
        actualDelay in minDelay..maxDelay,
      )
      assertEquals(index + 2, connector.observeContactsCalls)
    }

    assertEquals(listOf(900L, 2_200L, 4_000L), delays)
  }

  @Test
  fun `older snapshot finishing after newer snapshot should retain new contacts`() = runTest {
    val registry = BlockingIdentityRegistry(blockingCanonicalId = "alice")
    val alice = FirestoreSnapshotEntry(
      canonicalId = "alice",
      user = User(name = "Alice", bio = "bio", profilePicturePath = null, registrationTokens = mutableListOf()),
      presence = PresenceStatus.OFFLINE,
    )
    val bob = FirestoreSnapshotEntry(
      canonicalId = "bob",
      user = User(name = "Bob", bio = "bio", profilePicturePath = null, registrationTokens = mutableListOf()),
      presence = PresenceStatus.ONLINE,
    )

    val firstSnapshot = launch {
      processSnapshot(
        entries = listOf(alice),
        currentUserId = null,
        identityRegistry = registry,
      )
    }

    registry.awaitFirstUpsert()

    val secondSnapshot = launch {
      processSnapshot(
        entries = listOf(alice, bob),
        currentUserId = null,
        identityRegistry = registry,
      )
    }

    secondSnapshot.join()
    registry.allowFirstUpsert()
    firstSnapshot.join()

    val finalIds = registry.identitiesSnapshot().map { it.canonicalIdentity.id }
    assertTrue("Expected registry to retain newer contact bob but was $finalIds", "bob" in finalIds)
  }
}

private class BlockingIdentityRegistry(
  private val blockingCanonicalId: String,
) : IdentityRegistry {

  private val lock = ReentrantLock()
  private val records = LinkedHashMap<String, IdentityRecord>()
  private val aliasIndex = mutableMapOf<Pair<TransportId, String>, String>()
  private val currentIdentityState = MutableStateFlow<CanonicalIdentity?>(null)
  private val identitiesState = MutableStateFlow<List<IdentityRecord>>(emptyList())

  private val shouldBlockNextUpsert = AtomicBoolean(true)
  private val firstUpsertStarted = CompletableDeferred<Unit>()
  private val firstUpsertResume = CompletableDeferred<Unit>()

  suspend fun awaitFirstUpsert() {
    firstUpsertStarted.await()
  }

  fun allowFirstUpsert() {
    firstUpsertResume.complete(Unit)
  }

  override val currentIdentity = currentIdentityState.filterNotNull()

  override val identities = identitiesState

  override fun currentIdentitySnapshot(): CanonicalIdentity? = currentIdentityState.value

  override suspend fun resolveByConnector(transport: TransportId, transportId: String): CanonicalIdentity {
    val canonicalId = aliasIndex[transport to transportId]
      ?: error("Unknown identity for $transport:$transportId")
    return records[canonicalId]?.canonicalIdentity
      ?: error("Missing identity record for $canonicalId")
  }

  override fun conversationId(participants: Set<String>): String =
    participants.sorted().joinToString("|")

  override suspend fun upsertIdentity(
    identity: CanonicalIdentity,
    aliases: Map<TransportId, String>,
    profile: IdentityProfile?,
    setAsCurrent: Boolean,
  ) {
    if (identity.id == blockingCanonicalId && shouldBlockNextUpsert.compareAndSet(true, false)) {
      firstUpsertStarted.complete(Unit)
      firstUpsertResume.await()
    }
    lock.withLock {
      val existing = records[identity.id]
      val mergedAliases = existing?.aliases.orEmpty().toMutableMap().apply {
        putAll(aliases)
      }
      mergedAliases.forEach { (transport, alias) ->
        aliasIndex[transport to alias] = identity.id
      }
      val profileToStore = profile ?: existing?.profile ?: IdentityProfile()
      records[identity.id] = IdentityRecord(
        canonicalIdentity = identity,
        aliases = mergedAliases.toMap(),
        profile = profileToStore,
      )
      if (setAsCurrent || currentIdentityState.value == null) {
        currentIdentityState.value = identity
      }
      identitiesState.value = records.values.toList()
    }
  }

  override suspend fun removeIdentity(canonicalId: String) {
    lock.withLock {
      val removed = records.remove(canonicalId) ?: return
      removed.aliases.forEach { (transport, alias) ->
        aliasIndex.remove(transport to alias)
      }
      if (currentIdentityState.value?.id == canonicalId) {
        currentIdentityState.value = null
      }
      identitiesState.value = records.values.toList()
    }
  }

  override fun identitiesSnapshot(): List<IdentityRecord> =
    lock.withLock {
      records.values.map { it.copy() }
    }

  override suspend fun linkAlias(canonicalId: String, transport: TransportId, transportId: String) {
    lock.withLock {
      val existing = records[canonicalId] ?: return
      val updatedAliases = existing.aliases.toMutableMap().apply {
        this[transport] = transportId
      }
      aliasIndex[transport to transportId] = canonicalId
      records[canonicalId] = existing.copy(aliases = updatedAliases.toMap())
      identitiesState.value = records.values.toList()
    }
  }

  override suspend fun removeAlias(canonicalId: String, transport: TransportId) {
    lock.withLock {
      val existing = records[canonicalId] ?: return
      val updatedAliases = existing.aliases.toMutableMap()
      val removed = updatedAliases.remove(transport) ?: return
      aliasIndex.remove(transport to removed)
      records[canonicalId] = existing.copy(aliases = updatedAliases.toMap())
      identitiesState.value = records.values.toList()
    }
  }

  override suspend fun clear() {
    lock.withLock {
      records.clear()
      aliasIndex.clear()
      currentIdentityState.value = null
      identitiesState.value = emptyList()
    }
  }
}
