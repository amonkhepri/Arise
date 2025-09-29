package com.example.rise.auth

interface UserSessionProvider {
    fun currentUserId(): String
    fun currentUserName(): String
}
