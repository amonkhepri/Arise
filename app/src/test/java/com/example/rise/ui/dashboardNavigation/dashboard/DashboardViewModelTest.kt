package com.example.rise.ui.dashboardNavigation.dashboard

import app.cash.turbine.test
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Query
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.example.rise.util.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val query: Query = mockk(relaxed = true)
    private val repository = FakeAlarmRepository(query)
    private val firebaseUser: FirebaseUser = mockk {
        every { uid } returns "self"
        every { displayName } returns "Alice"
    }
    private val auth: FirebaseAuth = mockk {
        every { currentUser } returns firebaseUser
    }
    private val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)

    @org.junit.Test
    fun `initialise configures query and active user`() {
        val viewModel = DashboardViewModel(repository, auth, clock)

        viewModel.initialise(byBottomNavigation = false, explicitUserId = "other", chatChannel = "channel", message = null)

        val state = viewModel.uiState.value
        assertEquals("other", state.activeUserId)
        assertEquals(query, state.alarmQuery)
        assertEquals("channel", state.chatChannel)
        assertNull(state.pendingMessage)
        assertEquals(false, state.isLoading)
    }

    @org.junit.Test
    fun `createAlarm saves alarm and emits schedule event when message provided`() = runTest {
        val viewModel = DashboardViewModel(repository, auth, clock)
        val message = TextMessage(
            text = "Hello",
            time = Date(),
            senderId = "self",
            recipientId = "other",
            senderName = "Alice"
        )
        viewModel.initialise(byBottomNavigation = false, explicitUserId = "other", chatChannel = "channel", message = message)

        viewModel.events.test {
            viewModel.createAlarm(timeInMillis = 1234L)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is DashboardViewModel.DashboardEvent.ScheduleAlarm)
            assertEquals(1, repository.saved.size)
            val alarm = repository.saved.first()
            assertEquals(1234L, alarm.timeInMiliseconds)
            assertEquals("channel", alarm.chatChannel)
            assertEquals(message, alarm.messsage)
            assertEquals(Instant.now(clock).toEpochMilli().toInt(), alarm.idTimeStamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeAlarmRepository(private val query: Query) : AlarmRepository {
        val saved = mutableListOf<Alarm>()
        override fun alarmsQuery(userId: String): Query = query
        override suspend fun saveAlarm(userId: String, alarm: Alarm) {
            saved += alarm
        }
    }
}
