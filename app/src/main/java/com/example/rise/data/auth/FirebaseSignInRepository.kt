package com.example.rise.data.auth

import com.example.rise.auth.AuthenticationService
import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseSignInRepository(
    private val messaging: FirebaseMessaging,
    private val transportBridge: TransportRuntimeBridge,
    private val authService: AuthenticationService,
    private val userRemoteDataSource: UserRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SignInRepository {

    override suspend fun ensureUserInitialized() = withContext(ioDispatcher) {
        transportBridge.requireFirestore("FirebaseSignInRepository#ensureUserInitialized")
        val authUser = authService.currentUser() ?: throw IllegalStateException("UID is null.")
        val uid = authUser.id
        val existing = userRemoteDataSource.fetchUser(uid)
        if (existing == null) {
            val newUser = User(
                name = authUser.displayName.orEmpty(),
                bio = "",
                profilePicturePath = null,
                registrationTokens = mutableListOf(),
            )
            userRemoteDataSource.setUser(uid, newUser)
        }
    }

    override suspend fun fetchMessagingToken(): String? = try {
        transportBridge.requireFirestore("FirebaseSignInRepository#fetchMessagingToken")
        messaging.token.await()
    } catch (_: Exception) {
        null
    }

    override suspend fun storeMessagingToken(token: String) = withContext(ioDispatcher) {
        transportBridge.requireFirestore("FirebaseSignInRepository#storeMessagingToken")
        val authUser = authService.currentUser() ?: return@withContext
        val uid = authUser.id
        val existing = userRemoteDataSource.fetchUser(uid)
        val tokens = (existing?.registrationTokens ?: mutableListOf()).toMutableList()
        if (tokens.contains(token)) {
            return@withContext
        }
        tokens.add(token)
        if (existing == null) {
            val newUser = User(
                name = authUser.displayName.orEmpty(),
                bio = "",
                profilePicturePath = null,
                registrationTokens = tokens,
            )
            userRemoteDataSource.setUser(uid, newUser)
        } else {
            userRemoteDataSource.updateUser(uid, mapOf("registrationTokens" to tokens))
        }
    }
}
