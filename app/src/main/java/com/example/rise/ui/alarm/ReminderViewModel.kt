package com.example.rise.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.alarm.ReminderPreferences
import com.example.rise.helpers.MINUTE_SECONDS
import com.example.rise.models.Alarm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

class ReminderViewModel(
    private val preferences: ReminderPreferences,
) : ViewModel() {

    data class Args(
        val alarm: Alarm?,
        val isAlarmReminder: Boolean,
        val alarmLabelFallback: String,
        val timerLabel: String,
        val timerExpiredText: String,
        val formattedTimeProvider: () -> CharSequence,
    )

    data class UiState(
        val title: String = "",
        val message: CharSequence = "",
        val isAlarmReminder: Boolean = false,
        val soundUri: String? = null,
        val currentVolume: Float = 1f,
        val increaseVolumeGradually: Boolean = false,
        val isInitialized: Boolean = false,
    )

    sealed interface Event {
        object Finish : Event
        data class ScheduleSnooze(val alarm: Alarm, val seconds: Int) : Event
        data class ShowSnoozePicker(val defaultSeconds: Int, val alarm: Alarm) : Event
        data class UpdateVolume(val volume: Float) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var initialized = false
    private var alarm: Alarm? = null
    private var autoFinishJob: Job? = null
    private var volumeJob: Job? = null
    private var hasTerminated = false

    fun initialize(args: Args) {
        if (initialized) return
        initialized = true
        alarm = args.alarm

        val title = if (args.isAlarmReminder) {
            val label = args.alarm?.label.orEmpty()
            if (label.isBlank()) {
                args.alarmLabelFallback
            } else {
                label
            }
        } else {
            args.timerLabel
        }

        val message = if (args.isAlarmReminder) {
            args.formattedTimeProvider()
        } else {
            args.timerExpiredText
        }

        val shouldIncreaseVolume = args.isAlarmReminder && preferences.increaseVolumeGradually
        val soundUri = args.alarm?.soundUri ?: preferences.timerSoundUri
        val initialVolume = if (shouldIncreaseVolume) MIN_VOLUME else 1f

        _uiState.value = UiState(
            title = title,
            message = message,
            isAlarmReminder = args.isAlarmReminder,
            soundUri = soundUri,
            currentVolume = initialVolume,
            increaseVolumeGradually = shouldIncreaseVolume,
            isInitialized = true,
        )

        val maxDurationSeconds = if (args.isAlarmReminder) {
            preferences.alarmMaxReminderSeconds
        } else {
            preferences.timerMaxReminderSeconds
        }
        scheduleAutoFinish(maxDurationSeconds)

        if (shouldIncreaseVolume) {
            scheduleVolumeIncrease(initialVolume)
        }
    }

    fun onDismissRequested() {
        finishReminder()
    }

    fun onNewIntent() {
        finishReminder()
    }

    fun onSnoozeRequested() {
        val alarm = alarm ?: run {
            finishReminder()
            return
        }

        cancelJobs()
        if (preferences.useSameSnooze) {
            val seconds = preferences.snoozeMinutes * MINUTE_SECONDS
            sendScheduleSnooze(alarm, seconds)
        } else {
            val seconds = preferences.snoozeMinutes * MINUTE_SECONDS
            _events.tryEmit(Event.ShowSnoozePicker(seconds, alarm))
        }
    }

    fun onSnoozeDurationSelected(seconds: Int) {
        val alarm = alarm ?: return
        preferences.snoozeMinutes = seconds / MINUTE_SECONDS
        sendScheduleSnooze(alarm, seconds)
    }

    fun onSnoozePickerCancelled() {
        finishReminder()
    }

    private fun sendScheduleSnooze(alarm: Alarm, seconds: Int) {
        cancelJobs()
        hasTerminated = true
        _events.tryEmit(Event.ScheduleSnooze(alarm, seconds))
    }

    private fun finishReminder() {
        if (hasTerminated) return
        cancelJobs()
        hasTerminated = true
        _events.tryEmit(Event.Finish)
    }

    private fun scheduleAutoFinish(durationSeconds: Int) {
        if (durationSeconds <= 0) {
            finishReminder()
            return
        }

        autoFinishJob = viewModelScope.launch {
            delay(durationSeconds * 1000L)
            finishReminder()
        }
    }

    private fun scheduleVolumeIncrease(startVolume: Float) {
        volumeJob = viewModelScope.launch {
            var volume = startVolume
            while (volume < 1f && !hasTerminated) {
                delay(VOLUME_DELAY_MS)
                volume = min(1f, volume + VOLUME_STEP)
                _events.emit(Event.UpdateVolume(volume))
            }
        }
    }

    private fun cancelJobs() {
        autoFinishJob?.cancel()
        autoFinishJob = null
        volumeJob?.cancel()
        volumeJob = null
    }

    override fun onCleared() {
        cancelJobs()
        super.onCleared()
    }

    companion object {
        private const val VOLUME_STEP = 0.1f
        private const val MIN_VOLUME = 0.1f
        private const val VOLUME_DELAY_MS = 3000L
    }
}
