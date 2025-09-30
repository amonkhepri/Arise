package com.example.rise.ui.alarm

import app.cash.turbine.test
import com.example.rise.data.alarm.ReminderPreferences
import com.example.rise.models.Alarm
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val defaultAlarm = Alarm(label = "", soundUri = "content://alarm")

    @Test
    fun `initialize with alarm populates ui state`() = runTest {
        val preferences = FakeReminderPreferences(increaseVolumeGradually = true)
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = defaultAlarm.copy(label = "Morning"),
                isAlarmReminder = true,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "10:00" },
            ),
        )

        val state = viewModel.uiState.value
        assertEquals("Morning", state.title)
        assertEquals("10:00", state.message)
        assertTrue(state.isAlarmReminder)
        assertTrue(state.increaseVolumeGradually)
        assertEquals(0.1f, state.currentVolume, 0.001f)
    }

    @Test
    fun `initialize without alarm uses timer strings`() = runTest {
        val preferences = FakeReminderPreferences()
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = null,
                isAlarmReminder = false,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "Ignored" },
            ),
        )

        val state = viewModel.uiState.value
        assertEquals("Timer", state.title)
        assertEquals("Expired", state.message)
        assertFalse(state.isAlarmReminder)
        assertEquals(1f, state.currentVolume, 0.001f)
    }

    @Test
    fun `auto finish emits finish event after duration`() = runTest {
        val preferences = FakeReminderPreferences(alarmMaxReminderSeconds = 1)
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = defaultAlarm,
                isAlarmReminder = true,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "10:00" },
            ),
        )

        viewModel.events.test {
            advanceTimeBy(1_000)
            assertEquals(ReminderViewModel.Event.Finish, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snooze requested with same snooze emits schedule event`() = runTest {
        val preferences = FakeReminderPreferences(useSameSnooze = true, snoozeMinutes = 5)
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = defaultAlarm,
                isAlarmReminder = true,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "10:00" },
            ),
        )

        viewModel.events.test {
            viewModel.onSnoozeRequested()
            val event = awaitItem() as ReminderViewModel.Event.ScheduleSnooze
            assertEquals(5 * 60, event.seconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snooze requested without same snooze shows picker`() = runTest {
        val preferences = FakeReminderPreferences(useSameSnooze = false, snoozeMinutes = 3)
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = defaultAlarm,
                isAlarmReminder = true,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "10:00" },
            ),
        )

        viewModel.events.test {
            viewModel.onSnoozeRequested()
            val event = awaitItem() as ReminderViewModel.Event.ShowSnoozePicker
            assertEquals(3 * 60, event.defaultSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snooze duration selection stores preference and schedules`() = runTest {
        val preferences = FakeReminderPreferences(useSameSnooze = false, snoozeMinutes = 3)
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = defaultAlarm,
                isAlarmReminder = true,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "10:00" },
            ),
        )

        viewModel.events.test {
            viewModel.onSnoozeDurationSelected(600)
            val event = awaitItem() as ReminderViewModel.Event.ScheduleSnooze
            assertEquals(600, event.seconds)
            assertEquals(10, preferences.snoozeMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `volume increase emits updates until max`() = runTest {
        val preferences = FakeReminderPreferences(increaseVolumeGradually = true)
        val viewModel = ReminderViewModel(preferences)

        viewModel.initialize(
            ReminderViewModel.Args(
                alarm = defaultAlarm,
                isAlarmReminder = true,
                alarmLabelFallback = "Alarm",
                timerLabel = "Timer",
                timerExpiredText = "Expired",
                formattedTimeProvider = { "10:00" },
            ),
        )

        viewModel.events.test {
            advanceTimeBy(3_000)
            val update = awaitItem() as ReminderViewModel.Event.UpdateVolume
            assertEquals(0.2f, update.volume, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeReminderPreferences(
        override val alarmMaxReminderSeconds: Int = 30,
        override val timerMaxReminderSeconds: Int = 60,
        override val increaseVolumeGradually: Boolean = false,
        override val useSameSnooze: Boolean = true,
        override var snoozeMinutes: Int = 5,
        override val timerSoundUri: String? = "content://timer",
    ) : ReminderPreferences
}
