package com.example.rise.data.firestore

import com.example.rise.models.TextMessage
import kotlinx.coroutines.flow.Flow

interface ChatRemoteDataSource {
    suspend fun getUserDisplayName(userId: String): String?
    suspend fun getExistingChannelId(currentUserId: String, otherUserId: String): String?
    suspend fun createChannel(currentUserId: String, otherUserId: String): String
    fun observeMessages(conversationId: String): Flow<List<RemoteMessage>>
    suspend fun sendMessage(conversationId: String, message: TextMessage)

    data class RemoteMessage(
        val id: String,
        val payload: TextMessage,
    )
}
