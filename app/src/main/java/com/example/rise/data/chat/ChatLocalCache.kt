package com.example.rise.data.chat

import com.example.rise.models.TextMessage

interface ChatLocalCache {
    fun readMessages(userId: String, channelId: String): List<TextMessage>
    fun writeMessages(userId: String, channelId: String, messages: List<TextMessage>)
    fun readChannelId(userId: String, otherUserId: String): String?
    fun writeChannelId(userId: String, otherUserId: String, channelId: String)
    fun clear(userId: String)
}
