package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stage 2 stub for the fan-out orchestrator that will mirror messages between transports.
 * The implementation simply records when a connector notifies the router about inbound
 * messages so future stages can hook bridging logic in one place.
 */
interface BridgeOrchestrator {

    val routingState: StateFlow<PrimaryRoutingSnapshot>

    /**
     * Notifies that [messages] arrived via [source] for [conversationId]. Stage 2 keeps this
     * as a no-op so the transport router can stabilise before mirroring logic lands.
     */
    suspend fun onMessagesReceived(
        conversationId: String,
        source: TransportId,
        messages: List<ConnectorInboundMessage>,
    )

    suspend fun onConnectorLifecycleChanged(
        transport: TransportId,
        state: ConnectorLifecycleState,
    ) = Unit

    suspend fun onPrimaryFallback(
        fromTransport: TransportId,
        toTransport: TransportId,
        reason: ConnectorLifecycleState,
    ) = Unit

    object NoOp : BridgeOrchestrator {
        private val snapshot = MutableStateFlow(
            PrimaryRoutingSnapshot(
                mode = BriarTransportMode.FIRESTORE,
                primary = TransportId.FIRESTORE,
                preferred = TransportId.FIRESTORE,
                fallbackTarget = null,
                reason = PrimaryRoutingReason.Initial,
                preferredLifecycle = ConnectorLifecycleState.READY,
                trigger = PrimarySelectionTrigger.INITIAL,
                timestampMs = 0L,
            )
        )

        override val routingState: StateFlow<PrimaryRoutingSnapshot> = snapshot

        override suspend fun onMessagesReceived(
            conversationId: String,
            source: TransportId,
            messages: List<ConnectorInboundMessage>
        ) = Unit

        override suspend fun onConnectorLifecycleChanged(
            transport: TransportId,
            state: ConnectorLifecycleState,
        ) = Unit

        override suspend fun onPrimaryFallback(
            fromTransport: TransportId,
            toTransport: TransportId,
            reason: ConnectorLifecycleState,
        ) = Unit
    }
}
