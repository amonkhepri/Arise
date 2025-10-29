package com.example.rise.ui.dashboardNavigation.dashboard

import app.cash.turbine.test
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.ui.alarm.models.Alarm
import com.example.rise.models.TextMessage
import android.net.Uri
import com.example.rise.auth.AuthenticationService
import com.example.rise.auth.AuthStateHandle
import com.google.firebase.firestore.Query
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

    private val query = StubAlarmQuery("query")
    private val repository = FakeAlarmRepository(query)
    private val auth = FakeAuthenticationService(
        AuthenticationService.User(
            id = "self",
            displayName = "Alice",
            email = "alice@example.com",
            photoUrl = null,
        )
    )
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
            assertTrue(event is DashboardViewModel.DashboardEvent.ScheduleDelayedMessage)
            assertEquals(1, repository.saved.size)
            val alarm = repository.saved.first()
            assertEquals(1234L, alarm.timeInMiliseconds)
            assertEquals("channel", alarm.chatChannel)
            assertEquals(message, alarm.messsage)
            assertEquals(Instant.now(clock).toEpochMilli().toInt(), alarm.idTimeStamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeAlarmRepository(private val query: AlarmRepository.AlarmQuery) : AlarmRepository {
        val saved = mutableListOf<Alarm>()
        override fun alarmsQuery(userId: String): AlarmRepository.AlarmQuery = query
        override suspend fun saveAlarm(userId: String, alarm: Alarm) {
            saved += alarm
        }
    }

    private class StubAlarmQuery(private val id: String) : AlarmRepository.AlarmQuery {
        override fun asFirestoreQuery(): Query = throw UnsupportedOperationException("Not needed in test $id")
    }

    private class FakeAuthenticationService(
        private var current: AuthenticationService.User?
    ) : AuthenticationService {
        override fun currentUser(): AuthenticationService.User? = current

        override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle {
            listener(current)
            return AuthStateHandle { }
        }

        override suspend fun signInWithEmail(email: String, password: String) = unsupported()

        override suspend fun createUserWithEmail(email: String, password: String) = unsupported()

        override suspend fun signInWithCustomToken(customToken: String) = unsupported()

        override suspend fun updateProfile(displayName: String?, photoUrl: android.net.Uri?) {
            current = current?.copy(displayName = displayName ?: current?.displayName, photoUrl = photoUrl)
        }

        override fun signOut() {
            current = null
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Not needed in test")
    }
}
