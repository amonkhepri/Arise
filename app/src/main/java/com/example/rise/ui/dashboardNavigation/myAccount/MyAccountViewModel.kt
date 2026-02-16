package com.example.rise.ui.dashboardNavigation.myAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.myaccount.MyAccountRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyAccountViewModel(
    private val repository: MyAccountRepository,
) : ViewModel() {

    data class UiState(
        val name: String = "",
        val bio: String = "",
        val profilePicturePath: String? = null,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data class ShowMessage(val message: String) : Event
        object NavigateToSignIn : Event
    }

    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = repository.fetchCurrentUser()
                _uiState.update {
                    it.copy(
                        name = user.name,
                        bio = user.bio,
                        profilePicturePath = user.profilePicturePath,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                _events.tryEmit(Event.ShowMessage(error.message ?: "Failed to load profile"))
            }
        }
    }

    fun updateProfile(name: String, bio: String, profilePicturePath: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                repository.updateCurrentUser(name, bio, profilePicturePath)
                _uiState.update {
                    it.copy(
                        name = name,
                        bio = bio,
                        profilePicturePath = profilePicturePath ?: it.profilePicturePath,
                        isSaving = false,
                        errorMessage = null,
                    )
                }
                _events.tryEmit(Event.ShowMessage("saving"))
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                _events.tryEmit(Event.ShowMessage(error.message ?: "Failed to save profile"))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            var errorMessage: String?
            try {
                _uiState.update { it.copy(isLoading = true) }
                _events.tryEmit(Event.ShowMessage("Signing out..."))
                repository.signOut()
                _events.tryEmit(Event.NavigateToSignIn)
            } catch (error: Exception) {
                errorMessage = error.message ?: "Failed to sign out"
                _events.tryEmit(Event.ShowMessage(errorMessage))
            } finally {
                _uiState.update { UiState() }
            }
        }
    }
}
