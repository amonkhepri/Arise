package com.example.rise.data.firestore

import com.example.rise.models.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    override suspend fun setUser(userId: String, user: User) {
        firestore.collection("users").document(userId).set(user).await()
    }

    override fun observeUsers(): Flow<List<UserRemoteDataSource.UserSnapshot>> = callbackFlow {
        val registration = firestore.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val entries = snapshot?.documents.orEmpty().mapNotNull { document ->
                val user = document.toObject(User::class.java) ?: return@mapNotNull null
                UserRemoteDataSource.UserSnapshot(
                    id = document.id,
                    user = user,
                )
            }
            trySend(entries)
        }
        awaitClose { registration.remove() }
    }
}
