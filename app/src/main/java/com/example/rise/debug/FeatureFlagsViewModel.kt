package com.example.rise.debug

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.R
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.featureflags.TransportModeProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FeatureFlagsViewModel(
    private val transportModeProvider: TransportModeProvider,
    private val telegramAuthFlagProvider: TelegramAuthFlagProvider,
) : ViewModel() {

    data class UiState(
        val mode: BriarTransportMode = BriarTransportMode.FIRESTORE,
        val telegramAuthEnabled: Boolean = false,
    )

    sealed interface Event {
        data class ShowMessageRes(@StringRes val messageRes: Int) : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                transportModeProvider.observeMode(),
                telegramAuthFlagProvider.observeEnabled(),
            ) { mode, telegramEnabled ->
                UiState(mode = mode, telegramAuthEnabled = telegramEnabled)
            }.collect { uiState ->
                _state.value = uiState
            }
        }
    }

    fun setMode(mode: BriarTransportMode) {
        if (mode == BriarTransportMode.FIRESTORE) {
            if (mode != _state.value.mode) {
                viewModelScope.launch { transportModeProvider.setMode(mode) }
            }
        } else {
            _events.tryEmit(Event.ShowMessageRes(R.string.feature_flags_transport_mode_stage0_warning))
        }
    }

    fun setTelegramEnabled(enabled: Boolean) {
        if (enabled == _state.value.telegramAuthEnabled) return
        viewModelScope.launch {
            telegramAuthFlagProvider.setEnabled(enabled)
        }
    }
}
