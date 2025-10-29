package com.example.rise.transport.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        CachedChatMessageEntity::class,
        CachedChatChannelEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RoomConversationTypeConverters::class)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun chatCacheDao(): ChatCacheDao

    companion object {
        fun build(context: Context): ConversationDatabase {
            return Room.databaseBuilder(
                context,
                ConversationDatabase::class.java,
                "transport_conversations.db"
            ).fallbackToDestructiveMigration(true)
                .build()
        }
    }
}
