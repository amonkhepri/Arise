package com.example.rise.services

import com.example.rise.util.FirestoreUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber


class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        fun addTokenToFirestore(newRegistrationToken: String?) {
            if (newRegistrationToken == null) throw NullPointerException("FCM token is null.")

            FirestoreUtil.getFCMRegistrationTokens { tokens ->
                if (tokens.contains(newRegistrationToken))
                    return@getFCMRegistrationTokens

                tokens.add(newRegistrationToken)
                FirestoreUtil.setFCMRegistrationTokens(tokens)
            }
        }
    }

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)

        val newRegistrationToken = FirebaseInstanceId.getInstance().token
        if (FirebaseAuth.getInstance().currentUser != null)
            addTokenToFirestore(
                newRegistrationToken
            )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.notification != null) {
            //TODO: Show notification if we're not online
            Timber.tag("FCM").d(remoteMessage.data.toString())
        }
    }
}