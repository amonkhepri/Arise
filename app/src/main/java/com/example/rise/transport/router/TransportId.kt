package com.example.rise.transport.router

/**
 * Identifiers for the supported transports. Stage 2 supports Firestore as the primary path
 * and introduces Briar as the canonical backbone, with Telegram reserved for later stages.
 */
enum class TransportId {
    BRIAR,
    FIRESTORE,
    TELEGRAM,
}
