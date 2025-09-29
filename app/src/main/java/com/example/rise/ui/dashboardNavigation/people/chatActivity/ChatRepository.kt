package com.example.rise.ui.dashboardNavigation.people.chatActivity

import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.google.firebase.firestore.ListenerRegistration

data class ChatUser(val id: String, val user: User)

interface ChatRepository {
    fun fetchCurrentUser(onResult: (ChatUser) -> Unit)
    fun getOrCreateChatChannel(otherUserId: String, onResult: (String) -> Unit)
    fun listenForMessages(channelId: String, onMessages: (List<TextMessage>) -> Unit): ListenerRegistration
    fun sendMessage(channelId: String, message: TextMessage)
    fun removeListener(registration: ListenerRegistration)
}
