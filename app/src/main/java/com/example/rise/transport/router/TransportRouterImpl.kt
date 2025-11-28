package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.store.ConversationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class TransportRouterImpl(
    private val transportBridge: TransportRuntimeBridge,
    private val connectorRegistry: ConnectorRegistry,
    private val conversationStore: ConversationStore,
    private val identityRegistry: IdentityRegistry,
    private val bridgeOrchestrator: BridgeOrchestrator = BridgeOrchestrator.NoOp,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransportRouter {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher.canonicalize())
    private val observationJobs = ConcurrentHashMap<String, Job>()
    private val observationLocks = ConcurrentHashMap<String, ReentrantLock>()
    private val aliasLocks = ConcurrentHashMap<Pair<String, TransportId>, Mutex>()
    private val messageSnapshots = ConcurrentHashMap<String, MutableMap<TransportId, MutableMap<String, CanonicalMessage>>>()
    private val messageSnapshotLocks = ConcurrentHashMap<String, Mutex>()
    @Volatile
    private var lastFallbackSnapshot: Pair<TransportId, ConnectorLifecycleState>? = null

    init {
        connectorRegistry.connectors.forEach { connector ->
            observeConnectorLifecycle(connector)
        }
}

    override val currentIdentity: Flow<CanonicalIdentity> = identityRegistry.currentIdentity

    override suspend fun ensureCurrentIdentity(): CanonicalIdentity {
        val transportMode = transportBridge.currentMode.value
        return resolveCurrentIdentity(transportMode)
    }

    override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
        val transportMode = transportBridge.currentMode.value
        val selfIdentity = resolveCurrentIdentity(transportMode)
        val participants = setOf(selfIdentity.id, otherIdentity.id)
        val (chosenConnector, ensuredConversation) = executeWithFallback(
            mode = transportMode,
            onFallback = bridgeOrchestrator::onPrimaryFallback,
        ) { connector ->
            connector to ensureTransportConversation(connector, participants, otherIdentity.displayName)
        }
        val canonicalConversationId = ensuredConversation.canonicalId
        val existingConversation = conversationStore.getConversation(canonicalConversationId)
        val primaryAlias = ensuredConversation.transportConversationId
        identityRegistry.upsertIdentity(
            otherIdentity,
            aliases = mapOf(chosenConnector.transport to otherIdentity.id)
        )
        val conversation = CanonicalConversation(
            id = canonicalConversationId,
            participants = participants,
            title = otherIdentity.displayName,
            primaryTransportId = chosenConnector.transport,
            briarConversationId = primaryAlias.takeIf { chosenConnector.transport == TransportId.BRIAR },
        )
        conversationStore.upsertConversation(conversation)
        conversationStore.upsertAlias(canonicalConversationId, chosenConnector.transport, primaryAlias)
        if (existingConversation == null) {
            conversationStore.upsertMessages(canonicalConversationId, emptyList())
        }
        ensureObservation(conversation.id)
        return conversation
    }

    override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> {
        ensureObservation(conversationId)
        return conversationStore.observeMessages(conversationId)
    }

    override suspend fun sendMessage(message: ConnectorOutboundMessage) {
        val transportMode = transportBridge.currentMode.value
        val canonicalConversationId = message.conversationId
        executeWithFallback(
            mode = transportMode,
            onFallback = bridgeOrchestrator::onPrimaryFallback,
        ) { primaryConnector ->
            val primaryAlias = resolveTransportAlias(canonicalConversationId, primaryConnector)
            primaryConnector.sendMessage(message.copy(conversationId = primaryAlias))
            connectorRegistry
                .mirrorsFor(transportMode)
                .filter { mirror -> mirror.transport != primaryConnector.transport }
                .filter { mirror -> mirror.lifecycle.value.isOperational() && mirror.isMessagingReady() }
                .forEach { mirror ->
                    scope.launch {
                        runCatching {
                            val alias = resolveTransportAlias(canonicalConversationId, mirror)
                            mirror.sendMessage(message.copy(conversationId = alias))
                        }.onFailure { error ->
                            Timber.tag(TAG).w(
                                error,
                                "Failed to mirror send for %s",
                                mirror.transport,
                            )
                        }
                    }
                }
        }
    }

    override suspend fun reset() {
        resetCachedIdentity()
    }

    private suspend fun resolveCurrentIdentity(mode: BriarTransportMode): CanonicalIdentity {
        val primaryConnector = selectPrimaryConnector(mode)
        val cachedIdentity = identityRegistry.currentIdentitySnapshot()
        val connectorIdentityResult = runCatching { primaryConnector.currentIdentity() }
        val connectorIdentity = connectorIdentityResult.getOrNull()
        return when {
            connectorIdentity == null ->
                handleConnectorIdentityFailure(
                    connectorIdentityResult.exceptionOrNull(),
                    cachedIdentity
                )

            cachedIdentity == null ->
                storeAndReturn(primaryConnector, connectorIdentity)

            cachedIdentity.id != connectorIdentity.id -> {
                resetCachedIdentity()
                storeAndReturn(primaryConnector, connectorIdentity)
            }

            cachedIdentity.displayName != connectorIdentity.displayName -> {
                storeIdentity(primaryConnector, connectorIdentity)
                connectorIdentity
            }

            else -> cachedIdentity
        }
    }

    private suspend fun handleConnectorIdentityFailure(
        error: Throwable?,
        cached: CanonicalIdentity?,
    ): CanonicalIdentity {
        if (error is IllegalStateException && error.message == "User must be signed in") {
            resetCachedIdentity()
            throw error
        }
        cached?.let { return it }
        throw error ?: IllegalStateException("Unable to resolve connector identity")
    }

    private suspend fun storeAndReturn(
        primaryConnector: TransportConnector,
        identity: CanonicalIdentity,
    ): CanonicalIdentity {
        storeIdentity(primaryConnector, identity)
        return identity
    }

    private suspend fun storeIdentity(
        primaryConnector: TransportConnector,
        identity: CanonicalIdentity,
    ) {
        identityRegistry.upsertIdentity(
            identity = identity,
            aliases = mapOf(primaryConnector.transport to identity.id),
            setAsCurrent = true,
        )
    }

    private suspend fun resetCachedIdentity() {
        identityRegistry.clear()
        conversationStore.clearAll()
        observationJobs.values.forEach { it.cancel() }
        observationJobs.clear()
        aliasLocks.clear()
        messageSnapshots.clear()
        messageSnapshotLocks.clear()
    }

    private suspend fun ensureTransportConversation(
        primary: TransportConnector,
        participants: Set<String>,
        title: String,
    ): TransportConversationId {
        val provisionalConversation = CanonicalConversation(
            id = "",
            participants = participants,
            title = title,
        )
        return primary.ensureConversation(provisionalConversation)
    }

    private fun ensureObservation(conversationId: String) {
        val conversationLock = observationLocks.computeIfAbsent(conversationId) { ReentrantLock() }
        conversationLock.lock()
        try {
            val currentObservationJob = observationJobs[conversationId]
            if (currentObservationJob != null) {
                if (currentObservationJob.isActive) {
                    return
                }
                observationJobs.remove(conversationId, currentObservationJob)
                currentObservationJob.cancel()
            }

            val observationJob = newObservationJob(conversationId)
            observationJobs[conversationId] = observationJob
            observationJob.invokeOnCompletion {
                observationJobs.remove(conversationId, observationJob)
                observationLocks.remove(conversationId, conversationLock)
                scope.launch { clearMessageSnapshots(conversationId) }
            }
        } finally {
            if (conversationLock.isHeldByCurrentThread) {
                conversationLock.unlock()
            }
        }
    }

    private fun newObservationJob(conversationId: String): Job =
        scope.launch {
            connectorRegistry.connectors.forEach { connector ->
                launch {
                    val alias = try {
                        awaitConnectorOperational(connector)
                        resolveTransportAlias(conversationId, connector)
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        Timber.tag(TAG).w(
                            error,
                            "Skipping observation for %s; unable to resolve alias",
                            connector.transport,
                        )
                        return@launch
                    }
                    connector.observeMessages(alias).collectLatest { messages ->
                        val canonicalConnectorMessages = messages.map {
                            it.copy(conversationId = conversationId)
                        }
                        val canonicalMessages = canonicalConnectorMessages.map { it.toCanonical() }
                        persistMergedMessages(conversationId, connector.transport, canonicalMessages)
                        if (canonicalMessages.isNotEmpty()) {
                            bridgeOrchestrator.onMessagesReceived(
                                conversationId = conversationId,
                                source = connector.transport,
                                messages = canonicalConnectorMessages,
                            )
                        }
                    }
                }
            }
        }

    private suspend fun awaitConnectorOperational(connector: TransportConnector) {
        if (connector.lifecycle.value.isOperational()) {
            return
        }
        connector.lifecycle
            .filter { it.isOperational() }
            .first()
    }

    private suspend fun resolveTransportAlias(
        conversationId: String,
        connector: TransportConnector,
    ): String {
        conversationStore.getAlias(conversationId, connector.transport)?.let { return it }
        val lockKey = conversationId to connector.transport
        val lock = aliasLocks.getOrPut(lockKey) { Mutex() }
        return lock.withLock {
            try {
                conversationStore.getAlias(conversationId, connector.transport)?.let { return it }
                val canonicalConversation = conversationStore.getConversation(conversationId)
                    ?: error("Missing canonical conversation $conversationId")
                val ensured = connector.ensureConversation(canonicalConversation)
                val alias = ensured.transportConversationId
                conversationStore.upsertAlias(conversationId, connector.transport, alias)
                alias
            } finally {
                aliasLocks.remove(lockKey, lock)
            }
        }
    }

    private suspend fun persistMergedMessages(
        conversationId: String,
        transportId: TransportId,
        messages: List<CanonicalMessage>,
    ) {
        val lock = messageSnapshotLocks.computeIfAbsent(conversationId) { Mutex() }
        lock.withLock {
            val snapshots = messageSnapshots.getOrPut(conversationId) { mutableMapOf() }
            val transportSnapshots = snapshots.getOrPut(transportId) { mutableMapOf() }
            transportSnapshots.clear()
            messages.forEach { transportSnapshots[it.canonicalMessageId] = it }
            val merged = snapshots.values
                .flatMap { it.values }
                .sortedWith(
                    compareBy<CanonicalMessage> { it.timestamp }
                        .thenBy { it.canonicalMessageId }
                )
            conversationStore.upsertMessages(conversationId, merged)
            if (merged.isEmpty()) {
                messageSnapshots.remove(conversationId)
                messageSnapshotLocks.remove(conversationId, lock)
            }
        }
    }

    private suspend fun clearMessageSnapshots(conversationId: String) {
        val lock = messageSnapshotLocks[conversationId]
        if (lock == null) {
            messageSnapshots.remove(conversationId)
            return
        }
        lock.withLock {
            messageSnapshots.remove(conversationId)
            messageSnapshotLocks.remove(conversationId, lock)
        }
    }

    private fun observeConnectorLifecycle(connector: TransportConnector) {
        scope.launch {
            connector.lifecycle.collect { state ->
                bridgeOrchestrator.onConnectorLifecycleChanged(connector.transport, state)
            }
        }
    }

    private suspend fun selectPrimaryConnector(briarTransportMode: BriarTransportMode): TransportConnector {
        val preferredConnector = connectorRegistry.primaryFor(briarTransportMode)
        val stateOfPreferredConnector = preferredConnector.lifecycle.value
        if (stateOfPreferredConnector == ConnectorLifecycleState.READY && preferredConnector.isMessagingReady()) {
            lastFallbackSnapshot = null
            return preferredConnector
        }
        val priorityFallbackOrder = when (briarTransportMode) {
            BriarTransportMode.FIRESTORE -> listOf(TransportId.FIRESTORE, TransportId.FIRESTORE)
            BriarTransportMode.HYBRID -> listOf(TransportId.BRIAR, TransportId.FIRESTORE)
            BriarTransportMode.BRIAR_ONLY -> listOf(TransportId.BRIAR, TransportId.FIRESTORE)
        }
        val fallbackConnector = priorityFallbackOrder
            .mapNotNull { connectorRegistry.connectorFor(it) }
            .firstOrNull { it.lifecycle.value == ConnectorLifecycleState.READY && it.isMessagingReady() }
            ?: preferredConnector

        if (fallbackConnector != preferredConnector) {
            val snapshotConnector = preferredConnector.transport to stateOfPreferredConnector
            val cachedConnector = lastFallbackSnapshot
            if (cachedConnector != snapshotConnector) {
                lastFallbackSnapshot = snapshotConnector
                bridgeOrchestrator.onPrimaryFallback(
                    fromTransport = preferredConnector.transport,
                    toTransport = fallbackConnector.transport,
                    reason = stateOfPreferredConnector,
                )
            }
        } else {
            lastFallbackSnapshot = null
        }
        return fallbackConnector
    }

    private fun ConnectorInboundMessage.toCanonical(): CanonicalMessage {
        val canonicalMessageId = "${transport.name}:${messageId}"
        return CanonicalMessage(
            canonicalMessageId = canonicalMessageId,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            body = body,
            transport = transport,
            timestamp = timestamp,
        )
    }

    private fun ConnectorLifecycleState.isOperational(): Boolean {
        return this == ConnectorLifecycleState.READY || this == ConnectorLifecycleState.DEGRADED
    }

    /**
     * Normalizes the dispatcher to avoid Dispatchers.Unconfined for router coroutines.
     *
     * Unconfined dispatchers can resume on arbitrary threads, which makes thread-affine
     * primitives (like ReentrantLock) risky when re-entrant callbacks hop threads. Using
     * Default keeps work confined to a stable pool while still off the main thread.
     */
    private fun CoroutineDispatcher.canonicalize(): CoroutineDispatcher {
        return if (this == Dispatchers.Unconfined) Dispatchers.Default else this
    }

    /**
     * Returns true when the connector reports messaging capability enabled.
     *
     * Capability metadata is driven by connector state; we gate mirroring and sends on this
     * to avoid attempting message flows when the connector cannot handle them yet.
     */
    private fun TransportConnector.isMessagingReady(): Boolean {
        val messages = capabilities.value.entries["messages"] ?: return false
        return messages.properties["enabled"]?.toBooleanStrictOrNull() == true
    }

    /**
     * Runs a block against the primary connector, retrying with a ready fallback on failure.
     *
     * - Selects the primary for the current mode.
     * - If the block throws, finds the first operational, messaging-ready alternate connector
     *   and notifies the orchestrator via `onFallback`, then re-invokes the block with it.
     * - Propagates the original error when no suitable fallback exists.
     */
    private suspend fun <T> executeWithFallback(
        mode: BriarTransportMode,
        onFallback: suspend (TransportId, TransportId, ConnectorLifecycleState) -> Unit,
        block: suspend (TransportConnector) -> T,
    ): T {
        val primary = selectPrimaryConnector(mode)
        return runCatching { block(primary) }.getOrElse { error ->

            val allowedFallbackTransports = when (mode) {
                BriarTransportMode.FIRESTORE -> emptyList()
                BriarTransportMode.HYBRID -> listOf(TransportId.BRIAR, TransportId.FIRESTORE)
                BriarTransportMode.BRIAR_ONLY -> listOf(TransportId.BRIAR)
            }
            val fallback = connectorRegistry.connectors
                .filter { it.transport != primary.transport }
                .filter { allowedFallbackTransports.contains(it.transport) }
                .firstOrNull { it.lifecycle.value.isOperational() && it.isMessagingReady() }
                ?: throw error
            onFallback(primary.transport, fallback.transport, primary.lifecycle.value)
            block(fallback)
        }
    }

    companion object {
        private const val TAG = "TransportRouter"
    }
}

class BriarOnlyOperationException(cause: Throwable) : IllegalStateException(
    "Briar-only mode: operation failed and no fallback is permitted",
    cause
)
