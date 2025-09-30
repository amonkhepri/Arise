package com.example.rise.ui.dashboardNavigation.dashboard

import androidx.lifecycle.viewModelScope
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: AlarmRepository,
    private val auth: FirebaseAuth,
    private val clock: Clock = Clock.systemDefaultZone()
) : BaseViewModel() {

    data class DashboardUiState(
        val alarmQuery: Query? = null,
        val activeUserId: String? = null,
        val chatChannel: String = "",
        val pendingMessage: TextMessage? = null,
        val isLoading: Boolean = true,
        val errorMessage: String? = null
    )

    sealed interface DashboardEvent {
        data class ScheduleAlarm(val alarm: Alarm) : DashboardEvent
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DashboardEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun initialise(
        byBottomNavigation: Boolean,
        explicitUserId: String?,
        chatChannel: String?,
        message: TextMessage?
    ) {
        val resolvedUserId = if (!byBottomNavigation && !explicitUserId.isNullOrBlank()) {
            explicitUserId
        } else {
            auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in")
        }
        val query = repository.alarmsQuery(resolvedUserId)
        _uiState.value = DashboardUiState(
            alarmQuery = query,
            activeUserId = resolvedUserId,
            chatChannel = chatChannel.orEmpty(),
            pendingMessage = message,
            isLoading = false,
            errorMessage = null
        )
    }

    fun createAlarm(timeInMillis: Long, messageOverride: TextMessage? = null) {
        val state = _uiState.value
        val userId = state.activeUserId ?: return
        val message = messageOverride ?: state.pendingMessage
        val alarm = Alarm(
            idTimeStamp = Instant.now(clock).toEpochMilli().toInt(),
            timeInMiliseconds = timeInMillis,
            userName = auth.currentUser?.displayName.orEmpty(),
            chatChannel = state.chatChannel,
            messsage = message
        )
        viewModelScope.launch {
            try {
                repository.saveAlarm(userId, alarm)
                if (alarm.messsage != null) {
                    _events.emit(DashboardEvent.ScheduleAlarm(alarm))
                }
            } catch (error: Throwable) {
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }
}
