package com.example.rise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.ui.mainActivity.BriarInvitationDeepLinkEntrypoint
import com.example.rise.ui.mainActivity.BriarInvitationOnboardingAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SplashActivityViewModel(
    private val authStateProvider: AuthStateProvider,
    private val briarRuntimeManager: BriarRuntimeManager = NoOpBriarRuntimeManager,
    private val invitationDeepLinkEntrypoint: BriarInvitationDeepLinkEntrypoint = BriarInvitationDeepLinkEntrypoint(),
) : ViewModel() {

    sealed interface NavigationEvent {
        data class ToSignIn(
            val onboardingAction: BriarInvitationOnboardingAction? = null,
        ) : NavigationEvent
        data class ToMain(
            val onboardingAction: BriarInvitationOnboardingAction? = null,
        ) : NavigationEvent
    }

    private val _events = MutableSharedFlow<NavigationEvent>(replay = 1)
    val events = _events.asSharedFlow()

    fun determineDestination(rawDeepLink: String? = null) {
        val onboardingAction = invitationDeepLinkEntrypoint.resolve(rawDeepLink)
        val signedIn = authStateProvider.isSignedIn()
        if (signedIn) {
            _events.tryEmit(NavigationEvent.ToMain(onboardingAction = onboardingAction))
            return
        }
        val cachedLocalIdentityPresent =
            authStateProvider.currentUserId() != null || authStateProvider.currentUserDisplayName() != null
        if (!cachedLocalIdentityPresent) {
            _events.tryEmit(NavigationEvent.ToSignIn(onboardingAction = onboardingAction))
            return
        }
        viewModelScope.launch {
            runCatching { briarRuntimeManager.ensureStarted() }
            val event = if (briarRuntimeManager.status.value.hasPersistedAccount) {
                NavigationEvent.ToMain(onboardingAction = onboardingAction)
            } else {
                NavigationEvent.ToSignIn(onboardingAction = onboardingAction)
            }
            _events.emit(event)
        }
    }
}

private object NoOpBriarRuntimeManager : BriarRuntimeManager {
    private val statusFlow = MutableStateFlow(BriarRuntimeStatus.stopped)
    private val diagnosticsFlow = MutableSharedFlow<BriarRuntimeEvent>()
    private val chatGatewayFlow = MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway)
    private val contactServiceFlow = MutableStateFlow<BriarContactService>(NoOpBriarContactService)

    override val status: StateFlow<BriarRuntimeStatus> = statusFlow
    override val diagnostics: SharedFlow<BriarRuntimeEvent> = diagnosticsFlow
    override val chatGateway: StateFlow<BriarChatGateway> = chatGatewayFlow
    override val contactService: StateFlow<BriarContactService> = contactServiceFlow

    override suspend fun ensureStarted() = Unit

    override suspend fun createAccount(name: String, password: String): Boolean = true

    override suspend fun signIn(password: String): Boolean = true

    override suspend fun stop() = Unit
}
