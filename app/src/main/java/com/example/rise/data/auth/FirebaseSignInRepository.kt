package com.example.rise.data.auth

import com.example.rise.services.MyFirebaseMessagingService
import com.example.rise.util.FirestoreUtil
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class FirebaseSignInRepository(
    private val messaging: FirebaseMessaging,
) : SignInRepository {

    override suspend fun ensureUserInitialized() = suspendCancellableCoroutine { continuation ->
        FirestoreUtil.initCurrentUserIfFirstTime {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    override suspend fun fetchMessagingToken(): String? = try {
        messaging.token.await()
    } catch (error: Exception) {
        null
    }

    override suspend fun storeMessagingToken(token: String) {
        MyFirebaseMessagingService.addTokenToFirestore(token)
    }
}
