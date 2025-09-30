package com.example.rise.data.auth

interface AuthStateProvider {
    fun isSignedIn(): Boolean

    fun currentUserId(): String?

    fun currentUserDisplayName(): String?
}
