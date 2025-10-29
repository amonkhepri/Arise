package com.example.rise.transport.store

import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomConversationStore(
    private val dao: ConversationDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ConversationStore {

    override suspend fun upsertConversation(conversation: CanonicalConversation) {
        withContext(dispatcher) {
            dao.upsertConversation(conversation.toEntity())
        }
    }

    override suspend fun upsertMessages(
        conversationId: String,
        messages: List<CanonicalMessage>,
    ) {
        withContext(dispatcher) {
            if (messages.isEmpty()) {
                dao.deleteMessagesForConversation(conversationId)
            } else {
                dao.deleteMessagesForConversation(conversationId)
                dao.upsertMessages(messages.map { it.toEntity() })
            }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<CanonicalMessage>> {
        return dao.observeMessages(conversationId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getConversation(conversationId: String): CanonicalConversation? {
        return withContext(dispatcher) {
            dao.getConversation(conversationId)?.toModel()
        }
    }

    override suspend fun clearAll() {
        withContext(dispatcher) {
            dao.clearMessages()
            dao.clearConversations()
        }
    }

    private fun CanonicalConversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            participants = participants.toList(),
        )
    }

    private fun CanonicalMessage.toEntity(): MessageEntity {
        val transportMessageId = canonicalMessageId.substringAfter(":", canonicalMessageId)
        return MessageEntity(
            canonicalMessageId = canonicalMessageId,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            body = body,
            transport = transport.name,
            transportMessageId = transportMessageId,
            timestamp = timestamp,
        )
    }

    private fun ConversationEntity.toModel(): CanonicalConversation {
        return CanonicalConversation(
            id = id,
            participants = participants.toSet(),
            title = title,
        )
    }

    private fun MessageEntity.toModel(): CanonicalMessage {
        val transportId = runCatching { TransportId.valueOf(transport) }
            .getOrDefault(TransportId.FIRESTORE)
        return CanonicalMessage(
            canonicalMessageId = canonicalMessageId,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            body = body,
            transport = transportId,
            timestamp = timestamp,
        )
    }
}
