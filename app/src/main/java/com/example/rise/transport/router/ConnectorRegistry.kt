package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode

/**
 * Keeps track of registered connectors and exposes convenience helpers so the router can pick
 * primary/mirror transports based on the active mode.
 */
interface ConnectorRegistry {
    val connectors: Set<TransportConnector>

    /**
     * Returns the registered connector for the provided [transportId], or `null` when that
     * transport is not currently active. Useful for diagnostics and niche flows that need a
     * specific transport outside the primary/mirror selection.
     */
    fun connectorFor(transportId: TransportId): TransportConnector?

    /**
     * Returns the connector that should be treated as the primary path for the given mode.
     * Stage 2 uses Firestore for FIRESTORE mode and Briar for hybrid/Briar modes when available.
     */
    fun primaryFor(mode: BriarTransportMode): TransportConnector

    /**
     * Returns optional mirror connectors for the supplied mode. Stage 2 mirrors nothing yet but
     * exposes the hook so Stage 4 can add Telegram bridging.
     */
    fun mirrorsFor(mode: BriarTransportMode): List<TransportConnector>
}
