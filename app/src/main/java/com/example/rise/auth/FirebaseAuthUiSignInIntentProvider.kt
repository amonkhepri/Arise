package com.example.rise.auth

import android.content.Intent
import com.firebase.ui.auth.AuthUI

class FirebaseAuthUiSignInIntentProvider(
    private val authUI: AuthUI,
    private val providers: List<AuthUI.IdpConfig> = listOf(AuthUI.IdpConfig.EmailBuilder().build()),
    private val isSmartLockEnabled: Boolean = false
) : SignInIntentProvider {

    override fun createSignInIntent(): Intent {
        return authUI.createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(isSmartLockEnabled)
            .build()
    }
}
