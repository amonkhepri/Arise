package com.example.rise.data.chat

import com.example.rise.models.TextMessage
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransportBackedChatRepository(
    private val transportRouter: TransportRouter,
) : ChatRepository {

    override suspend fun getCurrentUser(): ChatUser {
        val identity = transportRouter.ensureCurrentIdentity()
        return identity.toChatUser()
    }

    override suspend fun getOrCreateConversation(
        otherUserId: String,
        otherUserName: String
    ): String {
        val otherIdentity = CanonicalIdentity(
            id = otherUserId,
            displayName = otherUserName.ifBlank { otherUserId },
        )
        val conversation = transportRouter.ensureConversation(otherIdentity)
        return conversation.id
    }

    override fun observeMessages(conversationId: String): Flow<List<TextMessage>> {
        return transportRouter.observeConversation(conversationId).map { messages ->
            messages.map { it.toTextMessage() }
        }
    }

    override suspend fun sendMessage(conversationId: String, message: TextMessage) {
        val outbound = ConnectorOutboundMessage(
            conversationId = conversationId,
            senderId = message.senderId,
            senderName = message.senderName,
            recipientIds = setOf(message.recipientId),
            body = message.text,
            timestamp = message.time,
        )
        transportRouter.sendMessage(outbound)
    }

    private fun CanonicalIdentity.toChatUser(): ChatUser {
        return ChatUser(
            id = id,
            displayName = displayName,
        )
    }

    private fun CanonicalMessage.toTextMessage(): TextMessage {
        return TextMessage(
            text = body,
            time = timestamp,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
        )
    }
}
