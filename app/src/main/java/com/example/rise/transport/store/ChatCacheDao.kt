package com.example.rise.transport.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ChatCacheDao {

    @Query(
        """
        SELECT * FROM cached_chat_messages
        WHERE userId = :userId AND channelId = :channelId
        ORDER BY timestamp ASC
        """
    )
    suspend fun readMessages(userId: String, channelId: String): List<CachedChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<CachedChatMessageEntity>)

    @Query(
        """
        DELETE FROM cached_chat_messages
        WHERE userId = :userId AND channelId = :channelId
        """
    )
    suspend fun deleteMessages(userId: String, channelId: String)

    @Transaction
    suspend fun replaceMessages(
        userId: String,
        channelId: String,
        messages: List<CachedChatMessageEntity>,
    ) {
        deleteMessages(userId, channelId)
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
    }

    @Query(
        """
        SELECT channelId FROM cached_chat_channels
        WHERE userId = :userId AND otherUserId = :otherUserId
        LIMIT 1
        """
    )
    suspend fun readChannelId(userId: String, otherUserId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: CachedChatChannelEntity)

    @Query(
        """
        DELETE FROM cached_chat_channels
        WHERE userId = :userId
        """
    )
    suspend fun clearChannelsForUser(userId: String)

    @Query(
        """
        DELETE FROM cached_chat_messages
        WHERE userId = :userId
        """
    )
    suspend fun clearMessagesForUser(userId: String)
}
