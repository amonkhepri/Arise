package com.example.rise.data.myaccount

import com.example.rise.auth.AuthenticationService
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.models.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseMyAccountRepository(
    private val authService: AuthenticationService,
    private val userRemoteDataSource: UserRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transportBridge: TransportRuntimeBridge,
) : MyAccountRepository {

    override suspend fun fetchCurrentUser(): User = withContext(ioDispatcher) {
        transportBridge.requireFirestore("FirebaseMyAccountRepository#fetchCurrentUser")
        val uid = authService.currentUser()?.id ?: throw IllegalStateException("UID is null.")
        userRemoteDataSource.fetchUser(uid) ?: throw IllegalStateException("User not found")
    }

    override suspend fun updateCurrentUser(name: String, bio: String) {
        withContext(ioDispatcher) {
            transportBridge.requireFirestore("FirebaseMyAccountRepository#updateCurrentUser")
            val uid = authService.currentUser()?.id ?: throw IllegalStateException("UID is null.")
            val updates = mutableMapOf<String, Any>()
            if (name.isNotBlank()) {
                updates["name"] = name
            }
            if (bio.isNotBlank()) {
                updates["bio"] = bio
            }
            if (updates.isEmpty()) return@withContext

            userRemoteDataSource.updateUser(uid, updates)
        }
    }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            authService.signOut()
        }
    }
}
