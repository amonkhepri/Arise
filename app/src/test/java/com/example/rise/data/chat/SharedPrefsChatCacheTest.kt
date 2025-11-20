package com.example.rise.data.chat

import android.content.Context
import android.content.SharedPreferences
import com.example.rise.models.TextMessage
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
class SharedPrefsChatCacheTest {

    private lateinit var preferences: FakeSharedPreferences
    private lateinit var context: Context
    private lateinit var cache: SharedPrefsChatCache

    @Before
    fun setUp() {
        preferences = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns preferences
        cache = SharedPrefsChatCache(context)
    }

    @Test
    fun `writeMessages stores messages that can be read back`() {
        val messages = listOf(message(1), message(2))

        cache.writeMessages(USER_ID, CHANNEL_ID, messages)

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(messages, stored)
    }

    @Test
    fun `writeMessages keeps only the most recent 100 entries`() {
        val allMessages = (0 until 120).map { index -> message(index) }

        cache.writeMessages(USER_ID, CHANNEL_ID, allMessages)

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(100, stored.size)
        assertEquals(allMessages.takeLast(100), stored)
    }

    @Test
    fun `writeMessages with empty list clears cached keys`() {
        cache.writeMessages(USER_ID, CHANNEL_ID, listOf(message(1)))
        assertTrue(preferences.getAll().isNotEmpty())

        cache.writeMessages(USER_ID, CHANNEL_ID, emptyList())

        assertTrue(cache.readMessages(USER_ID, CHANNEL_ID).isEmpty())
        assertTrue(preferences.getAll().isEmpty())
    }

    @Test
    fun `readMessages falls back to legacy key`() {
        val legacyJson = toJson(listOf(message(3)))
        val legacyKey = "$USER_ID:$CHANNEL_ID"
        preferences.edit().putString(legacyKey, legacyJson).apply()

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertEquals(listOf(message(3)), stored)
    }

    @Test
    fun `readMessages handles malformed json`() {
        val messageKey = "$USER_ID:messages:$CHANNEL_ID"
        preferences.edit().putString(messageKey, "not valid json").apply()

        val stored = cache.readMessages(USER_ID, CHANNEL_ID)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `channel ids are stored and retrieved`() {
        cache.writeChannelId(USER_ID, OTHER_USER_ID, CHANNEL_ID)

        assertEquals(CHANNEL_ID, cache.readChannelId(USER_ID, OTHER_USER_ID))
    }

    @Test
    fun `clear removes only data for provided user`() {
        val otherMessages = listOf(message(200))

        cache.writeMessages(USER_ID, CHANNEL_ID, listOf(message(100)))
        cache.writeChannelId(USER_ID, OTHER_USER_ID, "channel-a")

        cache.writeMessages(SECOND_USER_ID, SECOND_CHANNEL_ID, otherMessages)
        cache.writeChannelId(SECOND_USER_ID, OTHER_USER_ID, SECOND_CHANNEL_ID)

        cache.clear(USER_ID)

        assertTrue(cache.readMessages(USER_ID, CHANNEL_ID).isEmpty())
        assertNull(cache.readChannelId(USER_ID, OTHER_USER_ID))

        assertEquals(otherMessages, cache.readMessages(SECOND_USER_ID, SECOND_CHANNEL_ID))
        assertEquals(SECOND_CHANNEL_ID, cache.readChannelId(SECOND_USER_ID, OTHER_USER_ID))
    }

    private fun message(index: Int) = TextMessage(
        text = "Message $index",
        time = Date(index * 1_000L + 1),
        senderId = "sender-$index",
        recipientId = "recipient-$index",
        senderName = "Sender $index",
    )

    private fun toJson(messages: List<TextMessage>): String {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("text", message.text)
                    put("time", message.time.time)
                    put("senderId", message.senderId)
                    put("recipientId", message.recipientId)
                    put("senderName", message.senderName)
                }
            )
        }
        return array.toString()
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)

        override fun getString(key: String?, defValue: String?): String? =
            if (key != null && data.containsKey(key)) data[key] as String? else defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            when {
                key == null -> defValues
                data[key] is MutableSet<*> -> data[key] as MutableSet<String>?
                else -> defValues
            }

        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = key != null && data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values?.toMutableSet()
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removals += key
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun applyChanges() {
                if (clearRequested) {
                    data.clear()
                }
                removals.forEach { data.remove(it) }
                pending.forEach { (key, value) ->
                    if (value == null) {
                        data.remove(key)
                    } else {
                        data[key] = value
                    }
                }
                pending.clear()
                removals.clear()
                clearRequested = false
            }
        }
    }

    private companion object {
        private const val USER_ID = "user"
        private const val SECOND_USER_ID = "other-user"
        private const val CHANNEL_ID = "channel"
        private const val SECOND_CHANNEL_ID = "second-channel"
        private const val OTHER_USER_ID = "friend"
    }
}
