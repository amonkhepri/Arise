package com.example.rise.transport.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Transaction
    suspend fun replaceMessages(conversationId: String, messages: List<MessageEntity>) {
        deleteMessagesForConversation(conversationId)
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
    }

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun conversationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: ConversationAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<ConversationAliasEntity>)

    @Query("SELECT transportConversationId FROM conversation_aliases WHERE conversationId = :conversationId AND transportId = :transportId")
    suspend fun getAlias(conversationId: String, transportId: String): String?

    @Query("DELETE FROM conversation_aliases WHERE conversationId = :conversationId")
    suspend fun deleteAliasesForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM conversation_aliases")
    suspend fun clearAliases()
}
