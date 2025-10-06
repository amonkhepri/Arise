package com.example.rise.data.chat

import com.example.rise.models.ChatChannel
import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Volatile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val localCache: ChatLocalCache,
) : ChatRepository {

    private val usersCollection get() = firestore.collection("users")
    private val chatChannelsCollection get() = firestore.collection("chatChannels")
    private val channelCache = ConcurrentHashMap<String, String>()
    private val messagesCache = ConcurrentHashMap<String, List<TextMessage>>()
    @Volatile private var cachedCurrentUser: ChatUser? = null

    override suspend fun getCurrentUser(): ChatUser {
        val firebaseUser = auth.currentUser ?: throw IllegalStateException("User must be signed in")
        val previousUserId = cachedCurrentUser?.id
        if (previousUserId != null && previousUserId != firebaseUser.uid) {
            clearUserCaches(previousUserId)
        }
        cachedCurrentUser?.let { cached ->
            if (cached.id == firebaseUser.uid) return cached
        }
        val snapshot = usersCollection.document(firebaseUser.uid).get().await()
        val user = snapshot.toObject(User::class.java)
        val displayName = user?.name?.takeIf { it.isNotBlank() }
            ?: firebaseUser.displayName.orEmpty()
        return ChatUser(
            id = firebaseUser.uid,
            displayName = displayName
        ).also { cachedCurrentUser = it }
    }

    override suspend fun getOrCreateChannel(otherUserId: String): String {
        channelCache[otherUserId]?.let { return it }
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in")
        localCache.readChannelId(currentUserId, otherUserId)?.let { cachedId ->
            channelCache[otherUserId] = cachedId
            return cachedId
        }
        val currentUserDoc = usersCollection.document(currentUserId)
        val existingChannel = currentUserDoc
            .collection("engagedChatChannels")
            .document(otherUserId)
            .get()
            .await()
        val existingId = existingChannel.getString("channelId")
        if (existingId != null) {
            channelCache[otherUserId] = existingId
            localCache.writeChannelId(currentUserId, otherUserId, existingId)
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

        return newChannelDoc.id.also {
            channelCache[otherUserId] = it
            localCache.writeChannelId(currentUserId, otherUserId, it)
        }
    }

    override fun observeMessages(channelId: String): Flow<List<TextMessage>> = callbackFlow {
        val userId = auth.currentUser?.uid
        val inMemory = messagesCache[channelId]
        if (inMemory != null && inMemory.isNotEmpty()) {
            trySend(inMemory).isSuccess
        } else if (userId != null) {
            val cached = localCache.readMessages(userId, channelId)
            if (cached.isNotEmpty()) {
                messagesCache[channelId] = cached
                trySend(cached).isSuccess
            }
        }
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
                messagesCache[channelId] = messages
                if (userId != null) {
                    localCache.writeMessages(userId, channelId, messages)
                }
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

    private fun clearUserCaches(previousUserId: String) {
        channelCache.clear()
        messagesCache.clear()
        localCache.clear(previousUserId)
        cachedCurrentUser = null
    }
}
