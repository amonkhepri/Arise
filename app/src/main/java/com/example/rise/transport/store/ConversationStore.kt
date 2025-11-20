package com.example.rise.transport.store

import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.flow.Flow

/**
 * Persists canonical conversations and their messages for all transport connectors.
 * Implementations are responsible for keeping data consistent and thread-safe.
 */
interface ConversationStore {

    /**
     * Inserts or updates the stored details for a conversation.
     */
    suspend fun upsertConversation(conversation: CanonicalConversation)

    /**
     * Inserts or updates messages for the given conversation, replacing any messages
     * with matching canonical IDs.
     */
    suspend fun upsertMessages(conversationId: String, messages: List<CanonicalMessage>)

    /**
     * Streams message updates for the specified conversation. The returned flow should
     * emit the current message list immediately and re-emit whenever messages change.
     */
    fun observeMessages(conversationId: String): Flow<List<CanonicalMessage>>

    /**
     * Returns the stored conversation, or null when none exists.
     */
    suspend fun getConversation(conversationId: String): CanonicalConversation?

    /**
     * Persists the transport-specific alias for the given conversation.
     */
    suspend fun upsertAlias(conversationId: String, transportId: TransportId, alias: String)

    /**
     * Returns the persisted alias for the conversation + transport combo, or null if unknown.
     */
    suspend fun getAlias(conversationId: String, transportId: TransportId): String?

    /**
     * Removes every stored conversation and message entry.
     */
    suspend fun clearAll()
}
