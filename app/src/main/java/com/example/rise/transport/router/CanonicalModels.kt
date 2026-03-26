package com.example.rise.transport.router

import java.util.Date

/**
 * Canonical identity anchored to Briar contact IDs. Stage 2 keeps identifiers aligned with
 * the existing Firestore user IDs; the registry will migrate them as Briar identities come online.
 */
data class CanonicalIdentity(
    val id: String,
    val displayName: String,
)

/**
 * Represents a conversation between one or more canonical identities.
 */
data class CanonicalConversation(
    val id: String,
    val participants: Set<String>,
    val title: String,
    val primaryTransportId: TransportId = TransportId.FIRESTORE,
    val briarConversationId: String? = null,
)

/**
 * Canonical message persisted by the conversation store with transport metadata so the unified
 * timeline can annotate provenance in the UI.
 */
data class CanonicalMessage(
    val canonicalMessageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
    val body: String,
    val transport: TransportId,
    val transportMessageId: String? = null,
    val transportMetadata: String? = null,
    val timestamp: Date,
)

/**
 * Snapshot of a canonical identity plus its known connector aliases, used by UI surfaces to
 * present merge options or edit mappings.
 */
data class IdentityRecord(
    val canonicalIdentity: CanonicalIdentity,
    val aliases: Map<TransportId, String>,
    val profile: IdentityProfile = IdentityProfile(),
)

enum class PresenceStatus {
    UNKNOWN,
    ONLINE,
    OFFLINE,
}

data class IdentityProfile(
    val bio: String? = null,
    val profilePicturePath: String? = null,
    val presence: PresenceStatus = PresenceStatus.UNKNOWN,
)
