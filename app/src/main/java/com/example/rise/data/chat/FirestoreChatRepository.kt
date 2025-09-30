package com.example.rise.data.chat

import com.example.rise.models.ChatChannel
import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ChatRepository {

    private val usersCollection get() = firestore.collection("users")
    private val chatChannelsCollection get() = firestore.collection("chatChannels")

    override suspend fun getCurrentUser(): ChatUser {
        val firebaseUser = auth.currentUser ?: throw IllegalStateException("User must be signed in")
        val snapshot = usersCollection.document(firebaseUser.uid).get().await()
        val user = snapshot.toObject(User::class.java)
        val displayName = user?.name?.takeIf { it.isNotBlank() }
            ?: firebaseUser.displayName.orEmpty()
        return ChatUser(
            id = firebaseUser.uid,
            displayName = displayName
        )
    }

    override suspend fun getOrCreateChannel(otherUserId: String): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in")
        val currentUserDoc = usersCollection.document(currentUserId)
        val existingChannel = currentUserDoc
            .collection("engagedChatChannels")
            .document(otherUserId)
            .get()
            .await()
        val existingId = existingChannel.getString("channelId")
        if (existingId != null) {
            return existingId
        }

        val newChannelDoc = chatChannelsCollection.document()
        newChannelDoc.set(ChatChannel(mutableListOf(currentUserId, otherUserId))).await()

        currentUserDoc
            .collection("engagedChatChannels")
            .document(otherUserId)
            .set(mapOf("channelId" to newChannelDoc.id))
            .await()

        usersCollection
            .document(otherUserId)
            .collection("engagedChatChannels")
            .document(currentUserId)
            .set(mapOf("channelId" to newChannelDoc.id))
            .await()

        return newChannelDoc.id
    }

    override fun observeMessages(channelId: String): Flow<List<TextMessage>> = callbackFlow {
        val registration = chatChannelsCollection
            .document(channelId)
            .collection("messages")
            .orderBy("time")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toObject(TextMessage::class.java) }
                    .orEmpty()
                trySend(messages).isSuccess
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(channelId: String, message: TextMessage) {
        chatChannelsCollection
            .document(channelId)
            .collection("messages")
            .add(message)
            .await()
    }
}
