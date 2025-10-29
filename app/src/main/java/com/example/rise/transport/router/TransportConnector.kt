package com.example.rise.transport.router

import kotlinx.coroutines.flow.Flow

/**
 * Contract implemented by each transport. Stage 2 primarily exercises the Firestore connector,
 * while the Briar connector exposes availability through [status] and stubs message flows
 * until hybrid delivery is wired up.
 */
interface TransportConnector {
    val transport: TransportId
    val status: Flow<ConnectorStatus>

    /**
     * Returns the canonical identity representing the signed-in user for this connector.
     * Connectors with no notion of identity (e.g., monitoring-only) may throw until the
     * onboarding flow completes.
     */
    suspend fun currentIdentity(): CanonicalIdentity

    /**
     * Ensures a conversation exists for the supplied participants, returning its canonical
     * identifier. Connectors should create transport-specific resources as needed and
     * return the canonical ID agreed upon by the identity registry.
     */
    suspend fun ensureConversation(conversation: CanonicalConversation): String

    /**
     * Observe messages for the canonical conversation. Connectors emit transport-native
     * messages which the router will persist to the shared store.
     */
    fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>>

    /**
     * Sends a message through the connector. Connectors may return immediately (fire-and-forget)
     * or suspend until the underlying transport acknowledges the request.
     */
    suspend fun sendMessage(message: ConnectorOutboundMessage)

    /**
     * Optional contact sync used by the identity registry to map connector IDs to canonical IDs.
     */
    suspend fun syncContacts(): List<ConnectorContact> = emptyList()
}
