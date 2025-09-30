package com.example.rise.data.auth

import com.google.firebase.auth.FirebaseAuth

class FirebaseAuthStateProvider(private val firebaseAuth: FirebaseAuth) : AuthStateProvider {
    override fun isSignedIn(): Boolean = firebaseAuth.currentUser != null

    override fun currentUserId(): String? = firebaseAuth.currentUser?.uid

    override fun currentUserDisplayName(): String? = firebaseAuth.currentUser?.displayName
}
