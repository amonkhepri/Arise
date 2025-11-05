package com.example.rise.data.myaccount

import com.example.rise.models.User

/**
 * Repository for managing user account data.
 */
interface MyAccountRepository {
    /**
     * Returns the current user.
     */
    suspend fun fetchCurrentUser(): User

    /**
     * Updates the current user's profile.
     */
    suspend fun updateCurrentUser(name: String, bio: String)

    /**
     * Signs out the current user.
     */
    suspend fun signOut()
}
