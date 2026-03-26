package com.example.rise.debug

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.R
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.featureflags.TransportModeProvider
import com.example.rise.transport.router.BridgeOrchestrator
import com.example.rise.transport.router.ConnectorHealth
import com.example.rise.transport.router.ConnectorHealthProvider
import com.example.rise.transport.router.PrimaryRoutingSnapshot
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
    private val bridgeOrchestrator: BridgeOrchestrator,
    private val connectorHealthProvider: ConnectorHealthProvider,
) : ViewModel() {

    data class UiState(
        val mode: BriarTransportMode = BriarTransportMode.FIRESTORE,
        val telegramAuthEnabled: Boolean = false,
        val routingSnapshot: PrimaryRoutingSnapshot? = null,
        val connectorHealth: List<ConnectorHealth> = emptyList(),
    )

    sealed interface Event {
        data class ShowMessageRes(@StringRes val messageRes: Int) : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    private var pendingModeOverride: BriarTransportMode? = null

    init {
        viewModelScope.launch {
            combine(
                transportModeProvider.observeMode(),
                telegramAuthFlagProvider.observeEnabled(),
                bridgeOrchestrator.routingState,
                connectorHealthProvider.health,
            ) { observedMode, telegramEnabled, routing, health ->
                val effectiveMode = pendingModeOverride ?: observedMode
                if (pendingModeOverride != null && observedMode == pendingModeOverride) {
                    pendingModeOverride = null
                }
                UiState(
                    mode = effectiveMode,
                    telegramAuthEnabled = telegramEnabled,
                    routingSnapshot = routing,
                    connectorHealth = health.values.sortedBy { it.transport.name },
                )
            }.collect { uiState -> _state.value = uiState }
        }
    }

    fun setMode(mode: BriarTransportMode) {
        if (mode == _state.value.mode) return

        if (mode == BriarTransportMode.FIRESTORE) {
            pendingModeOverride = mode
            _state.value = _state.value.copy(mode = mode)
            viewModelScope.launch { transportModeProvider.setMode(mode) }
            return
        }

        // Stage 1 allows Hybrid/BRIAR_ONLY toggles, but we still surface a warning so testers
        // know the experience is experimental.
        _events.tryEmit(Event.ShowMessageRes(R.string.feature_flags_transport_mode_stage0_warning))
        pendingModeOverride = mode
        _state.value = _state.value.copy(mode = mode)
        viewModelScope.launch { transportModeProvider.setMode(mode) }
    }

    fun setTelegramEnabled(enabled: Boolean) {
        if (enabled == _state.value.telegramAuthEnabled) return
        viewModelScope.launch {
            telegramAuthFlagProvider.setEnabled(enabled)
        }
    }
}
