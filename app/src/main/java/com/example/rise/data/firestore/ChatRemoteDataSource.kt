package com.example.rise.data.firestore

import com.example.rise.models.TextMessage
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for interacting with the chat backing store (currently Firebase Cloud Firestore).
 *
 * Implementations should encapsulate Firestore specifics—such as collection names, snapshot
 * listeners, and channel fan-out—so callers can work with simple suspend functions and flows.
 */
interface ChatRemoteDataSource {
    /**
     * Fetches the display name exposed under `users/{userId}/name`.
     *
     * @return the name when present, or `null` when the user document or field is missing.
     */
    suspend fun getUserDisplayName(userId: String): String?

    /**
     * Looks up an existing conversation between `currentUserId` and `otherUserId`.
     *
     * The implementation checks the `engagedChatChannels` sub-collection for a previously
     * negotiated channel and returns its identifier when found.
     *
     * @return the channel identifier, or `null` when no engagement exists.
     */
    suspend fun getExistingChannelId(currentUserId: String, otherUserId: String): String?

    /**
     * Creates a new `chatChannels` document linking both participants and stores the resulting id
     * under each user's `engagedChatChannels/{otherUserId}` reference for future lookups.
     *
     * @return the identifier of the newly created channel.
     */
    suspend fun createChannel(currentUserId: String, otherUserId: String): String

    /**
     * Observes ordered message snapshots for the provided conversation id.
     *
     * Implementations typically wrap Firestore's snapshot listener in a cold [Flow]. The flow emits
     * lists of [RemoteMessage] objects, is closed when the listener is removed, and may emit an
     * empty list before completing when the backend returns `PERMISSION_DENIED`.
     */
    fun observeMessages(conversationId: String): Flow<List<RemoteMessage>>

    /**
     * Persists a new chat message inside `chatChannels/{conversationId}/messages`.
     */
    suspend fun sendMessage(conversationId: String, message: TextMessage)

    data class RemoteMessage(
        val id: String,
        val payload: TextMessage,
    )
}
