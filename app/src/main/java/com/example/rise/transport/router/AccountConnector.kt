package com.example.rise.transport.router

import com.example.rise.models.User

/**
 * Optional capability that transports can implement to expose account/profile operations through
 * the connector stack. This keeps Firestore-specific logic encapsulated inside the connector while
 * allowing higher layers (e.g., `MyAccountRepository`) to stay transport-agnostic.
 */
interface AccountConnector {

    /**
     * Fetches the latest profile for the currently authenticated user.
     */
    suspend fun fetchAccountProfile(): User

    /**
     * Applies a partial profile update for the signed-in user. Fields left `null` are ignored.
     */
    suspend fun updateAccountProfile(update: AccountProfileUpdate)

    data class AccountProfileUpdate(
        val name: String? = null,
        val bio: String? = null,
        val profilePicturePath: String? = null,
    ) {
        fun isEmpty(): Boolean = name == null && bio == null && profilePicturePath == null
    }
}
