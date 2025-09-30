package com.example.rise.data.myaccount

import com.example.rise.models.User

interface MyAccountRepository {
    suspend fun fetchCurrentUser(): User
    suspend fun updateCurrentUser(name: String, bio: String)
    suspend fun signOut()
}
