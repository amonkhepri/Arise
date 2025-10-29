package com.example.rise.ui.signInActivity

import android.net.Uri
import android.util.Patterns
import androidx.credentials.Credential
import androidx.credentials.PasswordCredential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.auth.TelegramAuthData
import com.example.rise.data.auth.TelegramAuthRepository
import com.example.rise.data.auth.TelegramAuthResponse
import com.example.rise.auth.AuthenticationService
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.core.net.toUri

class SignInViewModel(
    private val authenticationService: AuthenticationService,
    private val repository: SignInRepository,
    private val telegramRepository: TelegramAuthRepository,
    private val emailValidator: EmailValidator = DefaultEmailValidator,
) : ViewModel() {

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
    }
    enum class Mode { SignIn, Register }

    data class UiState(
        val isLoading: Boolean = false,
        val mode: Mode = Mode.SignIn,
    )

    sealed interface Event {
        data class ShowMessage(val message: String) : Event
        data object NavigateToMain : Event
        data class SaveCredentials(val email: String, val password: String) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun toggleMode() {
        _uiState.update { state ->
            val nextMode = if (state.mode == Mode.SignIn) Mode.Register else Mode.SignIn
            state.copy(mode = nextMode)
        }
    }

    fun submitPrimaryAction(name: String, email: String, password: String) {
        when (_uiState.value.mode) {
            Mode.SignIn -> signInWithEmail(email, password)
            Mode.Register -> register(name, email, password)
        }
    }

    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (!isValidEmail(trimmedEmail)) {
            emitMessage("Please enter a valid email")
            return
        }
        if (password.isBlank()) {
            emitMessage("Please enter your password")
            return
        }
        viewModelScope.launch {
            setLoading(true)
            try {
                authenticationService.signInWithEmail(trimmedEmail, password)
                _events.emit(Event.SaveCredentials(trimmedEmail, password))
                finalizeSignIn()
            } catch (error: Exception) {
                handleAuthError(error)
            } finally {
                setLoading(false)
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            emitMessage("Please enter your name")
            return
        }
        if (!isValidEmail(trimmedEmail)) {
            emitMessage("Please enter a valid email")
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            emitMessage("Password must be at least $MIN_PASSWORD_LENGTH characters")
            return
        }
        viewModelScope.launch {
            setLoading(true)
            try {
                authenticationService.createUserWithEmail(trimmedEmail, password)
                authenticationService.updateProfile(trimmedName, null)
                _events.emit(Event.SaveCredentials(trimmedEmail, password))
                finalizeSignIn()
            } catch (error: Exception) {
                handleAuthError(error)
            } finally {
                setLoading(false)
            }
        }
    }

    fun onCredentialReceived(credential: Credential) {
        when (credential) {
            is PasswordCredential -> signInWithPasswordCredential(credential)
            else -> emitMessage("Unsupported credential type")
        }
    }

    fun onCredentialError(error: Exception) {
        val message = error.message ?: "Unable to use saved credentials"
        emitMessage(message)
    }

    fun showMessage(message: String) {
        emitMessage(message)
    }

    fun signInWithTelegram(authData: TelegramAuthData) {
        viewModelScope.launch {
            setLoading(true)
            try {
                val response = telegramRepository.exchange(authData)
                // The backend still issues Firebase custom tokens, so we complete the flow by authenticating with Firebase before continuing the app sign-in pipeline.
                authenticationService.signInWithCustomToken(response.customToken)
                updateProfileIfNeeded(response)
                finalizeSignIn()
            } catch (error: Exception) {
                when (error) {
                    is FirebaseAuthException -> handleAuthError(error)
                    else -> emitMessage(error.message ?: "Failed to sign in with Telegram")
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun signInWithPasswordCredential(credential: PasswordCredential) {
        viewModelScope.launch {
            setLoading(true)
            try {
                authenticationService.signInWithEmail(credential.id, credential.password)
                finalizeSignIn()
            } catch (error: Exception) {
                handleAuthError(error)
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun finalizeSignIn() {
        val initialization = runCatching { repository.ensureUserInitialized() }
        when {
            initialization.isFailure -> {
                val message = initialization.exceptionOrNull()?.message ?: "Failed to finish sign-in"
                _events.emit(Event.ShowMessage(message))
            }
            else -> {
                val tokenResult = runCatching { repository.fetchMessagingToken() }
                val messagingToken = tokenResult.getOrNull()
                when {
                    tokenResult.isFailure -> _events.emit(Event.ShowMessage("Failed to register for notifications"))
                    messagingToken.isNullOrBlank() -> _events.emit(Event.NavigateToMain)
                    else -> {
                        val storeResult = runCatching { repository.storeMessagingToken(messagingToken) }
                        when {
                            storeResult.isFailure -> {
                                val message = storeResult.exceptionOrNull()?.message ?: "Failed to finish sign-in"
                                _events.emit(Event.ShowMessage(message))
                            }
                            else -> _events.emit(Event.NavigateToMain)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleAuthError(error: Exception) {
        val message = when (error) {
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
            is FirebaseAuthWeakPasswordException -> error.reason ?: "Password is too weak"
            is FirebaseAuthUserCollisionException -> "An account already exists with this email"
            is FirebaseAuthException -> error.localizedMessage ?: "Authentication failed"
            else -> error.localizedMessage ?: "Authentication failed"
        }
        _events.emit(Event.ShowMessage(message))
    }

    private suspend fun updateProfileIfNeeded(response: TelegramAuthResponse) {
        val currentUser = authenticationService.currentUser() ?: return
        var needsUpdate = false
        var displayNameToApply: String? = null
        if (!response.displayName.isNullOrBlank() && currentUser.displayName.isNullOrBlank()) {
            displayNameToApply = response.displayName
            needsUpdate = true
        }

        var photoUri: Uri? = null
        if (!response.photoUrl.isNullOrBlank()) {
            photoUri = runCatching { response.photoUrl.toUri() }.getOrNull()
            if (photoUri != null && currentUser.photoUrl != photoUri) {
                needsUpdate = true
            }
        }

        if (needsUpdate) {
            runCatching { authenticationService.updateProfile(displayNameToApply, photoUri) }
        }
    }

    private fun emitMessage(message: String) {
        _events.tryEmit(Event.ShowMessage(message))
    }

    private fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    private fun isValidEmail(email: String): Boolean = emailValidator.isValid(email)

    fun interface EmailValidator {
        fun isValid(email: String): Boolean
    }

    object DefaultEmailValidator : EmailValidator {
        override fun isValid(email: String): Boolean =
            email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
