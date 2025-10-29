package com.example.rise.data.firestore

import com.example.rise.models.ChatChannel
import com.example.rise.models.TextMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseChatRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : ChatRemoteDataSource {

    override suspend fun getUserDisplayName(userId: String): String? {
        val snapshot = firestore.collection("users").document(userId).get().await()
        return snapshot.getString("name")
    }

    override suspend fun getExistingChannelId(currentUserId: String, otherUserId: String): String? {
        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("engagedChatChannels")
            .document(otherUserId)
            .get()
            .await()
        return snapshot.getString("channelId")
    }

    override suspend fun createChannel(currentUserId: String, otherUserId: String): String {
        val chatChannels = firestore.collection("chatChannels")
        val newChannel = chatChannels.document()
        newChannel.set(ChatChannel(mutableListOf(currentUserId, otherUserId))).await()
        val payload = mapOf("channelId" to newChannel.id)
        firestore.collection("users")
            .document(currentUserId)
            .collection("engagedChatChannels")
            .document(otherUserId)
            .set(payload)
            .await()
        firestore.collection("users")
            .document(otherUserId)
            .collection("engagedChatChannels")
            .document(currentUserId)
            .set(payload)
            .await()
        return newChannel.id
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatRemoteDataSource.RemoteMessage>> = callbackFlow {
        val registration = firestore.collection("chatChannels")
            .document(conversationId)
            .collection("messages")
            .orderBy("time")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ) {
                        trySend(emptyList())
                        close()
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val message = doc.toObject(TextMessage::class.java) ?: return@mapNotNull null
                    ChatRemoteDataSource.RemoteMessage(
                        id = doc.id,
                        payload = message,
                    )
                }
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(conversationId: String, message: TextMessage) {
        firestore.collection("chatChannels")
            .document(conversationId)
            .collection("messages")
            .add(message)
            .await()
    }
}
