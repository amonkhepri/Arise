package com.example.rise.data.chat

import com.example.rise.models.TextMessage
import kotlinx.coroutines.flow.Flow

data class ChatUser(
    val id: String,
    val displayName: String
)

interface ChatRepository {
    suspend fun getCurrentUser(): ChatUser
    suspend fun getOrCreateChannel(otherUserId: String): String
    fun observeMessages(channelId: String): Flow<List<TextMessage>>
    suspend fun sendMessage(channelId: String, message: TextMessage)
}
