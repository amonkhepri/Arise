package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarMessage
import com.example.rise.briar.runtime.BriarOutboundMessage
import com.example.rise.briar.runtime.BriarPresenceStatus
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportId
import java.util.Date

internal fun BriarMessage.toConnectorMessage(): ConnectorInboundMessage {
    val recipientId = recipientIds.firstOrNull() ?: senderId
    return ConnectorInboundMessage(
        messageId = messageId,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        senderName = senderName,
        body = body,
        transport = TransportId.BRIAR,
        timestamp = Date.from(timestamp),
    )
}

internal fun BriarContact.toConnectorContact(): ConnectorContact {
    return ConnectorContact(
        transport = TransportId.BRIAR,
        transportId = transportAlias,
        displayName = displayName,
        canonicalId = canonicalId,
        bio = bio,
        profilePicturePath = avatarPath,
        presence = presence.toRouterPresence(),
    )
}

internal fun ConnectorOutboundMessage.toBriarOutboundMessage(): BriarOutboundMessage {
    return BriarOutboundMessage(
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        recipientIds = recipientIds,
        body = body,
        timestamp = timestamp.toInstant(),
    )
}

private fun BriarPresenceStatus.toRouterPresence(): PresenceStatus {
    return when (this) {
        BriarPresenceStatus.UNKNOWN -> PresenceStatus.UNKNOWN
        BriarPresenceStatus.ONLINE -> PresenceStatus.ONLINE
        BriarPresenceStatus.OFFLINE -> PresenceStatus.OFFLINE
    }
}
