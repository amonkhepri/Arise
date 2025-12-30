package com.example.rise.data.auth

/**
 * Handles account creation for Briar-only mode where no email is provided.
 */
interface BriarAccountRepository {
    /**
     * Creates a local Briar account using the provided display name and password.
     */
    suspend fun createAccount(name: String, password: String)

    /**
     * Signs into an existing Briar account.
     */
    suspend fun signIn(name: String, password: String)
}
