package com.example.rise.transport.store

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ConversationDatabaseMigrations {

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE conversations ADD COLUMN primaryTransportId TEXT NOT NULL DEFAULT 'FIRESTORE'"
            )
            db.execSQL(
                "ALTER TABLE conversations ADD COLUMN briarConversationId TEXT"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_aliases (
                    conversationId TEXT NOT NULL,
                    transportId TEXT NOT NULL,
                    transportConversationId TEXT NOT NULL,
                    PRIMARY KEY(conversationId, transportId)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_conversation_aliases_transportConversationId ON conversation_aliases(transportConversationId)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS messages_new (
                    canonicalMessageId TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    senderId TEXT NOT NULL,
                    recipientId TEXT NOT NULL,
                    senderName TEXT NOT NULL,
                    body TEXT NOT NULL,
                    transportId TEXT NOT NULL,
                    transportMessageId TEXT NOT NULL,
                    transportMetadata TEXT,
                    timestamp INTEGER NOT NULL,
                    PRIMARY KEY(canonicalMessageId)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO messages_new (
                    canonicalMessageId,
                    conversationId,
                    senderId,
                    recipientId,
                    senderName,
                    body,
                    transportId,
                    transportMessageId,
                    timestamp
                )
                SELECT
                    canonicalMessageId,
                    conversationId,
                    senderId,
                    recipientId,
                    senderName,
                    body,
                    transport,
                    transportMessageId,
                    timestamp
                FROM messages
                """.trimIndent()
            )
            db.execSQL("DROP TABLE messages")
            db.execSQL("ALTER TABLE messages_new RENAME TO messages")
        }
    }
}
