package com.example.rise.transport.store

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDatabaseMigrationTest {

    // Instrumentation handle used for both app and test contexts.
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    // Target app context gives us the real DB file location.
    private val appContext = instrumentation.targetContext
    // Instrumentation context exposes the exported Room schema assets.
    private val assetContext = instrumentation.context

    @Test
    fun migrate2To3_preservesConversationsAndMessages() {
        appContext.deleteDatabase(TEST_DB)
        // Recreate the version-2 schema from the JSON snapshot so it matches production installs.
        createDatabaseFromSchema(version = 2) { db ->
            db.execSQL(
                "INSERT INTO conversations (id, title, participants) VALUES ('conv-1', 'Test', 'self|other')"
            )
            db.execSQL(
                """
                INSERT INTO messages (
                    canonicalMessageId,
                    conversationId,
                    senderId,
                    recipientId,
                    senderName,
                    body,
                    transport,
                    transportMessageId,
                    timestamp
                ) VALUES (
                    'FIRESTORE:msg-1',
                    'conv-1',
                    'self',
                    'other',
                    'Self',
                    'hello',
                    'FIRESTORE',
                    'msg-1',
                    1
                )
                """.trimIndent()
            )
        }

        // Apply the 2→3 migration and ensure newly-added columns contain the expected values.
        migrateDatabase().use { database ->
            database.query(
                "SELECT primaryTransportId, briarConversationId FROM conversations WHERE id = 'conv-1'"
            ).use { cursor ->
                require(cursor.moveToFirst())
                val primaryTransport = cursor.getString(0)
                val briarAlias = if (cursor.isNull(1)) null else cursor.getString(1)
                assertEquals("FIRESTORE", primaryTransport)
                assertNull(briarAlias)
            }

            database.query(
                "SELECT transportId, transportMetadata FROM messages WHERE canonicalMessageId = 'FIRESTORE:msg-1'"
            ).use { cursor ->
                require(cursor.moveToFirst())
                val transportId = cursor.getString(0)
                val metadata = if (cursor.isNull(1)) null else cursor.getString(1)
                assertEquals("FIRESTORE", transportId)
                assertNull(metadata)
            }
        }
    }

    private fun createDatabaseFromSchema(
        version: Int,
        block: (SupportSQLiteDatabase) -> Unit,
    ) {
        // Open a helper that creates the historical schema during onCreate, then run the seeding block.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(appContext)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        applySchema(db, version)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        helper.writableDatabase.use(block)
        helper.close()
    }

    private fun migrateDatabase(): SupportSQLiteDatabase {
        // Open a helper that runs MIGRATION_2_3 when the DB is first opened so we can inspect the result.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(appContext)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        ConversationDatabaseMigrations.MIGRATION_2_3.migrate(db)
                    }
                })
                .build()
        )
        return helper.writableDatabase
    }

    private fun applySchema(db: SupportSQLiteDatabase, version: Int) {
        // Load the exported Room schema JSON for the target version from instrumentation assets.
        val assetPath = "$SCHEMA_PATH/$version.json"
        val schemaJson = assetContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        val schema = JSONObject(schemaJson).getJSONObject("database")
        createEntities(db, schema.getJSONArray("entities"))
        schema.optJSONArray("views")?.let { createViews(db, it) }
        schema.optJSONArray("setupQueries")?.let { executeQueries(db, it) }
    }

    private fun createEntities(db: SupportSQLiteDatabase, entities: JSONArray) {
        // Iterate each entity definition and execute the stored CREATE TABLE statement.
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val createSql = entity.getString("createSql")
                .replace("`${'$'}{TABLE_NAME}`", tableName)
            db.execSQL(createSql)
        }
    }

    private fun createViews(db: SupportSQLiteDatabase, views: JSONArray) {
        // Some schemas include database views; recreate them if present in the snapshot.
        for (i in 0 until views.length()) {
            val view = views.getJSONObject(i)
            val viewName = view.getString("viewName")
            val createSql = view.getString("createSql")
                .replace("`${'$'}{VIEW_NAME}`", viewName)
            db.execSQL(createSql)
        }
    }

    private fun executeQueries(db: SupportSQLiteDatabase, queries: JSONArray) {
        // Apply any schema setup queries (indices, triggers, etc.) captured by Room.
        for (i in 0 until queries.length()) {
            db.execSQL(queries.getString(i))
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
        private const val SCHEMA_PATH =
            "com.example.rise.transport.store.ConversationDatabase"
    }
}
