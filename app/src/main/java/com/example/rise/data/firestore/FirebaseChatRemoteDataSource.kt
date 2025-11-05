package com.example.rise.data.firestore

import com.example.rise.models.ChatChannel
import com.example.rise.models.TextMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emitSafely(emptyList(), closeAfter = true)
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val messages = snapshot?.toRemoteMessages().orEmpty()
                emitSafely(messages)
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

private fun QuerySnapshot.toRemoteMessages(): List<ChatRemoteDataSource.RemoteMessage> {
    val payloads = toObjects(TextMessage::class.java)
    return documents.mapIndexedNotNull { index, document ->
        val payload = payloads.getOrNull(index) ?: return@mapIndexedNotNull null
        ChatRemoteDataSource.RemoteMessage(
            id = document.id,
            payload = payload,
        )
    }
}

private fun ProducerScope<List<ChatRemoteDataSource.RemoteMessage>>.emitSafely(
    messages: List<ChatRemoteDataSource.RemoteMessage>,
    closeAfter: Boolean = false,
) {
    val result = trySend(messages)
    when {
        result.isSuccess -> {
            if (closeAfter) {
                close()
            }
        }
        result.isClosed -> {
            val cause = result.exceptionOrNull()
            if (cause != null) {
                close(cause)
            }
        }
        else -> {
            launch {
                try {
                    send(messages)
                    if (closeAfter) {
                        close()
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    close(error)
                }
            }
        }
    }
}
