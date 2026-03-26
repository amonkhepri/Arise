package com.example.rise.transport.router

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Contract implemented by each transport. Stage 2 primarily exercises the Firestore connector,
 * while the Briar connector exposes availability through [status] and stubs message flows
 * until hybrid delivery is wired up.
 */
interface TransportConnector {
    val transport: TransportId
    val status: StateFlow<ConnectorStatus>
    val lifecycle: StateFlow<ConnectorLifecycleState>
    val capabilities: StateFlow<ConnectorCapabilities>

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
    suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId

    /**
     * Observe messages for the canonical conversation. Connectors emit transport-native
     * messages which the router will persist to the shared store.
     */
    fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>>

    /**
     * Roster observer used by people sync flows to stay aligned with connector-backed contacts.
     * Connectors that do not surface a roster can rely on the default empty flow.
     */
    fun observeContacts(): Flow<List<ConnectorContact>> = emptyFlow()

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
