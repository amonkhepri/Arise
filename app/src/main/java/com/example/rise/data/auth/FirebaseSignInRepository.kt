package com.example.rise.data.auth

import com.example.rise.services.MyFirebaseMessagingService
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.util.FirestoreUtil
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class FirebaseSignInRepository(
    private val messaging: FirebaseMessaging,
    private val transportBridge: TransportRuntimeBridge,
) : SignInRepository {

    override suspend fun ensureUserInitialized() = suspendCancellableCoroutine { continuation ->
        transportBridge.requireFirestore("FirebaseSignInRepository#ensureUserInitialized")
        FirestoreUtil.initCurrentUserIfFirstTime {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    override suspend fun fetchMessagingToken(): String? = try {
        transportBridge.requireFirestore("FirebaseSignInRepository#fetchMessagingToken")
        messaging.token.await()
    } catch (error: Exception) {
        null
    }

    override suspend fun storeMessagingToken(token: String) {
        transportBridge.requireFirestore("FirebaseSignInRepository#storeMessagingToken")
        MyFirebaseMessagingService.addTokenToFirestore(token)
    }
}
