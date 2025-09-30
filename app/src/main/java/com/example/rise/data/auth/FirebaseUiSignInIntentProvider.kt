package com.example.rise.data.auth

import android.content.Intent
import com.firebase.ui.auth.AuthUI

class FirebaseUiSignInIntentProvider(private val authUi: AuthUI) : SignInIntentProvider {
    override fun createSignInIntent(): Intent {
        return authUi.createSignInIntentBuilder()
            .setAvailableProviders(listOf(AuthUI.IdpConfig.EmailBuilder().build()))
            .setIsSmartLockEnabled(false)
            .build()
    }
}
