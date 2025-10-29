package com.example.rise.data.chat

import com.example.rise.transport.store.CachedChatChannelEntity
import com.example.rise.transport.store.CachedChatMessageEntity
import com.example.rise.transport.store.ChatCacheDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomChatCache(
    private val dao: ChatCacheDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatLocalCache {

    override suspend fun readMessages(userId: String, channelId: String): List<CachedChatMessage> {
        return withContext(dispatcher) {
            dao.readMessages(userId, channelId).map { it.toModel() }
        }
    }

    override suspend fun writeMessages(
        userId: String,
        channelId: String,
        messages: List<CachedChatMessage>,
    ) {
        withContext(dispatcher) {
            if (messages.isEmpty()) {
                dao.deleteMessages(userId, channelId)
                return@withContext
            }
            val trimmed = if (messages.size > MAX_STORED_MESSAGES) {
                messages.takeLast(MAX_STORED_MESSAGES)
            } else {
                messages
            }
            val entities = trimmed.map { it.toEntity(userId, channelId) }
            dao.replaceMessages(userId, channelId, entities)
        }
    }

    override suspend fun readChannelId(userId: String, otherUserId: String): String? {
        return withContext(dispatcher) {
            dao.readChannelId(userId, otherUserId)
        }
    }

    override suspend fun writeChannelId(userId: String, otherUserId: String, channelId: String) {
        withContext(dispatcher) {
            dao.upsertChannel(
                CachedChatChannelEntity(
                    userId = userId,
                    otherUserId = otherUserId,
                    channelId = channelId,
                )
            )
        }
    }

    override suspend fun clear(userId: String) {
        withContext(dispatcher) {
            dao.clearMessagesForUser(userId)
            dao.clearChannelsForUser(userId)
        }
    }

    private fun CachedChatMessageEntity.toModel(): CachedChatMessage {
        return CachedChatMessage(
            messageId = messageId,
            text = text,
            time = timestamp,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
        )
    }

    private fun CachedChatMessage.toEntity(
        userId: String,
        channelId: String,
    ): CachedChatMessageEntity {
        return CachedChatMessageEntity(
            userId = userId,
            channelId = channelId,
            messageId = messageId,
            text = text,
            timestamp = time,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
        )
    }

    private companion object {
        private const val MAX_STORED_MESSAGES = 100
    }
}
