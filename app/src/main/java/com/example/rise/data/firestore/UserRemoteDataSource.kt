package com.example.rise.data.firestore

import com.example.rise.models.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for loading and mutating remote user profiles regardless of the underlying backend.
 *
 * Production builds currently use [FirebaseUserRemoteDataSource], which maps these calls to the
 * `users/{id}` documents in Firestore. Tests provide fake in-memory implementations so higher
 * layers (e.g., `RouterMyAccountRepository`, `FirestoreConnectorTest`) can exercise the same API
 * without touching the network.
 *
 * Each method focuses on a single responsibility:
 * - [fetchUser] returns the latest snapshot of a single profile when you only need to hydrate the
 *   current user view.
 * - [updateUser] applies partial field updates (name, bio, etc.) without replacing the whole
 *   document.
 * - [setUser] creates or replaces the full document when bootstrap flows need to establish the
 *   initial profile scaffold for a freshly signed-in user.
 * - [observeUsers] streams roster changes for features that need to reflect contact updates in
 *   real time.
 */
interface UserRemoteDataSource {

    /**
     * Fetches the current representation of the user with [userId], or `null` when the record does
     * not exist. Implementations should perform a single network/database read and avoid caching so
     * callers decide how to store the result.
     */
    suspend fun fetchUser(userId: String): User?

    /**
     * Applies a partial update to the user document identified by [userId].
     *
     * The [updates] map mirrors the semantics of Firestore's `update()` call: only the provided
     * fields are mutated, missing keys are left untouched, and providing an empty map should be a
     * no-op. Implementations may throw if the user does not exist.
     */
    suspend fun updateUser(userId: String, updates: Map<String, Any>)

    /**
     * Creates or replaces the entire user document.
     */
    suspend fun setUser(userId: String, user: User)

    /**
     * Observes the full set of remote user documents and emits the canonical roster whenever any
     * entry changes. The returned [Flow] must stay hot until the collector cancels, surfacing
     * backend errors via exceptions.
     */
    fun observeUsers(): Flow<List<UserSnapshot>>

    /**
     * Lightweight projection of a remote user document used by roster flows to keep track of the
     * canonical id and payload together.
     */
    data class UserSnapshot(
        val id: String,
        val user: User,
    )
}
