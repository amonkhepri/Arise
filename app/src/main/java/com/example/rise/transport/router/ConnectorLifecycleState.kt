package com.example.rise.transport.router

/**
 * Detailed lifecycle states shared by all connectors. These map directly to the
 * documentation in docs/connector_lifecycle.md and allow the router to reason about
 * readiness, retries, and fallbacks.
 */
enum class ConnectorLifecycleState {
    INITIAL,
    AUTHENTICATING,
    HANDSHAKING,
    READY,
    DEGRADED,
    FAILED,
    RETIRING,
    STOPPED,
}
