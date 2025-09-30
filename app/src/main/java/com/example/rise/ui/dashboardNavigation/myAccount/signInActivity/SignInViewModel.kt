package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.auth.SignInIntentProvider
import com.example.rise.data.auth.SignInRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInViewModel(
    private val signInIntentProvider: SignInIntentProvider,
    private val repository: SignInRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
    )

    sealed interface Event {
        data class LaunchSignIn(val intent: Intent) : Event
        data class ShowMessage(val message: String) : Event
        object NavigateToMain : Event
    }

    sealed interface SignInFailure {
        object Cancelled : SignInFailure
        object NoNetwork : SignInFailure
        data class Unknown(val message: String?) : SignInFailure
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun onSignInClicked() {
        val intent = signInIntentProvider.createSignInIntent()
        _events.tryEmit(Event.LaunchSignIn(intent))
    }

    fun onSignInSuccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.ensureUserInitialized()
                try {
                    val token = repository.fetchMessagingToken()
                    if (!token.isNullOrBlank()) {
                        repository.storeMessagingToken(token)
                    }
                } catch (tokenError: Exception) {
                    _events.emit(Event.ShowMessage("Failed to register for notifications"))
                }
                _events.emit(Event.NavigateToMain)
            } catch (error: Exception) {
                _events.emit(Event.ShowMessage(error.message ?: "Failed to finish sign-in"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSignInFailure(failure: SignInFailure) {
        val message = when (failure) {
            SignInFailure.Cancelled -> null
            SignInFailure.NoNetwork -> "No network"
            is SignInFailure.Unknown -> failure.message ?: "Unknown error"
        }
        if (message != null) {
            _events.tryEmit(Event.ShowMessage(message))
        }
    }
}
