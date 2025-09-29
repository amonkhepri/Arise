package com.example.rise.auth

import com.google.firebase.auth.FirebaseAuth

class FirebaseUserSessionProvider(
    private val firebaseAuth: FirebaseAuth
) : UserSessionProvider {

    override fun currentUserId(): String =
        firebaseAuth.currentUser?.uid ?: throw IllegalStateException("UID is null.")

    override fun currentUserName(): String = firebaseAuth.currentUser?.displayName.orEmpty()
}
