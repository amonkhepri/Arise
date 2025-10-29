package com.example.rise.data.chat

import java.util.Date

data class CachedChatMessage(
    val messageId: String,
    val text: String,
    val time: Date,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
)

interface ChatLocalCache {
    suspend fun readMessages(userId: String, channelId: String): List<CachedChatMessage>
    suspend fun writeMessages(userId: String, channelId: String, messages: List<CachedChatMessage>)
    suspend fun readChannelId(userId: String, otherUserId: String): String?
    suspend fun writeChannelId(userId: String, otherUserId: String, channelId: String)
    suspend fun clear(userId: String)
}
