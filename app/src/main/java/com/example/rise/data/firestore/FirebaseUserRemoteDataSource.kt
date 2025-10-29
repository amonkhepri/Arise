package com.example.rise.data.firestore

import com.example.rise.models.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseUserRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : UserRemoteDataSource {

    override suspend fun fetchUser(userId: String): User? {
        return firestore.collection("users").document(userId).get().await()
            .toObject(User::class.java)
    }

    override suspend fun updateUser(userId: String, updates: Map<String, Any>) {
        firestore.collection("users").document(userId).update(updates).await()
    }
}
