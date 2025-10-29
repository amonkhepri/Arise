package com.example.rise.transport.store

import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalMessage
import kotlinx.coroutines.flow.Flow

interface ConversationStore {

    suspend fun upsertConversation(conversation: CanonicalConversation)

    suspend fun upsertMessages(conversationId: String, messages: List<CanonicalMessage>)

    fun observeMessages(conversationId: String): Flow<List<CanonicalMessage>>

    suspend fun getConversation(conversationId: String): CanonicalConversation?

    suspend fun clearAll()
}
