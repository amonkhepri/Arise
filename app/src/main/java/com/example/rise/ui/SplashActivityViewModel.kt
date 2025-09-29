package com.example.rise.ui

import com.example.rise.auth.AuthStateProvider
import com.example.rise.baseclasses.BaseViewModel

class SplashActivityViewModel(
    private val authStateProvider: AuthStateProvider
) : BaseViewModel() {

    enum class Destination {
        SIGN_IN,
        MAIN
    }

    fun resolveDestination(): Destination {
        return if (authStateProvider.isUserSignedIn()) {
            Destination.MAIN
        } else {
            Destination.SIGN_IN
        }
    }

}