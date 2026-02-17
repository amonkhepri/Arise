package com.example.rise.data.people

import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportId
import com.example.rise.featureflags.BriarTransportMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
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
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

interface PeopleSync {
    /**
     * Emits sync errors that occur while observing the people data. Consumers should surface the
     * error state but keep listening for subsequent updates because the sync layer will retry.
     */
    val syncPeopleErrors: Flow<Throwable>

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

class RouterPeopleRepositoryImpl(
    private val identityRegistry: IdentityRegistry,
    private val peopleSync: PeopleSync,
) : RouterPeopleRepository {

    override val syncPeopleErrors: Flow<Throwable> = peopleSync.syncPeopleErrors

    override fun observePeople(): Flow<List<PersonSummary>> {
        peopleSync.ensureStarted()
        val currentUserIds: Flow<String?> = peopleSync.currentUserCanonicalId

        return identityRegistry.identities.combine(currentUserIds) { records, currentUserId ->
            val effectiveCurrentUserId = currentUserId ?: identityRegistry.currentIdentitySnapshot()?.id
            records
                .filter { effectiveCurrentUserId == null || it.canonicalIdentity.id != effectiveCurrentUserId }
                .map { it.toPersonSummary() }
        }
    }

    override fun observePerson(personId: String): Flow<PersonSummary?> {
        peopleSync.ensureStarted()
        return identityRegistry.identities.map { records ->
            records
                .firstOrNull { it.canonicalIdentity.id == personId }
                ?.toPersonSummary()
        }
    }

    //TODO: findPerson is used in tests only
    override suspend fun findPerson(personId: String): PersonSummary? {
        peopleSync.ensureStarted()
        return identityRegistry.identitiesSnapshot()
            .firstOrNull { record -> record.canonicalIdentity.id == personId }
            ?.toPersonSummary()
    }

    private fun IdentityRecord.toPersonSummary(): PersonSummary {
        return PersonSummary(
            id = canonicalIdentity.id,
            name = canonicalIdentity.displayName,
            bio = profile.bio ?: "",
            profilePicturePath = profile.profilePicturePath,
            presence = profile.presence,
        )
    }

}

internal data class FirestoreSnapshotEntry(
    val canonicalId: String,
    val user: User,
    val presence: PresenceStatus,
)

/**
 * Listens to Firestore roster updates, keeps the identity registry in sync with
 * remote contacts, reacts to auth state changes, and retries the listener with
 * exponential backoff on errors. Exposes current user id and sync errors.
 */
class FirestorePeopleSync(
    private val firebaseAuth: FirebaseAuth?,
    private val transportConnector: TransportConnector,
    private val identityRegistry: IdentityRegistry,
    private val transportBridge: TransportRuntimeBridge,
    private val syncSupervisorJob: Job = SupervisorJob(),
    private val scope: CoroutineScope = CoroutineScope(syncSupervisorJob + Dispatchers.IO),
    private val currentUserIdProvider: () -> String? = { firebaseAuth?.currentUser?.uid },
    private val initialRetryDelayMillis: Long = 250,
    private val maxRetryDelayMillis: Long = 30_000,
    private val backoffMultiplier: Double = 2.0,
    private val retryJitterRatio: Double = 0.1,
    private val retryRandomProvider: () -> Double = { Random.nextDouble() },
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) : PeopleSync {

    private val syncActive = AtomicBoolean(false)
    private var rosterJob: Job? = null
    private var restartJob: Job? = null
    private var retryDelayMillis: Long = initialRetryDelayMillis
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    private val currentUserIdState = MutableStateFlow(currentUserIdProvider())
    private val snapshotProcessingMutex = Mutex()

    private val authListener: FirebaseAuth.AuthStateListener? = firebaseAuth?.let { firebaseAuth ->
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
        require(transportConnector.transport == TransportId.FIRESTORE) {
            "FirestorePeopleSync requires a Firestore transport connector"
        }
        require(retryJitterRatio in 0.0..1.0) {
            "retryJitterRatio must be between 0.0 and 1.0"
        }
        firebaseAuth?.let { firebaseAuth ->
            val listener = authListener ?: return@let
            firebaseAuth.addAuthStateListener(listener)
            syncSupervisorJob.invokeOnCompletion { firebaseAuth.removeAuthStateListener(listener) }
        }
    }

    override val syncPeopleErrors: Flow<Throwable> = _errors.asSharedFlow()
    override val currentUserCanonicalId: StateFlow<String?> = currentUserIdState.asStateFlow()

    override fun ensureStarted() {
        if (!syncActive.compareAndSet(false, true)) {
            return
        }
        retryDelayMillis = initialRetryDelayMillis
        restartJob?.cancel()
        if (transportBridge.currentMode.value != BriarTransportMode.FIRESTORE) {
            syncActive.set(false)
            return
        }
        registerListener()
    }

    override fun stop() {
        rosterJob?.cancel()
        rosterJob = null
        restartJob?.cancel()
        restartJob = null
        syncSupervisorJob.cancelChildren()
        syncActive.set(false)
        currentUserIdState.value = null
        retryDelayMillis = initialRetryDelayMillis
    }

    private fun registerListener() {
        if (!syncActive.get()) return
        transportBridge.requireFirestore("FirestorePeopleSync#ensureStarted")
        rosterJob?.cancel()
        rosterJob = scope.launch {
            try {
                transportConnector.observeContacts().collect { contacts ->
                    resetBackoff()
                    val currentUserId = currentUserIdProvider()
                    currentUserIdState.value = currentUserId
                    val entries = contacts.map { contact ->
                        FirestoreSnapshotEntry(
                            canonicalId = contact.canonicalId,
                            user = contact.toUser(),
                            presence = contact.presence,
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
            } catch (error: Throwable) {
                if (!syncActive.get()) return@launch
                if (error is CancellationException) return@launch
                handleListenerError(error)
            }
        }
    }

    private fun handleListenerError(error: Throwable) {
        if (shouldSurfaceError(error)) {
            _errors.tryEmit(error)
        }
        rosterJob?.cancel()
        rosterJob = null
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!syncActive.get()) return
        restartJob?.cancel()
        val delayMillis = jitterDelay(retryDelayMillis)
        restartJob = scope.launch {
            delayProvider(delayMillis)
            if (!syncActive.get()) return@launch
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

    private fun jitterDelay(baseDelayMillis: Long): Long {
        if (retryJitterRatio == 0.0 || baseDelayMillis <= 0L) return baseDelayMillis
        val randomUnit = retryRandomProvider().coerceIn(0.0, 1.0)
        val centeredRandom = (randomUnit * 2.0) - 1.0
        val jitterFactor = 1.0 + (centeredRandom * retryJitterRatio)
        return (baseDelayMillis * jitterFactor).toLong().coerceAtLeast(1L)
    }

    private fun shouldSurfaceError(error: Throwable): Boolean {
        val causes = generateSequence(error as Throwable?) { it.cause }.toList()
        val firestoreCodes = causes
            .filterIsInstance<FirebaseFirestoreException>()
            .map { it.code }
            .toList()
        val hasAuthError = firestoreCodes.any { code ->
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                code == FirebaseFirestoreException.Code.UNAUTHENTICATED
        }
        if (hasAuthError) return true
        if (firestoreCodes.isNotEmpty()) return false

        val hasTransientTimeout = causes.any { cause ->
            cause is SocketTimeoutException ||
                cause is UnknownHostException ||
                (cause is SocketException &&
                    (
                        cause.message.orEmpty().contains("unreachable", ignoreCase = true) ||
                            cause.message.orEmpty().contains("network is down", ignoreCase = true) ||
                            cause.message.orEmpty().contains("host is down", ignoreCase = true) ||
                            cause.message.orEmpty().contains("no route to host", ignoreCase = true) ||
                            cause.message.orEmpty().contains("name resolution", ignoreCase = true) ||
                            cause.message.orEmpty().contains("name or service not known", ignoreCase = true) ||
                            cause.message.orEmpty().contains("no such host is known", ignoreCase = true) ||
                            cause.message.orEmpty().contains("nodename nor servname provided", ignoreCase = true) ||
                            cause.message.orEmpty().contains("failed to connect", ignoreCase = true) ||
                            cause.message.orEmpty().contains("connection reset", ignoreCase = true) ||
                            cause.message.orEmpty().contains("connection closed", ignoreCase = true) ||
                            cause.message.orEmpty().contains("socket closed", ignoreCase = true) ||
                            cause.message.orEmpty().contains("connection refused", ignoreCase = true) ||
                            cause.message.orEmpty().contains("connection abort", ignoreCase = true) ||
                            cause.message.orEmpty().contains("broken pipe", ignoreCase = true)
                    )) ||
                cause.message.orEmpty().contains("timed out", ignoreCase = true) ||
                cause.message.orEmpty().contains("timeout", ignoreCase = true)
        }
        if (hasTransientTimeout) return false

        return true
    }
}

/**
 * Mirrors Briar contacts into the identity registry, tracking the local Briar identity,
 * applying exponential backoff on listener failures, and surfacing sync errors for callers.
 */
class BriarPeopleSync(
    private val transportConnector: TransportConnector,
    private val identityRegistry: IdentityRegistry,
    private val transportBridge: TransportRuntimeBridge,
    private val syncSupervisorJob: Job = SupervisorJob(),
    private val scope: CoroutineScope = CoroutineScope(syncSupervisorJob + Dispatchers.IO),
    private val initialRetryDelayMillis: Long = 250,
    private val maxRetryDelayMillis: Long = 30_000,
    private val backoffMultiplier: Double = 2.0,
    private val retryJitterRatio: Double = 0.1,
    private val retryRandomProvider: () -> Double = { Random.nextDouble() },
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) : PeopleSync {

    private val syncActive = AtomicBoolean(false)
    private var rosterJob: Job? = null
    private var restartJob: Job? = null
    private var retryDelayMillis: Long = initialRetryDelayMillis
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    private val currentUserIdState = MutableStateFlow<String?>(null)
    private val snapshotProcessingMutex = Mutex()

    init {
        require(transportConnector.transport == TransportId.BRIAR) {
            "BriarPeopleSync requires a Briar transport connector"
        }
        require(retryJitterRatio in 0.0..1.0) {
            "retryJitterRatio must be between 0.0 and 1.0"
        }
    }

    override val syncPeopleErrors: Flow<Throwable> = _errors.asSharedFlow()
    override val currentUserCanonicalId: StateFlow<String?> = currentUserIdState.asStateFlow()

    override fun ensureStarted() {
        if (!syncActive.compareAndSet(false, true)) {
            return
        }
        retryDelayMillis = initialRetryDelayMillis
        restartJob?.cancel()
        if (transportBridge.currentMode.value == BriarTransportMode.FIRESTORE) {
            syncActive.set(false)
            return
        }
        registerListener()
    }

    override fun stop() {
        rosterJob?.cancel()
        rosterJob = null
        restartJob?.cancel()
        restartJob = null
        syncSupervisorJob.cancelChildren()
        syncActive.set(false)
        currentUserIdState.value = null
        retryDelayMillis = initialRetryDelayMillis
    }

    private fun registerListener() {
        if (!syncActive.get()) return
        rosterJob?.cancel()
        rosterJob = scope.launch {
            try {
                transportConnector.observeContacts().collect { contacts ->
                    resetBackoff()
                    val currentUserId = runCatching { transportConnector.currentIdentity().id }.getOrNull()
                    currentUserIdState.value = currentUserId
                    snapshotProcessingMutex.withLock {
                        processBriarSnapshot(
                            contacts = contacts,
                            currentUserId = currentUserId,
                            identityRegistry = identityRegistry,
                        )
                    }
                }
            } catch (error: Throwable) {
                if (!syncActive.get()) return@launch
                if (error is CancellationException) return@launch
                handleListenerError(error)
            }
        }
    }

    private fun handleListenerError(error: Throwable) {
        if (shouldSurfaceError(error)) {
            _errors.tryEmit(error)
        }
        rosterJob?.cancel()
        rosterJob = null
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!syncActive.get()) return
        restartJob?.cancel()
        val delayMillis = jitterDelay(retryDelayMillis)
        restartJob = scope.launch {
            delayProvider(delayMillis)
            if (!syncActive.get()) return@launch
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

    private fun jitterDelay(baseDelayMillis: Long): Long {
        if (retryJitterRatio == 0.0 || baseDelayMillis <= 0L) return baseDelayMillis
        val randomUnit = retryRandomProvider().coerceIn(0.0, 1.0)
        val centeredRandom = (randomUnit * 2.0) - 1.0
        val jitterFactor = 1.0 + (centeredRandom * retryJitterRatio)
        return (baseDelayMillis * jitterFactor).toLong().coerceAtLeast(1L)
    }

    private fun shouldSurfaceError(error: Throwable): Boolean {
        val isTransientBriarState = generateSequence(error as Throwable?) { it.cause }
            .any { cause ->
                val message = cause.message.orEmpty()
                cause is UnknownHostException ||
                    message.contains("not ready", ignoreCase = true) ||
                    message.contains("connection lost", ignoreCase = true) ||
                    message.contains("connection reset", ignoreCase = true) ||
                    message.contains("connection closed", ignoreCase = true) ||
                    message.contains("socket closed", ignoreCase = true) ||
                    message.contains("connection refused", ignoreCase = true) ||
                    message.contains("software caused connection abort", ignoreCase = true) ||
                    message.contains("connection abort", ignoreCase = true) ||
                    message.contains("unreachable", ignoreCase = true) ||
                    message.contains("network is down", ignoreCase = true) ||
                    message.contains("host is down", ignoreCase = true) ||
                    message.contains("no route to host", ignoreCase = true) ||
                    message.contains("name resolution", ignoreCase = true) ||
                    message.contains("name or service not known", ignoreCase = true) ||
                    message.contains("no such host is known", ignoreCase = true) ||
                    message.contains("nodename nor servname provided", ignoreCase = true) ||
                    message.contains("failed to connect", ignoreCase = true) ||
                    message.contains("unavailable", ignoreCase = true) ||
                    message.contains("broken pipe", ignoreCase = true) ||
                    message.contains("timeout", ignoreCase = true) ||
                    message.contains("timed out", ignoreCase = true)
            }
        return !isTransientBriarState
    }
}

/**
 * Starts/stops multiple PeopleSync delegates and merges their state: errors are merged,
 * and the first non-null current user id from the delegates is emitted.
 */
class CompositePeopleSync(
    private val delegates: List<PeopleSync>,
    private val transportMode: StateFlow<BriarTransportMode>,
    private val briarDelegateIndex: Int,
) : PeopleSync {
    init {
        require(briarDelegateIndex in delegates.indices) {
            "briarDelegateIndex must point to an existing delegate"
        }
    }

    override val syncPeopleErrors: Flow<Throwable> = kotlinx.coroutines.flow.merge(
        *delegates.map { it.syncPeopleErrors }.toTypedArray()
    )

    override val currentUserCanonicalId: Flow<String?> = combine(
        transportMode,
        combine(delegates.map { it.currentUserCanonicalId }) { ids -> ids.toList() },
    ) { mode, ids ->
        if (mode == BriarTransportMode.BRIAR_ONLY) {
            ids.getOrNull(briarDelegateIndex)
        } else {
            ids.firstOrNull { it != null }
        }
    }

    override fun ensureStarted() {
        delegates.forEach { it.ensureStarted() }
    }

    override fun stop() {
        delegates.forEach { it.stop() }
    }
}

internal suspend fun processSnapshot(
    entries: List<FirestoreSnapshotEntry>,
    currentUserId: String?,
    identityRegistry: IdentityRegistry,
) {
    val remoteIds = entries.mapTo(mutableSetOf()) { it.canonicalId }
    val existingRecords = identityRegistry.identitiesSnapshot()
    val existingIds = existingRecords
        .filter { record ->
            record.canonicalIdentity.id != currentUserId &&
                record.aliases.containsKey(TransportId.FIRESTORE)
        }
        .mapTo(mutableSetOf()) { it.canonicalIdentity.id }
    val presenceByCanonicalId = existingRecords
        .associate { it.canonicalIdentity.id to it.profile.presence }
        .toMutableMap()
    val (currentEntries, otherEntries) = if (currentUserId == null) {
        emptyList<FirestoreSnapshotEntry>() to entries
    } else {
        entries.partition { it.canonicalId == currentUserId }
    }

    suspend fun upsert(entry: FirestoreSnapshotEntry, setAsCurrent: Boolean) {
        val mergedPresence = preserveKnownPresence(
            incomingPresence = entry.presence,
            existingPresence = presenceByCanonicalId[entry.canonicalId],
        )
        identityRegistry.upsertIdentity(
            identity = CanonicalIdentity(
                id = entry.canonicalId,
                displayName = entry.user.name,
            ),
            aliases = mapOf(TransportId.FIRESTORE to entry.canonicalId),
            profile = IdentityProfile(
                bio = entry.user.bio,
                profilePicturePath = entry.user.profilePicturePath,
                presence = mergedPresence,
            ),
            setAsCurrent = setAsCurrent,
        )
        presenceByCanonicalId[entry.canonicalId] = mergedPresence
    }

    currentEntries.forEach { entry -> upsert(entry, setAsCurrent = true) }
    otherEntries.forEach { entry -> upsert(entry, setAsCurrent = false) }

    removeMissingContacts(
        identityRegistry = identityRegistry,
        remoteIds = remoteIds,
        currentUserId = currentUserId,
        knownIdsBeforeSnapshot = existingIds,
        transportId = TransportId.FIRESTORE,
    )
}

internal suspend fun processBriarSnapshot(
    contacts: List<ConnectorContact>,
    currentUserId: String?,
    identityRegistry: IdentityRegistry,
) {
    val remoteIds = contacts.mapTo(mutableSetOf()) { it.canonicalId }
    val existingRecords = identityRegistry.identitiesSnapshot()
    val existingIds = existingRecords
        .filter { record ->
            record.canonicalIdentity.id != currentUserId &&
                record.aliases.containsKey(TransportId.BRIAR)
        }
        .mapTo(mutableSetOf()) { it.canonicalIdentity.id }
    val presenceByCanonicalId = existingRecords
        .associate { it.canonicalIdentity.id to it.profile.presence }
        .toMutableMap()

    contacts.forEach { contact ->
        val mergedPresence = preserveKnownPresence(
            incomingPresence = contact.presence,
            existingPresence = presenceByCanonicalId[contact.canonicalId],
        )
        val identity = CanonicalIdentity(
            id = contact.canonicalId,
            displayName = contact.displayName,
        )
        identityRegistry.upsertIdentity(
            identity = identity,
            aliases = mapOf(TransportId.BRIAR to contact.transportId),
            profile = IdentityProfile(
                bio = contact.bio,
                profilePicturePath = contact.profilePicturePath,
                presence = mergedPresence,
            ),
            setAsCurrent = contact.canonicalId == currentUserId,
        )
        presenceByCanonicalId[contact.canonicalId] = mergedPresence
    }

    removeMissingContacts(
        identityRegistry = identityRegistry,
        remoteIds = remoteIds,
        currentUserId = currentUserId,
        knownIdsBeforeSnapshot = existingIds,
        transportId = TransportId.BRIAR,
    )
}

internal suspend fun removeMissingContacts(
    identityRegistry: IdentityRegistry,
    remoteIds: Set<String>,
    currentUserId: String?,
    knownIdsBeforeSnapshot: Set<String>,
    transportId: TransportId = TransportId.FIRESTORE,
) {
    identityRegistry.identitiesSnapshot()
        .filter { record ->
            val canonicalId = record.canonicalIdentity.id
            if (canonicalId == currentUserId) return@filter false
            if (canonicalId !in knownIdsBeforeSnapshot) return@filter false
            record.aliases.containsKey(transportId) && canonicalId !in remoteIds
        }
        .forEach { record ->
            identityRegistry.removeIdentity(record.canonicalIdentity.id)
        }
}

private fun ConnectorContact.toUser(): User {
    return User(
        name = displayName,
        bio = bio.orEmpty(),
        profilePicturePath = profilePicturePath,
        registrationTokens = registrationTokens.toMutableList(),
    )
}

private fun preserveKnownPresence(
    incomingPresence: PresenceStatus,
    existingPresence: PresenceStatus?,
): PresenceStatus {
    return if (incomingPresence != PresenceStatus.UNKNOWN) {
        incomingPresence
    } else {
        existingPresence ?: PresenceStatus.UNKNOWN
    }
}
