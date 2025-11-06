package com.example.rise.transport.router

import java.util.Date

/**
 * Outbound message request dispatched to connectors. Stage 2 focuses on plain text payloads;
 * richer media routing will extend this contract later.
 */
data class ConnectorOutboundMessage(
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val recipientIds: Set<String>,
    val body: String,
    val timestamp: Date,
)

/**
 * Message emitted by a connector. The router converts it into a canonical record before
 * persisting to the conversation store.
 */
data class ConnectorInboundMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
    val body: String,
    val transport: TransportId,
    val timestamp: Date,
)

/**
 * Contact snapshot supplied by connectors so the identity registry can map aliases to the
 * canonical Briar identity.
 */
data class ConnectorContact(
    val transport: TransportId,
    val transportId: String,
    val displayName: String,
    val canonicalId: String,
    val bio: String? = null,
    val profilePicturePath: String? = null,
    val presence: PresenceStatus = PresenceStatus.UNKNOWN,
    val registrationTokens: List<String> = emptyList(),
)
