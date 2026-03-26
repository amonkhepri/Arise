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

    /**
     * Creates (or loads an existing) canonical conversation between the current identity and the
     * specified peer. Implementations should provision transport-layer conversations when
     * necessary, persist the canonical record, and trigger background observation so message
     * history will start flowing into storage.
     */
    suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation

    /**
     * Produces a continuous stream of canonical messages for the given conversation. Unlike
     * [ensureConversation], which bootstraps storage and observation, this flow is read-only and
     * should emit the evolving timeline (including historical items) so UIs can render updates in
     * realtime.
     */
    fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>>

    /** Dispatch an outbound message; router handles primary/mirror paths per mode. */
    suspend fun sendMessage(message: ConnectorOutboundMessage)

    /** Clears cached identities, conversations, and observation jobs (used on sign-out). */
    suspend fun reset()
}
