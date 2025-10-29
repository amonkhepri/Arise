package com.example.rise.auth

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthenticationService(
    private val firebaseAuth: FirebaseAuth,
) : AuthenticationService {

    override fun currentUser(): AuthenticationService.User? = firebaseAuth.currentUser?.toAuthUser()

    override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle {
        val firebaseListener = FirebaseAuth.AuthStateListener { auth ->
            listener(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(firebaseListener)
        return AuthStateHandle { firebaseAuth.removeAuthStateListener(firebaseListener) }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun createUserWithEmail(email: String, password: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await()
    }

    override suspend fun signInWithCustomToken(customToken: String) {
        firebaseAuth.signInWithCustomToken(customToken).await()
    }

    override suspend fun updateProfile(displayName: String?, photoUrl: Uri?) {
        val user = firebaseAuth.currentUser ?: return
        val builder = UserProfileChangeRequest.Builder()
        if (!displayName.isNullOrEmpty()) {
            builder.setDisplayName(displayName)
        }
        if (photoUrl != null) {
            builder.setPhotoUri(photoUrl)
        }
        val request = builder.build()
        if (request.displayName != null || request.photoUri != null) {
            user.updateProfile(request).await()
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toAuthUser(): AuthenticationService.User {
        return AuthenticationService.User(
            id = uid,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl
        )
    }
}
