package com.example.rise.data.chat

import android.content.Context
import com.example.rise.models.TextMessage
import java.util.Date
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.edit

class SharedPrefsChatCache(context: Context) : ChatLocalCache {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readMessages(userId: String, channelId: String): List<TextMessage> {
        val raw = preferences.getString(messageKey(userId, channelId), null)
            ?: preferences.getString(legacyMessageKey(userId, channelId), null)
            ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val messages = mutableListOf<TextMessage>()
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val text = obj.optString(KEY_TEXT)
                val time = obj.optLong(KEY_TIME, 0L)
                val senderId = obj.optString(KEY_SENDER_ID)
                val recipientId = obj.optString(KEY_RECIPIENT_ID)
                val senderName = obj.optString(KEY_SENDER_NAME)
                if (time <= 0L || senderId.isEmpty() || recipientId.isEmpty()) {
                    continue
                }
                messages += TextMessage(
                    text = text,
                    time = Date(time),
                    senderId = senderId,
                    recipientId = recipientId,
                    senderName = senderName,
                )
            }
            messages
        } catch (_: JSONException) {
            emptyList()
        }
    }

    override fun writeMessages(userId: String, channelId: String, messages: List<TextMessage>) {
        val key = messageKey(userId, channelId)
        if (messages.isEmpty()) {
            preferences.edit {
                remove(key)
                    .remove(legacyMessageKey(userId, channelId))
            }
            return
        }
        val trimmed = if (messages.size > MAX_STORED_MESSAGES) {
            messages.takeLast(MAX_STORED_MESSAGES)
        } else {
            messages
        }
        val array = JSONArray()
        trimmed.forEach { message ->
            val obj = JSONObject().apply {
                put(KEY_TEXT, message.text)
                put(KEY_TIME, message.time.time)
                put(KEY_SENDER_ID, message.senderId)
                put(KEY_RECIPIENT_ID, message.recipientId)
                put(KEY_SENDER_NAME, message.senderName)
            }
            array.put(obj)
        }
        preferences.edit {
            putString(key, array.toString())
                .remove(legacyMessageKey(userId, channelId))
        }
    }

    override fun readChannelId(userId: String, otherUserId: String): String? {
        return preferences.getString(channelKey(userId, otherUserId), null)
    }

    override fun writeChannelId(userId: String, otherUserId: String, channelId: String) {
        preferences.edit { putString(channelKey(userId, otherUserId), channelId) }
    }

    override fun clear(userId: String) {
        val prefix = prefix(userId)
        val keysToRemove = preferences.all.keys.filter { it.startsWith(prefix) }
        if (keysToRemove.isEmpty()) return
        with(preferences.edit()) {
            keysToRemove.forEach { remove(it) }
            apply()
        }
    }

    private fun messageKey(userId: String, channelId: String) = prefix(userId) + MESSAGE_MARKER + DELIMITER + channelId
    private fun legacyMessageKey(userId: String, channelId: String) = prefix(userId) + channelId
    private fun channelKey(userId: String, otherUserId: String) = prefix(userId) + CHANNEL_MARKER + DELIMITER + otherUserId
    private fun prefix(userId: String) = "$userId$DELIMITER"

    companion object {
        private const val PREFS_NAME = "chat_local_cache"
        private const val MAX_STORED_MESSAGES = 100
        private const val DELIMITER = ":"
        private const val MESSAGE_MARKER = "messages"
        private const val CHANNEL_MARKER = "channel"

        private const val KEY_TEXT = "text"
        private const val KEY_TIME = "time"
        private const val KEY_SENDER_ID = "senderId"
        private const val KEY_RECIPIENT_ID = "recipientId"
        private const val KEY_SENDER_NAME = "senderName"
    }
}
