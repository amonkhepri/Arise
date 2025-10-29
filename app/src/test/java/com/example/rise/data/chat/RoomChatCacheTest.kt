package com.example.rise.data.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rise.transport.store.ConversationDatabase
import com.example.rise.transport.store.ChatCacheDao
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomChatCacheTest {

    private lateinit var database: ConversationDatabase
    private lateinit var dao: ChatCacheDao
    private lateinit var cache: RoomChatCache

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConversationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.chatCacheDao()
        cache = RoomChatCache(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `writeMessages stores messages that can be read back`() = runBlocking {
        val messages = listOf(
            cachedMessage(1),
            cachedMessage(2),
        )

        cache.writeMessages(USER_ID, CHANNEL_ID, messages)

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(messages, stored)
    }

    @Test
    fun `writeMessages keeps only the most recent 100 entries`() = runBlocking {
        val messages = (0 until 120).map { index -> cachedMessage(index) }

        cache.writeMessages(USER_ID, CHANNEL_ID, messages)

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(100, stored.size)
        assertEquals(messages.takeLast(100), stored)
    }

    @Test
    fun `writeMessages replaces snapshot to avoid duplicates`() = runBlocking {
        val initial = listOf(cachedMessage(1, text = "first"))
        val updated = listOf(cachedMessage(1, text = "updated"))

        cache.writeMessages(USER_ID, CHANNEL_ID, initial)
        cache.writeMessages(USER_ID, CHANNEL_ID, updated)

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(updated, stored)
    }

    @Test
    fun `writeMessages with empty list clears cached entries`() = runBlocking {
        cache.writeMessages(USER_ID, CHANNEL_ID, listOf(cachedMessage(1)))

        cache.writeMessages(USER_ID, CHANNEL_ID, emptyList())

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(emptyList<CachedChatMessage>(), stored)
    }

    @Test
    fun `channel ids are stored and retrieved`() = runBlocking {
        cache.writeChannelId(USER_ID, OTHER_USER_ID, CHANNEL_ID)

        val stored = cache.readChannelId(USER_ID, OTHER_USER_ID)
        assertEquals(CHANNEL_ID, stored)
    }

    @Test
    fun `clear removes only data for provided user`() = runBlocking {
        val otherMessages = listOf(cachedMessage(99))

        cache.writeMessages(USER_ID, CHANNEL_ID, listOf(cachedMessage(1)))
        cache.writeChannelId(USER_ID, OTHER_USER_ID, CHANNEL_ID)

        cache.writeMessages(SECOND_USER_ID, CHANNEL_ID, otherMessages)
        cache.writeChannelId(SECOND_USER_ID, OTHER_USER_ID, SECOND_CHANNEL_ID)

        cache.clear(USER_ID)

        assertEquals(emptyList<CachedChatMessage>(), cache.readMessages(USER_ID, CHANNEL_ID))
        assertNull(cache.readChannelId(USER_ID, OTHER_USER_ID))

        assertEquals(otherMessages, cache.readMessages(SECOND_USER_ID, CHANNEL_ID))
        assertEquals(SECOND_CHANNEL_ID, cache.readChannelId(SECOND_USER_ID, OTHER_USER_ID))
    }

    private fun cachedMessage(
        index: Int,
        text: String = "Message $index",
    ): CachedChatMessage {
        return CachedChatMessage(
            messageId = "msg-$index",
            text = text,
            time = Date(index * 1_000L + 1),
            senderId = "sender-$index",
            recipientId = "recipient-$index",
            senderName = "Sender $index",
        )
    }

    private companion object {
        private const val USER_ID = "user"
        private const val SECOND_USER_ID = "other-user"
        private const val CHANNEL_ID = "channel"
        private const val SECOND_CHANNEL_ID = "second-channel"
        private const val OTHER_USER_ID = "friend"
    }
}
