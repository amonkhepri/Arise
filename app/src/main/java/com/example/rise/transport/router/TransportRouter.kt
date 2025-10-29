package com.example.rise.transport.router

import kotlinx.coroutines.flow.Flow

/**
 * High-level entry point for chat features. The router owns canonical timeline persistence and
 * dispatches send/receive operations across the registered connectors.
 */
interface TransportRouter {

    /**
     * Emits the canonical representation of the signed-in user (the same identity Chat UI treats
     * as “me”), allowing the app to keep sender labels and permissions consistent while transports
     * switch under the hood.
     */
    val currentIdentity: Flow<CanonicalIdentity>

    /** Ensures the current canonical identity is loaded and returns it. */
    suspend fun ensureCurrentIdentity(): CanonicalIdentity

    /** Ensures the canonical conversation exists and returns its descriptor. */
    suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation

    /** Observe the canonical timeline for the given conversation. */
    fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>>

    /** Dispatch an outbound message; router handles primary/mirror paths per mode. */
    suspend fun send(message: ConnectorOutboundMessage)
}
