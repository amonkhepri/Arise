package com.example.rise.services

import com.example.rise.data.auth.SignInRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber


class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val signInRepository: SignInRepository by inject()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            try {
                signInRepository.storeMessagingToken(token)
            } catch (error: Throwable) {
                Timber.w(error, "Failed to store FCM token")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.notification != null) {
            //TODO: Show notification if we're not online
            Timber.tag("FCM").d(remoteMessage.data.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
