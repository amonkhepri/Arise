package com.example.rise.data.auth

interface SignInRepository {
    suspend fun ensureUserInitialized()
    suspend fun fetchMessagingToken(): String?
    suspend fun storeMessagingToken(token: String)
}
