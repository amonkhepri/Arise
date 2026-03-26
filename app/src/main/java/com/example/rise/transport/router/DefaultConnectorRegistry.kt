package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode

/**
 * Simple connector registry that selects Firestore as the primary transport for Stage 2 while
 * exposing the Briar connector when hybrid routing is enabled.
 */
class DefaultConnectorRegistry(
    override val connectors: Set<TransportConnector>,
) : ConnectorRegistry {

    override fun connectorFor(transportId: TransportId): TransportConnector? {
        return connectors.firstOrNull { it.transport == transportId }
    }

    override fun primaryFor(mode: BriarTransportMode): TransportConnector {
        return when (mode) {
            BriarTransportMode.FIRESTORE -> connectorFor(TransportId.FIRESTORE)
                ?: connectorFor(TransportId.BRIAR)
            BriarTransportMode.HYBRID,
            BriarTransportMode.BRIAR_ONLY -> connectorFor(TransportId.BRIAR)
                ?: connectorFor(TransportId.FIRESTORE)
        } ?: error("Primary connector missing for mode $mode")
    }

    override fun mirrorsFor(mode: BriarTransportMode): List<TransportConnector> {
        val primary = primaryFor(mode)
        return when (mode) {
            BriarTransportMode.FIRESTORE -> emptyList()
            BriarTransportMode.HYBRID -> {
                connectors
                    .filter { it.transport == TransportId.FIRESTORE && it != primary }
            }
            BriarTransportMode.BRIAR_ONLY -> emptyList()
        }
    }
}
