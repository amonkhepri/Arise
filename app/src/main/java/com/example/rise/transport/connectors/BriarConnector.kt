package com.example.rise.transport.connectors

import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.briar.BriarChatAdapter
import com.example.rise.transport.briar.BriarContactAdapter
import com.example.rise.transport.router.CapabilityDescriptor
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorCapabilities
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.transport.router.ConnectorTelemetryEvent
import com.example.rise.transport.router.ConnectorTelemetrySink
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Lightweight Briar connector stub that exposes runtime availability through the shared lifecycle
 * contract. Messaging/presence plumbing will land in later stages; until then the connector stays
 * `DEGRADED` so the router prefers Firestore while still surfacing lifecycle telemetry.
 */
class BriarConnector(
    private val transportBridge: TransportRuntimeBridge,
    private val telemetrySink: ConnectorTelemetrySink,
    private val briarChatAdapter: BriarChatAdapter,
    private val briarContactAdapter: BriarContactAdapter,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TransportConnector {

    override val transport: TransportId = TransportId.BRIAR
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _status = MutableStateFlow(ConnectorStatus.INACTIVE)
    private val _lifecycle = MutableStateFlow(ConnectorLifecycleState.INITIAL)
    private val _capabilities = MutableStateFlow(
        ConnectorCapabilities(
            mapOf(
                "messages" to CapabilityDescriptor(
                    version = 0,
                    properties = mapOf("enabled" to "false"),
                ),
                "contacts" to CapabilityDescriptor(
                    version = 0,
                    properties = mapOf("presence" to PresenceStatus.UNKNOWN.name),
                ),
                "account" to CapabilityDescriptor(version = 0, properties = emptyMap()),
            )
        )
    )
    override val status: StateFlow<ConnectorStatus> = _status.asStateFlow()
    override val lifecycle: StateFlow<ConnectorLifecycleState> = _lifecycle.asStateFlow()
    override val capabilities: StateFlow<ConnectorCapabilities> = _capabilities.asStateFlow()

    private val latestMode = AtomicReference(transportBridge.currentMode.value)
    private val latestRuntimeStatus = AtomicReference(transportBridge.runtimeStatus.value)

    init {
        scope.launch {
            transportBridge.currentMode.collect { mode ->
                latestMode.set(mode)
                recomputeLifecycle()
            }
        }
        scope.launch {
            transportBridge.runtimeStatus.collect { status ->
                latestRuntimeStatus.set(status)
                recomputeLifecycle()
            }
        }
        recomputeLifecycle()
    }

    override suspend fun currentIdentity(): CanonicalIdentity = briarChatAdapter.currentIdentity()

    override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId =
        briarChatAdapter.ensureConversation(conversation)

    override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> =
        briarChatAdapter.observeMessages(conversationId)

    override suspend fun sendMessage(message: ConnectorOutboundMessage) =
        briarChatAdapter.sendMessage(message)

    override fun observeContacts(): Flow<List<ConnectorContact>> = briarContactAdapter.observeContacts()

    private fun recomputeLifecycle() {
        val mode = latestMode.get()
        val runtimeStatus: BriarRuntimeStatus = latestRuntimeStatus.get()
        val (lifecycleState, connectorStatus) = when (mode) {
            BriarTransportMode.FIRESTORE -> ConnectorLifecycleState.STOPPED to ConnectorStatus.INACTIVE
            BriarTransportMode.HYBRID,
            BriarTransportMode.BRIAR_ONLY -> mapRuntimePhase(runtimeStatus.phase)
        }
        if (_lifecycle.value != lifecycleState) {
            Timber.tag(TAG).d(
                "Briar lifecycle -> %s (mode=%s, runtime=%s)",
                lifecycleState,
                mode,
                runtimeStatus.phase,
            )
        }
        transition(lifecycleState, connectorStatus, runtimeStatus)
    }

    private fun mapRuntimePhase(phase: BriarRuntimePhase): Pair<ConnectorLifecycleState, ConnectorStatus> {
        return when (phase) {
            BriarRuntimePhase.STARTING ->
                ConnectorLifecycleState.AUTHENTICATING to ConnectorStatus.STARTING

            BriarRuntimePhase.RUNNING ->
                ConnectorLifecycleState.DEGRADED to ConnectorStatus.ACTIVE

            BriarRuntimePhase.STOPPING ->
                ConnectorLifecycleState.RETIRING to ConnectorStatus.STARTING

            BriarRuntimePhase.STOPPED ->
                ConnectorLifecycleState.AUTHENTICATING to ConnectorStatus.STARTING

            BriarRuntimePhase.FAILED ->
                ConnectorLifecycleState.FAILED to ConnectorStatus.ERROR
        }
    }

    private fun transition(
        lifecycleState: ConnectorLifecycleState,
        connectorStatus: ConnectorStatus,
        runtimeStatus: BriarRuntimeStatus,
    ) {
        _lifecycle.value = lifecycleState
        _status.value = connectorStatus
        if (lifecycleState == ConnectorLifecycleState.FAILED) {
            telemetrySink.emit(
                ConnectorTelemetryEvent.Failure(
                    transport = transport,
                    state = lifecycleState,
                    error = runtimeStatus.lastError,
                )
            )
        }
    }

    companion object {
        private const val TAG = "BriarConnector"
    }
}
