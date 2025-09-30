package com.example.rise.ui.mainActivity

import android.app.Activity
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.auth.SignInIntentProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainActivityViewModel(
    private val authStateProvider: AuthStateProvider,
    private val signInIntentProvider: SignInIntentProvider
) : BaseViewModel() {

    data class MainActivityUiState(
        val isSigningIn: Boolean = false,
        val isUserSignedIn: Boolean = false
    )

    sealed interface MainActivityEvent {
        data class LaunchSignIn(val intent: android.content.Intent) : MainActivityEvent
    }

    private val _uiState = MutableStateFlow(
        MainActivityUiState(isUserSignedIn = authStateProvider.isSignedIn())
    )
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainActivityEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun onStart() {
        ensureSignedIn()
    }

    fun onSignInResult(resultCode: Int) {
        _uiState.update { it.copy(isSigningIn = false) }
        if (resultCode == Activity.RESULT_OK) {
            _uiState.update { it.copy(isUserSignedIn = true) }
        } else {
            ensureSignedIn()
        }
    }

    private fun ensureSignedIn() {
        val signedIn = authStateProvider.isSignedIn()
        _uiState.update { it.copy(isUserSignedIn = signedIn) }
        if (!signedIn && !_uiState.value.isSigningIn) {
            launchSignIn()
        }
    }

    private fun launchSignIn() {
        val intent = signInIntentProvider.createSignInIntent()
        val emitted = _events.tryEmit(MainActivityEvent.LaunchSignIn(intent))
        if (emitted) {
            _uiState.update { it.copy(isSigningIn = true) }
        }
    }
}
