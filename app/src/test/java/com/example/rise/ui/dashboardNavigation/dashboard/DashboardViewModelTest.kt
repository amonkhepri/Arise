package com.example.rise.ui.dashboardNavigation.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.rise.auth.UserSessionProvider
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.google.firebase.firestore.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: FakeDashboardRepository
    private lateinit var sessionProvider: FakeSessionProvider
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        repository = FakeDashboardRepository()
        sessionProvider = FakeSessionProvider()
        viewModel = DashboardViewModel(repository, sessionProvider)
    }

    @Test
    fun `initialize resolves user based on navigation source`() {
        viewModel.initialize("other-id", null, null, launchedFromBottomNavigation = false)

        assertEquals(listOf("other-id"), repository.resolvedUserIds)
        assertEquals("other-id", viewModel.otherUserId.value)
        assertTrue(repository.buildQueryInvoked)

        repository.clear()

        viewModel.initialize("other-id", null, null, launchedFromBottomNavigation = true)

        assertEquals(listOf(null), repository.resolvedUserIds)
        assertNull(viewModel.otherUserId.value)
    }

    @Test
    fun `createAlarm persists data and emits schedule when message present`() {
        val message = TextMessage("text", java.util.Date(), "sender", "receiver", "sender")
        viewModel.initialize(null, "channel", message, launchedFromBottomNavigation = true)

        var scheduledAlarm: Alarm? = null
        viewModel.events.observeForever { event ->
            val content = event.getContentIfNotHandled()
            if (content is DashboardViewModel.DashboardEvent.ScheduleAlarm) {
                scheduledAlarm = content.alarm
            }
        }

        viewModel.createAlarm(1234L)

        assertTrue(repository.incrementCalled)
        assertEquals(1, repository.savedAlarms.size)
        val saved = repository.savedAlarms.first()
        assertEquals("session-user", saved.first.userId)
        assertEquals("channel", saved.second.chatChannel)
        assertSame(message, saved.second.messsage)
        assertEquals(sessionProvider.displayName, saved.second.userName)

        assertEquals(saved.second, scheduledAlarm)
    }

    @Test
    fun `createAlarm does not emit schedule when message missing`() {
        viewModel.initialize(null, "channel", null, launchedFromBottomNavigation = true)

        var scheduled = false
        viewModel.events.observeForever { event ->
            scheduled = scheduled || event.getContentIfNotHandled() is DashboardViewModel.DashboardEvent.ScheduleAlarm
        }

        viewModel.createAlarm(5000L)

        assertTrue(repository.incrementCalled)
        assertTrue(repository.savedAlarms.isNotEmpty())
        assertTrue(!scheduled)
    }

    private class FakeDashboardRepository : DashboardRepository {
        val savedAlarms = mutableListOf<Pair<UserHandle, Alarm>>()
        val resolvedUserIds = mutableListOf<String?>()
        var buildQueryInvoked = false
        var incrementCalled = false

        fun clear() {
            savedAlarms.clear()
            resolvedUserIds.clear()
            buildQueryInvoked = false
            incrementCalled = false
        }

        override fun resolveUser(userId: String?): UserHandle {
            resolvedUserIds.add(userId)
            return UserHandle(userId ?: "session-user")
        }

        override fun buildAlarmQuery(handle: UserHandle): AlarmQuery {
            buildQueryInvoked = true
            return object : AlarmQuery {
                override fun unwrap(): Query {
                    throw UnsupportedOperationException()
                }
            }
        }

        override fun incrementAlarmId(handle: UserHandle) {
            incrementCalled = true
        }

        override fun saveAlarm(handle: UserHandle, alarm: Alarm) {
            savedAlarms += handle to alarm
        }
    }

    private class FakeSessionProvider : UserSessionProvider {
        val displayName = "Session User"

        override fun currentUserId(): String = "session-user"

        override fun currentUserName(): String = displayName
    }
}
