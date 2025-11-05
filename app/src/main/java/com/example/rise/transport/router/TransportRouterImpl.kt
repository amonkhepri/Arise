package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.store.ConversationStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransportRouterImpl(
    private val transportBridge: TransportRuntimeBridge,
    private val connectorRegistry: ConnectorRegistry,
    private val conversationStore: ConversationStore,
    private val identityRegistry: IdentityRegistry,
    private val bridgeOrchestrator: BridgeOrchestrator = BridgeOrchestrator.NoOp,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransportRouter {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val observationJobs = ConcurrentHashMap<String, Job>()
    private val observationLocks = ConcurrentHashMap<String, ReentrantLock>()

    override val currentIdentity: Flow<CanonicalIdentity> = identityRegistry.currentIdentity

    override suspend fun ensureCurrentIdentity(): CanonicalIdentity {
        val mode = transportBridge.currentMode.value
        return resolveCurrentIdentity(mode)
    }

    override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
        val mode = transportBridge.currentMode.value
        val selfIdentity = resolveCurrentIdentity(mode)
        val primary = connectorRegistry.primaryFor(mode)
        identityRegistry.upsertIdentity(
            otherIdentity,
            aliases = mapOf(primary.transport to otherIdentity.id)
        )

        val participants = setOf(selfIdentity.id, otherIdentity.id)
        val conversationId =
            ensureTransportConversation(primary, participants, otherIdentity.displayName)
        val conversation = CanonicalConversation(
            id = conversationId,
            participants = participants,
            title = otherIdentity.displayName,
        )
        conversationStore.upsertConversation(conversation)
        ensureObservation(conversation.id)
        return conversation
    }

    override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> {
        ensureObservation(conversationId)
        return conversationStore.observeMessages(conversationId)
    }

    override suspend fun send(message: ConnectorOutboundMessage) {
        val mode = transportBridge.currentMode.value
        val primaryConnector = connectorRegistry.primaryFor(mode)
        primaryConnector.sendMessage(message)
        connectorRegistry.mirrorsFor(mode).forEach { mirror ->
            scope.launch { mirror.sendMessage(message) }
        }
    }

    override suspend fun reset() {
        resetCachedIdentity()
    }

    private suspend fun resolveCurrentIdentity(mode: BriarTransportMode): CanonicalIdentity {
        val primaryConnector = connectorRegistry.primaryFor(mode)
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
    }

    private suspend fun ensureTransportConversation(
        primary: TransportConnector,
        participants: Set<String>,
        title: String,
    ): String {
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

            val lazyObservationJob = newObservationJob(conversationId)
            observationJobs[conversationId] = lazyObservationJob
            lazyObservationJob.invokeOnCompletion {
                observationJobs.remove(conversationId, lazyObservationJob)
                observationLocks.remove(conversationId, conversationLock)
            }
            lazyObservationJob.start()
        } finally {
            if (conversationLock.isHeldByCurrentThread) {
                conversationLock.unlock()
            }
        }
    }

    private fun newObservationJob(conversationId: String): Job =
        scope.launch(start = CoroutineStart.LAZY) {
            connectorRegistry.connectors.forEach { connector ->
                launch {
                    connector.observeMessages(conversationId).collectLatest { messages ->
                        val canonicalMessages = messages.map { it.toCanonical() }
                        conversationStore.upsertMessages(conversationId, canonicalMessages)
                        if (canonicalMessages.isNotEmpty()) {
                            bridgeOrchestrator.onMessagesReceived(
                                conversationId = conversationId,
                                source = connector.transport,
                                messages = messages,
                            )
                        }
                    }
                }
            }
        }

    private fun ConnectorInboundMessage.toCanonical(): CanonicalMessage {
        val canonicalId = "${transport.name}:${messageId}"
        return CanonicalMessage(
            canonicalMessageId = canonicalId,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            body = body,
            transport = transport,
            timestamp = timestamp,
        )
    }
}
