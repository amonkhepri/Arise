package com.example.rise.data.firestore

import com.example.rise.models.User

interface UserRemoteDataSource {
    suspend fun fetchUser(userId: String): User?
    suspend fun updateUser(userId: String, updates: Map<String, Any>)
}
