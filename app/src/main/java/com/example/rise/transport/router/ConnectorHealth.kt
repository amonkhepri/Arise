package com.example.rise.transport.router

/**
 * Snapshot describing the observable health of a connector. ViewModels use this to surface
 * lifecycle information in developer/debug UIs, while telemetry relies on the same data to log
 * transitions.
 */
data class ConnectorHealth(
    val transport: TransportId,
    val lifecycle: ConnectorLifecycleState,
    val status: ConnectorStatus,
    val capabilities: ConnectorCapabilities,
    val messagingReady: Boolean,
)
