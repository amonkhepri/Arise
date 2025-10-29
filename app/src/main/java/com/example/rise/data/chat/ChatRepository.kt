package com.example.rise.data.chat

import com.example.rise.models.TextMessage
import kotlinx.coroutines.flow.Flow

data class ChatUser(
    val id: String,
    val displayName: String
)

interface ChatRepository {
    suspend fun getCurrentUser(): ChatUser
    suspend fun getOrCreateConversation(otherUserId: String, otherUserName: String): String
    fun observeMessages(conversationId: String): Flow<List<TextMessage>>
    suspend fun sendMessage(conversationId: String, message: TextMessage)
}
