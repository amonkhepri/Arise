package com.example.rise.transport.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "conversations")
@TypeConverters(RoomConversationTypeConverters::class)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val participants: List<String>,
    @ColumnInfo(defaultValue = "'FIRESTORE'")
    val primaryTransportId: String,
    val briarConversationId: String?,
)

@Entity(tableName = "messages")
@TypeConverters(RoomConversationTypeConverters::class)
data class MessageEntity(
    @PrimaryKey val canonicalMessageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
    val body: String,
    val transportId: String,
    val transportMessageId: String,
    val transportMetadata: String?,
    val timestamp: Date,
)

@Entity(
    tableName = "conversation_aliases",
    primaryKeys = ["conversationId", "transportId"],
    indices = [Index(value = ["transportConversationId"])]
)
data class ConversationAliasEntity(
    val conversationId: String,
    val transportId: String,
    val transportConversationId: String,
)

@Entity(
    tableName = "cached_chat_messages",
    primaryKeys = ["userId", "channelId", "messageId"],
)
@TypeConverters(RoomConversationTypeConverters::class)
data class CachedChatMessageEntity(
    val userId: String,
    val channelId: String,
    val messageId: String,
    val text: String,
    val timestamp: Date,
    val senderId: String,
    val recipientId: String,
    val senderName: String,
)

@Entity(
    tableName = "cached_chat_channels",
    primaryKeys = ["userId", "otherUserId"],
)
data class CachedChatChannelEntity(
    val userId: String,
    val otherUserId: String,
    val channelId: String,
)
