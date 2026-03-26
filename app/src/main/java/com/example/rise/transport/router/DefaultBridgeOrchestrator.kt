package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class DefaultBridgeOrchestrator(
    private val transportBridge: TransportRuntimeBridge,
    private val connectorRegistry: ConnectorRegistry,
    private val telemetrySink: ConnectorTelemetrySink,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    clock: () -> Long = { System.currentTimeMillis() },
    externalScope: CoroutineScope? = null,
) : BridgeOrchestrator {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + dispatcher)
    private val lifecycleStates = ConcurrentHashMap<TransportId, ConnectorLifecycleState>()
    private val now = clock

    private val _routingState = MutableStateFlow(
        PrimaryRoutingSnapshot(
            mode = transportBridge.currentMode.value,
            primary = preferredTransport(transportBridge.currentMode.value),
            preferred = preferredTransport(transportBridge.currentMode.value),
            fallbackTarget = null,
            reason = PrimaryRoutingReason.Initial,
            preferredLifecycle = null,
            trigger = PrimarySelectionTrigger.INITIAL,
            timestampMs = clock(),
        )
    )
    override val routingState: StateFlow<PrimaryRoutingSnapshot> = _routingState.asStateFlow()

    init {
        connectorRegistry.connectors.forEach { connector ->
            lifecycleStates[connector.transport] = connector.lifecycle.value
        }
        recomputeSelection(transportBridge.currentMode.value, PrimarySelectionTrigger.INITIAL)
        scope.launch {
            transportBridge.currentMode.collect { mode ->
                recomputeSelection(mode, PrimarySelectionTrigger.MODE_CHANGED)
            }
        }
    }

    override suspend fun onMessagesReceived(
        conversationId: String,
        source: TransportId,
        messages: List<ConnectorInboundMessage>,
    ) {
        telemetrySink.emit(
            ConnectorTelemetryEvent.MessagesObserved(
                transport = source,
                conversationId = conversationId,
                count = messages.size,
            )
        )
    }

    override suspend fun onConnectorLifecycleChanged(
        transport: TransportId,
        state: ConnectorLifecycleState,
    ) {
        lifecycleStates[transport] = state
        recomputeSelection(transportBridge.currentMode.value, PrimarySelectionTrigger.LIFECYCLE_CHANGED)
    }

    override suspend fun onPrimaryFallback(
        fromTransport: TransportId,
        toTransport: TransportId,
        reason: ConnectorLifecycleState,
    ) {
        Timber.tag(TAG).w(
            "Primary fallback from %s to %s (reason=%s)",
            fromTransport,
            toTransport,
            reason,
        )
        telemetrySink.emit(
            ConnectorTelemetryEvent.PrimaryFallback(
                fromTransport = fromTransport,
                toTransport = toTransport,
                reason = reason,
            )
        )
        val mode = transportBridge.currentMode.value
        _routingState.value = PrimaryRoutingSnapshot(
            mode = mode,
            primary = toTransport,
            preferred = preferredTransport(mode),
            fallbackTarget = fromTransport,
            reason = PrimaryRoutingReason.ExplicitFallback,
            preferredLifecycle = lifecycleStates[preferredTransport(mode)],
            trigger = PrimarySelectionTrigger.EXPLICIT_FALLBACK,
            timestampMs = now(),
        )
    }

    private fun recomputeSelection(
        mode: BriarTransportMode,
        trigger: PrimarySelectionTrigger,
    ) {
        val preferred = preferredTransport(mode)
        val preferredConnector = connectorRegistry.connectorFor(preferred)
        val preferredLifecycle = lifecycleStates[preferred]
            ?: preferredConnector?.lifecycle?.value

        val candidateOrder = candidateOrder(mode)
        val readyConnector = candidateOrder
            .mapNotNull { connectorRegistry.connectorFor(it) }
            .firstOrNull { lifecycleStates[it.transport] == ConnectorLifecycleState.READY }
        val fallbackConnector = when {
            mode == BriarTransportMode.FIRESTORE && preferredConnector != null -> preferredConnector
            else -> readyConnector
                ?: candidateOrder.firstNotNullOfOrNull { connectorRegistry.connectorFor(it) }
                ?: connectorRegistry.connectors.firstOrNull()
        }

        val reason = when {
            preferredConnector == null -> PrimaryRoutingReason.PreferredMissing
            mode == BriarTransportMode.FIRESTORE -> PrimaryRoutingReason.FlagForcesFirestore
            preferredLifecycle != ConnectorLifecycleState.READY ->
                PrimaryRoutingReason.PreferredNotReady
            readyConnector != null -> PrimaryRoutingReason.PreferredReady
            else -> PrimaryRoutingReason.PreferredMissing
        }

        val primaryTransport = fallbackConnector?.transport ?: preferred
        val fallbackTarget = if (primaryTransport == preferred) null else preferred
        val snapshot = PrimaryRoutingSnapshot(
            mode = mode,
            primary = primaryTransport,
            preferred = preferred,
            fallbackTarget = fallbackTarget,
            reason = reason,
            preferredLifecycle = preferredLifecycle,
            trigger = trigger,
            timestampMs = now(),
        )
        _routingState.value = snapshot
        Timber.tag(TAG).d(
            "Routing updated: mode=%s primary=%s preferred=%s fallback=%s reason=%s lifecycle=%s trigger=%s",
            mode,
            snapshot.primary,
            snapshot.preferred,
            snapshot.fallbackTarget,
            snapshot.reason,
            snapshot.preferredLifecycle,
            trigger,
        )
    }

    companion object {
        private const val TAG = "BridgeOrchestrator"

        private fun preferredTransport(mode: BriarTransportMode): TransportId =
            when (mode) {
                BriarTransportMode.FIRESTORE -> TransportId.FIRESTORE
                BriarTransportMode.HYBRID,
                BriarTransportMode.BRIAR_ONLY -> TransportId.BRIAR
            }

        private fun candidateOrder(mode: BriarTransportMode): List<TransportId> {
            val base = when (mode) {
                BriarTransportMode.FIRESTORE -> listOf(TransportId.FIRESTORE, TransportId.BRIAR)
                BriarTransportMode.HYBRID -> listOf(TransportId.BRIAR, TransportId.FIRESTORE)
                BriarTransportMode.BRIAR_ONLY -> listOf(TransportId.BRIAR)
            }
            val extras = if (mode == BriarTransportMode.BRIAR_ONLY) {
                emptyList()
            } else {
                TransportId.entries.filter { it !in base }
            }
            return base + extras
        }
    }
}
