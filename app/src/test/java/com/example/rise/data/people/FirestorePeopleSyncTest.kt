package com.example.rise.data.people

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.TransportId
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModel
import com.example.rise.util.MainDispatcherRule
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePeopleSyncTest {

  @get:Rule
  val dispatcherRule = MainDispatcherRule()

  @Test
  fun `listener error should reach people view model`() = runTest {
    var capturedListener: EventListener<QuerySnapshot>? = null
    val registration = object : ListenerRegistration {
      override fun remove() = Unit
    }

    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(object : BriarChatGateway {
        override val isAvailable: Boolean = false
      })
      override val briarContactService = MutableStateFlow(object : BriarContactService {
        override val isAvailable: Boolean = false
      })

      override fun requireFirestore(caller: String) = Unit
    }

    val job = SupervisorJob()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(job + dispatcher)
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val sync = FirestorePeopleSync(
      auth = null,
      firestore = null,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      job = job,
      scope = scope,
      currentUserIdProvider = { null },
      listenerBinder = { listener ->
        capturedListener = listener
        registration
      },
    )
    val repository = RouterPeopleRepository(identityRegistry, sync)
    val viewModel = PeopleViewModel(repository)

    viewModel.start()
    advanceUntilIdle()

    val listener = capturedListener
    assertNotNull("Expected snapshot listener to be registered", listener)
    val exception = FirebaseFirestoreException(
      "Permission denied",
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
    listener!!.onEvent(null, exception)
    advanceUntilIdle()

    assertEquals("Permission denied", viewModel.uiState.value.errorMessage)
  }

  private class InMemoryIdentityRegistryStore : IdentityRegistryStore {
    private var state = IdentityRegistryStore.StoredState(emptyMap(), null)

    override fun load(): IdentityRegistryStore.StoredState = state

    override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
      state = IdentityRegistryStore.StoredState(records.toMap(), currentIdentityId)
    }
  }

  @Test
  fun `listener error retries with exponential backoff`() = runTest {
    var currentListener: EventListener<QuerySnapshot>? = null
    val registration = object : ListenerRegistration {
      override fun remove() = Unit
    }
    val delays = mutableListOf<Long>()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(object : BriarChatGateway {
        override val isAvailable: Boolean = false
      })
      override val briarContactService = MutableStateFlow(object : BriarContactService {
        override val isAvailable: Boolean = false
      })
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
      auth = null,
      firestore = null,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      job = job,
      scope = scope,
      currentUserIdProvider = { null },
      listenerBinder = { listener ->
        currentListener = listener
        registration
      },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 4_000,
      backoffMultiplier = 2.0,
      delayProvider = { delayMillis ->
        delays += delayMillis
        testScheduler.advanceTimeBy(delayMillis)
      },
    )

    sync.ensureStarted()
    assertNotNull("Expected initial listener registration", currentListener)

    repeat(3) { index ->
      currentListener!!.onEvent(null, error)
      runCurrent()
      val expectedDelay = when (index) {
        0 -> 1_000L
        1 -> 2_000L
        else -> 4_000L
      }
      assertEquals(expectedDelay, delays[index])
      assertNotNull("Expected listener re-registration after error", currentListener)
    }
  }

  @Test
  fun `successful snapshot resets retry delay`() = runTest {
    var currentListener: EventListener<QuerySnapshot>? = null
    val registration = object : ListenerRegistration {
      override fun remove() = Unit
    }
    val delays = mutableListOf<Long>()
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(object : BriarChatGateway {
        override val isAvailable: Boolean = false
      })
      override val briarContactService = MutableStateFlow(object : BriarContactService {
        override val isAvailable: Boolean = false
      })
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
      auth = null,
      firestore = null,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      job = job,
      scope = scope,
      currentUserIdProvider = { null },
      listenerBinder = { listener ->
        currentListener = listener
        registration
      },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 4_000,
      backoffMultiplier = 2.0,
      delayProvider = { delayMillis ->
        delays += delayMillis
        testScheduler.advanceTimeBy(delayMillis)
      },
    )

    sync.ensureStarted()
    val listener: EventListener<QuerySnapshot> = requireNotNull(currentListener)

    // First failure -> 1_000
    listener.onEvent(null, error)
    runCurrent()
    // Update to new listener
    val listenerAfterFirstRetry = requireNotNull(currentListener)

    // Second failure -> 2_000
    listenerAfterFirstRetry.onEvent(null, error)
    runCurrent()
    val listenerAfterSecondRetry = requireNotNull(currentListener)

    // Successful snapshot should reset backoff delay
    listenerAfterSecondRetry.onEvent(null, null)
    runCurrent()
    val listenerAfterSuccess = requireNotNull(currentListener)

    // Next failure should use initial delay (1_000) again
    listenerAfterSuccess.onEvent(null, error)
    runCurrent()

    assertEquals(listOf(1_000L, 2_000L, 1_000L), delays)
  }

  @Test
  fun `error after restart should still trigger listener re-registration`() = runTest {
    var currentListener: EventListener<QuerySnapshot>? = null
    val registration = ListenerRegistration { }
    var registrations = 0
    val transportBridge = object : TransportRuntimeBridge {
      override val currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
      override val runtimeStatus = MutableStateFlow(BriarRuntimeStatus.stopped)
      override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
      override val briarChatGateway = MutableStateFlow(object : BriarChatGateway {
        override val isAvailable: Boolean = false
      })
      override val briarContactService = MutableStateFlow(object : BriarContactService {
        override val isAvailable: Boolean = false
      })
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
      auth = null,
      firestore = null,
      identityRegistry = identityRegistry,
      transportBridge = transportBridge,
      job = job,
      scope = scope,
      currentUserIdProvider = { "self" },
      listenerBinder = { listener ->
        registrations += 1
        currentListener = listener
        registration
      },
      initialRetryDelayMillis = 1_000,
      maxRetryDelayMillis = 1_000,
      backoffMultiplier = 2.0,
      delayProvider = { },
    )

    sync.ensureStarted()
    assertEquals(1, registrations)

    sync.stop()
    sync.ensureStarted()
    assertEquals(2, registrations)

    currentListener!!.onEvent(null, error)
    runCurrent()

    assertEquals("Expected listener to re-register after error even after restart", 3, registrations)
  }

  @Test
  fun `older snapshot finishing after newer snapshot should retain new contacts`() = runTest {
    val registry = BlockingIdentityRegistry(blockingCanonicalId = "alice")
    val alice = FirestoreSnapshotEntry(
      canonicalId = "alice",
      user = User(name = "Alice", bio = "bio", profilePicturePath = null, registrationTokens = mutableListOf()),
    )
    val bob = FirestoreSnapshotEntry(
      canonicalId = "bob",
      user = User(name = "Bob", bio = "bio", profilePicturePath = null, registrationTokens = mutableListOf()),
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

    val finalIds = registry.identitiesSnapshot().map { it.identity.id }
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
    return records[canonicalId]?.identity
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
        identity = identity,
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
