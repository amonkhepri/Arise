package com.example.rise.auth

import com.google.firebase.auth.FirebaseAuth

class FirebaseAuthStateProvider(
    private val firebaseAuth: FirebaseAuth
) : AuthStateProvider {

    override fun isUserSignedIn(): Boolean = firebaseAuth.currentUser != null
}
