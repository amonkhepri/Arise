package com.example.rise.transport.store

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.TransportId
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)

@OptIn(ExperimentalCoroutinesApi::class)
class RoomConversationStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private lateinit var database: ConversationDatabase
    private lateinit var store: RoomConversationStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConversationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomConversationStore(database.conversationDao(), dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsertConversation persists entity`() = scope.runTest {
        val conversation = conversation(
            id = "conversation-1",
            participants = setOf("self", "other"),
            primary = TransportId.BRIAR,
            briarAlias = "briar-1"
        )

        store.upsertConversation(conversation)

        val loaded = store.getConversation("conversation-1")
        assertEquals(conversation, loaded)
    }

    @Test
    fun `alias helpers persist lookups`() = scope.runTest {
        val conversation = conversation("conversation-1", setOf("self", "other"))
        store.upsertConversation(conversation)

        store.upsertAlias("conversation-1", TransportId.BRIAR, "briar-alias")

        val restored = store.getAlias("conversation-1", TransportId.BRIAR)
        assertEquals("briar-alias", restored)
    }

    @Test
    fun `upsertMessages stores sorted canonical timeline`() = scope.runTest {
        val conversation = conversation("conversation-1", setOf("self", "other"))
        store.upsertConversation(conversation)
        val later = Date(2L)
        val earlier = Date(1L)
        val messages = listOf(
            message("conversation-1", "msg-2", timestamp = later, body = "second"),
            message("conversation-1", "msg-1", timestamp = earlier, body = "first"),
        )

        store.upsertMessages("conversation-1", messages)

        val stored = store.observeMessages("conversation-1")
        stored.test {
            val emission = awaitItem()
            assertEquals(listOf(messages[1], messages[0]), emission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeMessages emits updates when messages arrive`() = scope.runTest {
        val conversation = conversation("conversation-1", setOf("self", "other"))
        store.upsertConversation(conversation)

        store.observeMessages("conversation-1").test {
            var emission = awaitItem()
            if (emission.isNotEmpty()) {
                // In case Room delivers a cached value immediately; ensure we start empty.
                emission = awaitItem()
            }
            assertEquals(emptyList<CanonicalMessage>(), emission)

            val messages = listOf(
                message("conversation-1", "msg-1", Date(1L), "hello"),
                message("conversation-1", "msg-2", Date(2L), "world"),
            )
            store.upsertMessages("conversation-1", messages)
            scope.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(messages, updated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsertMessages preserves connector metadata`() = scope.runTest {
        val conversation = conversation("conversation-1", setOf("self", "other"))
        store.upsertConversation(conversation)
        val connectorMessage = CanonicalMessage(
            canonicalMessageId = "CANON-1",
            conversationId = "conversation-1",
            senderId = "self",
            recipientId = "other",
            senderName = "Self",
            body = "briar hello",
            transport = TransportId.BRIAR,
            transportMessageId = "BRIAR:42",
            transportMetadata = """{"relay":"briar"}""",
            timestamp = Date(3L),
        )

        store.upsertMessages("conversation-1", listOf(connectorMessage))

        store.observeMessages("conversation-1").test {
            var emission = awaitItem()
            if (emission.isEmpty()) {
                emission = awaitItem()
            }
            val stored = emission.single()
            assertEquals(connectorMessage, stored)
            assertEquals("BRIAR:42", stored.transportMessageId)
            assertEquals("""{"relay":"briar"}""", stored.transportMetadata)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsertMessages clears persisted rows on empty snapshot`() = scope.runTest {
        val conversation = conversation("conversation-1", setOf("self", "other"))
        store.upsertConversation(conversation)
        val messages = listOf(message("conversation-1", "msg-1", Date(1L), "hello"))
        store.upsertMessages("conversation-1", messages)

        store.upsertMessages("conversation-1", emptyList())

        store.observeMessages("conversation-1").test {
            val emission = awaitItem()
            assertEquals(emptyList<CanonicalMessage>(), emission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAll removes conversations and messages`() = scope.runTest {
        val conversation = conversation("conversation-1", setOf("self", "other"))
        val messages = listOf(message("conversation-1", "msg-1", Date(1L), "hello"))
        store.upsertConversation(conversation)
        store.upsertMessages("conversation-1", messages)

        store.upsertAlias("conversation-1", TransportId.FIRESTORE, "firestore")

        store.clearAll()

        assertNull(store.getConversation("conversation-1"))
        assertNull(store.getAlias("conversation-1", TransportId.FIRESTORE))
        store.observeMessages("conversation-1").test {
            val emission = awaitItem()
            assertEquals(emptyList<CanonicalMessage>(), emission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun conversation(
        id: String,
        participants: Set<String>,
        primary: TransportId = TransportId.FIRESTORE,
        briarAlias: String? = null,
    ): CanonicalConversation {
        return CanonicalConversation(
            id = id,
            participants = participants,
            title = "Chat with ${participants.joinToString()}",
            primaryTransportId = primary,
            briarConversationId = briarAlias,
        )
    }

    private fun message(
        conversationId: String,
        messageId: String,
        timestamp: Date,
        body: String = "body",
    ): CanonicalMessage {
        return CanonicalMessage(
            canonicalMessageId = "FIRESTORE:$messageId",
            conversationId = conversationId,
            senderId = "self",
            recipientId = "other",
            senderName = "Self",
            body = body,
            transport = TransportId.FIRESTORE,
            transportMessageId = messageId,
            timestamp = timestamp,
        )
    }
}
