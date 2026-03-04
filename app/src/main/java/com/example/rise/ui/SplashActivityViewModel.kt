package com.example.rise.ui

import androidx.lifecycle.ViewModel
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.ui.mainActivity.BriarInvitationDeepLinkEntrypoint
import com.example.rise.ui.mainActivity.BriarInvitationOnboardingAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SplashActivityViewModel(
    private val authStateProvider: AuthStateProvider,
    private val invitationDeepLinkEntrypoint: BriarInvitationDeepLinkEntrypoint = BriarInvitationDeepLinkEntrypoint(),
) : ViewModel() {

    sealed interface NavigationEvent {
        data object ToSignIn : NavigationEvent
        data class ToMain(
            val onboardingAction: BriarInvitationOnboardingAction? = null,
        ) : NavigationEvent
    }

    private val _events = MutableSharedFlow<NavigationEvent>(replay = 1)
    val events = _events.asSharedFlow()

    fun determineDestination(rawDeepLink: String? = null) {
        val onboardingAction = invitationDeepLinkEntrypoint.resolve(rawDeepLink)
        if (onboardingAction != null) {
            _events.tryEmit(NavigationEvent.ToMain(onboardingAction = onboardingAction))
            return
        }

        val event = if (authStateProvider.isSignedIn()) {
            NavigationEvent.ToMain()
        } else {
            NavigationEvent.ToSignIn
        }
        _events.tryEmit(event)
    }
}
