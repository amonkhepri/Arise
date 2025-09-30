package com.example.rise.data.myaccount

import android.content.Context
import com.example.rise.models.User
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseMyAccountRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authUI: AuthUI,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MyAccountRepository {

    override suspend fun fetchCurrentUser(): User = withContext(ioDispatcher) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("UID is null.")
        val snapshot = firestore.collection("users").document(uid).get().await()
        snapshot.toObject(User::class.java) ?: throw IllegalStateException("User not found")
    }

    override suspend fun updateCurrentUser(name: String, bio: String) {
        withContext(ioDispatcher) {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("UID is null.")
            val updates = mutableMapOf<String, Any>()
            if (name.isNotBlank()) {
                updates["name"] = name
            }
            if (bio.isNotBlank()) {
                updates["bio"] = bio
            }
            if (updates.isEmpty()) return@withContext

            firestore.collection("users").document(uid).update(updates).await()
        }
    }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            authUI.signOut(context).await()
        }
    }
}
