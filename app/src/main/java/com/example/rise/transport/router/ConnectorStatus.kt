package com.example.rise.transport.router

/**
 * Lightweight connector lifecycle states surfaced to the transport router. Stage 2 only
 * distinguishes between inactive/active/error to unblock basic routing and health logging.
 */
enum class ConnectorStatus {
    INACTIVE,
    STARTING,
    ACTIVE,
    ERROR,
}
