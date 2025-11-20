package com.example.rise.briar.runtime

import java.time.Instant

/**
 * Canonical identity information exposed by the embedded Briar runtime.
 */
data class BriarIdentity(
    val id: String,
    val displayName: String,
    val bio: String? = null,
    val avatarPath: String? = null,
)

/**
 * Descriptor provided when ensuring a conversation. Mirrors the canonical conversation fields in
 * the transport router while keeping the runtime module decoupled from app-specific models.
 */
data class BriarConversationDescriptor(
    val canonicalConversationId: String,
    val participantIds: Set<String>,
    val title: String,
)

/**
 * Result returned by the runtime after ensuring/creating a conversation.
 */
data class BriarConversation(
    val canonicalId: String,
    val transportConversationId: String,
)

/**
 * Message emitted by the Briar runtime.
 */
data class BriarMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val recipientIds: Set<String>,
    val body: String,
    val timestamp: Instant,
)

/**
 * Outbound payload consumed by the Briar runtime when sending a message.
 */
data class BriarOutboundMessage(
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val recipientIds: Set<String>,
    val body: String,
    val timestamp: Instant,
)

/**
 * Presence information propagated from Briar contacts.
 */
enum class BriarPresenceStatus {
    UNKNOWN,
    ONLINE,
    OFFLINE,
}

/**
 * Contact snapshot emitted by the Briar runtime.
 */
data class BriarContact(
    val canonicalId: String,
    val transportAlias: String,
    val displayName: String,
    val bio: String? = null,
    val avatarPath: String? = null,
    val presence: BriarPresenceStatus = BriarPresenceStatus.UNKNOWN,
)
