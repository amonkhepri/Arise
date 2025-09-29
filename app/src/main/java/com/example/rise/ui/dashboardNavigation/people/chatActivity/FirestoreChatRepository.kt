package com.example.rise.ui.dashboardNavigation.people.chatActivity

import com.example.rise.auth.UserSessionProvider
import com.example.rise.models.TextMessage
import com.example.rise.util.FirestoreUtil
import com.google.firebase.firestore.ListenerRegistration

class FirestoreChatRepository(
    private val userSessionProvider: UserSessionProvider
) : ChatRepository {

    override fun fetchCurrentUser(onResult: (ChatUser) -> Unit) {
        val userId = userSessionProvider.currentUserId()
        FirestoreUtil.getCurrentUser { user ->
            onResult(ChatUser(userId, user))
        }
    }

    override fun getOrCreateChatChannel(otherUserId: String, onResult: (String) -> Unit) {
        FirestoreUtil.getOrCreateChatChannel(otherUserId, onResult)
    }

    override fun listenForMessages(
        channelId: String,
        onMessages: (List<TextMessage>) -> Unit
    ): ListenerRegistration = FirestoreUtil.addChatMessagesListener(channelId, onMessages)

    override fun sendMessage(channelId: String, message: TextMessage) {
        FirestoreUtil.sendMessage(message, channelId)
    }

    override fun removeListener(registration: ListenerRegistration) {
        FirestoreUtil.removeListener(registration)
    }
}
