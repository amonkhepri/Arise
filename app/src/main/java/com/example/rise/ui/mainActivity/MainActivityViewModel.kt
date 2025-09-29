package com.example.rise.ui.mainActivity

import android.content.Intent
import com.example.rise.auth.AuthStateProvider
import com.example.rise.auth.SignInIntentProvider
import com.example.rise.baseclasses.BaseViewModel

class MainActivityViewModel(
    private val authStateProvider: AuthStateProvider,
    private val signInIntentProvider: SignInIntentProvider
) : BaseViewModel() {

    private var signingIn: Boolean = false

    fun requestSignInIfNeeded(): Intent? {
        if (!signingIn && !authStateProvider.isUserSignedIn()) {
            signingIn = true
            return signInIntentProvider.createSignInIntent()
        }
        return null
    }

    fun requestRetrySignIn(isResultSuccessful: Boolean): Intent? {
        signingIn = false
        if (!isResultSuccessful && !authStateProvider.isUserSignedIn()) {
            signingIn = true
            return signInIntentProvider.createSignInIntent()
        }
        return null
    }

}