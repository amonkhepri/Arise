package com.example.rise.ui

import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.data.auth.AuthStateProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SplashActivityViewModel(
    private val authStateProvider: AuthStateProvider
) : BaseViewModel() {

    sealed interface NavigationEvent {
        data object ToSignIn : NavigationEvent
        data object ToMain : NavigationEvent
    }

    private val _events = MutableSharedFlow<NavigationEvent>(replay = 1)
    val events = _events.asSharedFlow()

    fun determineDestination() {
        val event = if (authStateProvider.isSignedIn()) {
            NavigationEvent.ToMain
        } else {
            NavigationEvent.ToSignIn
        }
        _events.tryEmit(event)
    }
}
