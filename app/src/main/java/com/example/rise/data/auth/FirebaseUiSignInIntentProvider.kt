package com.example.rise.data.auth

import android.content.Intent
import com.example.rise.R
import com.firebase.ui.auth.AuthUI

class FirebaseUiSignInIntentProvider(private val authUi: AuthUI) : SignInIntentProvider {
    override fun createSignInIntent(): Intent {
        return authUi.createSignInIntentBuilder()
            .setAvailableProviders(
                listOf(
                    AuthUI.IdpConfig.EmailBuilder()
                        .setAllowNewAccounts(true)
                        .setRequireName(true)
                        .build(),
                ),
            )
            .setLogo(R.drawable.ic_fire_emoji)
            .setIsSmartLockEnabled(false)
            .build()
    }
}
