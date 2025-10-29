package com.example.rise.transport.router

/**
 * Stage 2 stub for the fan-out orchestrator that will mirror messages between transports.
 * The implementation simply records when a connector notifies the router about inbound
 * messages so future stages can hook bridging logic in one place.
 */
interface BridgeOrchestrator {

    /**
     * Notifies that [messages] arrived via [source] for [conversationId]. Stage 2 keeps this
     * as a no-op so the transport router can stabilise before mirroring logic lands.
     */
    suspend fun onMessagesReceived(
        conversationId: String,
        source: TransportId,
        messages: List<ConnectorInboundMessage>,
    )

    object NoOp : BridgeOrchestrator {
        override suspend fun onMessagesReceived(
            conversationId: String,
            source: TransportId,
            messages: List<ConnectorInboundMessage>
        ) = Unit
    }
}
