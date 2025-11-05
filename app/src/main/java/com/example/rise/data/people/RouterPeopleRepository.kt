package com.example.rise.data.people

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PeopleSync {
    /**
     * Emits sync errors that occur while observing the people data. Consumers should surface the
     * error state but keep listening for subsequent updates because the sync layer will retry.
     */
    val errors: Flow<Throwable>

    /**
     * Emits the canonical ID of the currently signed-in user, or `null` if no user is signed in.
     */
    val currentUserCanonicalId: Flow<String?>

    /**
     * Starts the sync process if it is not already running.
     */
    fun ensureStarted()

    /**
     * Stops the sync process if it is running.
     */
    fun stop()
}

class RouterPeopleRepository(
    private val identityRegistry: IdentityRegistry,
    private val sync: PeopleSync,
) : PeopleRepository {

    override val errors: Flow<Throwable> = sync.errors

    override fun observePeople(): Flow<List<PersonSummary>> {
        sync.ensureStarted()
        val currentUserIds: Flow<String?> = sync.currentUserCanonicalId

        return identityRegistry.identities.combine(currentUserIds) { records, currentUserId ->
            val effectiveCurrentUserId = currentUserId ?: identityRegistry.currentIdentitySnapshot()?.id
            records
                .filter { effectiveCurrentUserId == null || it.identity.id != effectiveCurrentUserId }
                .map { it.toPersonSummary() }
        }
    }

    override fun observePerson(personId: String): Flow<PersonSummary?> {
        sync.ensureStarted()
        return identityRegistry.identities.map { records ->
            records
                .firstOrNull { it.identity.id == personId }
                ?.toPersonSummary()
        }
    }

    //TODO: findPerson is used in tests only
    override suspend fun findPerson(personId: String): PersonSummary? {
        sync.ensureStarted()
        return identityRegistry.identitiesSnapshot()
            .firstOrNull { record -> record.identity.id == personId }
            ?.toPersonSummary()
    }

    private fun IdentityRecord.toPersonSummary(): PersonSummary {
        return PersonSummary(
            id = identity.id,
            name = identity.displayName,
            bio = profile.bio ?: "",
            profilePicturePath = profile.profilePicturePath,
            presence = profile.presence,
        )
    }

}

internal data class FirestoreSnapshotEntry(
    val canonicalId: String,
    val user: User,
)

class FirestorePeopleSync(
    private val auth: FirebaseAuth?,
    private val firestore: FirebaseFirestore?,
    private val identityRegistry: IdentityRegistry,
    private val transportBridge: TransportRuntimeBridge,
    private val job: Job = SupervisorJob(),
    private val scope: CoroutineScope = CoroutineScope(job + Dispatchers.IO),
    private val currentUserIdProvider: () -> String? = { auth?.currentUser?.uid },
    private val listenerBinder: (EventListener<QuerySnapshot>) -> ListenerRegistration = { listener ->
        requireNotNull(firestore) { "Firestore instance required when using default listener binder" }
            .collection("users")
            .addSnapshotListener(listener)
    },
    private val initialRetryDelayMillis: Long = 250,
    private val maxRetryDelayMillis: Long = 30_000,
    private val backoffMultiplier: Double = 2.0,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) : PeopleSync {

    private val started = AtomicBoolean(false)
    private var registration: ListenerRegistration? = null
    private var restartJob: Job? = null
    private var retryDelayMillis: Long = initialRetryDelayMillis
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    private val currentUserIdState = MutableStateFlow(currentUserIdProvider())
    private val snapshotProcessingMutex = Mutex()

    private val authListener: FirebaseAuth.AuthStateListener? = auth?.let { firebaseAuth ->
        FirebaseAuth.AuthStateListener { updatedAuth ->
            val userId = updatedAuth.currentUser?.uid
            currentUserIdState.value = userId
            if (userId != null) {
                ensureStarted()
            } else {
                stop()
            }
        }
    }

    init {
        auth?.let { firebaseAuth ->
            val listener = authListener ?: return@let
            firebaseAuth.addAuthStateListener(listener)
            job.invokeOnCompletion { firebaseAuth.removeAuthStateListener(listener) }
        }
    }

    override val errors: Flow<Throwable> = _errors.asSharedFlow()
    override val currentUserCanonicalId: StateFlow<String?> = currentUserIdState.asStateFlow()

    override fun ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        retryDelayMillis = initialRetryDelayMillis
        restartJob?.cancel()
        registerListener()
    }

    override fun stop() {
        registration?.remove()
        registration = null
        restartJob?.cancel()
        restartJob = null
        job.cancelChildren()
        started.set(false)
        currentUserIdState.value = null
        retryDelayMillis = initialRetryDelayMillis
    }

    private fun registerListener() {
        if (!started.get()) return
        transportBridge.requireFirestore("FirestorePeopleSync#ensureStarted")
        registration = listenerBinder(
            EventListener { snapshot, error ->
                if (error != null) {
                    handleListenerError(error)
                    return@EventListener
                }
                resetBackoff()
                val documents = snapshot?.documents.orEmpty()
                scope.launch {
                    val currentUserId = currentUserIdProvider()
                    currentUserIdState.value = currentUserId
                    val entries = documents.mapNotNull { document ->
                        val user = document.toObject(User::class.java) ?: return@mapNotNull null
                        FirestoreSnapshotEntry(
                            canonicalId = document.id,
                            user = user,
                        )
                    }
                    snapshotProcessingMutex.withLock {
                        processSnapshot(
                            entries = entries,
                            currentUserId = currentUserId,
                            identityRegistry = identityRegistry,
                        )
                    }
                }
            },
        )
    }

    private fun handleListenerError(error: Throwable) {
        _errors.tryEmit(error)
        registration?.remove()
        registration = null
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!started.get()) return
        restartJob?.cancel()
        val delayMillis = retryDelayMillis
        restartJob = scope.launch {
            delayProvider(delayMillis)
            if (!started.get()) return@launch
            registerListener()
        }
        val nextDelay = (retryDelayMillis * backoffMultiplier).toLong()
        retryDelayMillis = nextDelay.coerceAtMost(maxRetryDelayMillis)
    }

    private fun resetBackoff() {
        retryDelayMillis = initialRetryDelayMillis
        restartJob?.cancel()
        restartJob = null
    }
}

internal suspend fun processSnapshot(
    entries: List<FirestoreSnapshotEntry>,
    currentUserId: String?,
    identityRegistry: IdentityRegistry,
) {
    val remoteIds = entries.mapTo(mutableSetOf()) { it.canonicalId }
    val existingIds = identityRegistry.identitiesSnapshot()
        .filter { record ->
            record.identity.id != currentUserId &&
                record.aliases.containsKey(TransportId.FIRESTORE)
        }
        .mapTo(mutableSetOf()) { it.identity.id }
    val (currentEntries, otherEntries) = if (currentUserId == null) {
        emptyList<FirestoreSnapshotEntry>() to entries
    } else {
        entries.partition { it.canonicalId == currentUserId }
    }

    suspend fun upsert(entry: FirestoreSnapshotEntry, setAsCurrent: Boolean) {
        identityRegistry.upsertIdentity(
            identity = CanonicalIdentity(
                id = entry.canonicalId,
                displayName = entry.user.name,
            ),
            aliases = mapOf(TransportId.FIRESTORE to entry.canonicalId),
            profile = IdentityProfile(
                bio = entry.user.bio,
                profilePicturePath = entry.user.profilePicturePath,
                presence = PresenceStatus.UNKNOWN,
            ),
            setAsCurrent = setAsCurrent,
        )
    }

    currentEntries.forEach { entry -> upsert(entry, setAsCurrent = true) }
    otherEntries.forEach { entry -> upsert(entry, setAsCurrent = false) }

    removeMissingContacts(
        identityRegistry = identityRegistry,
        remoteIds = remoteIds,
        currentUserId = currentUserId,
        knownIdsBeforeSnapshot = existingIds
    )
}

internal suspend fun removeMissingContacts(
    identityRegistry: IdentityRegistry,
    remoteIds: Set<String>,
    currentUserId: String?,
    knownIdsBeforeSnapshot: Set<String>,
) {
    identityRegistry.identitiesSnapshot()
        .filter { record ->
            val canonicalId = record.identity.id
            if (canonicalId == currentUserId) return@filter false
            if (canonicalId !in knownIdsBeforeSnapshot) return@filter false
            record.aliases.containsKey(TransportId.FIRESTORE) && canonicalId !in remoteIds
        }
        .forEach { record ->
            identityRegistry.removeIdentity(record.identity.id)
        }
}
