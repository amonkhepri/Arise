package com.example.rise.data.people

import android.content.Context
import com.example.rise.auth.AuthenticationService
import com.example.rise.featureflags.BriarTransportMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityBackfillSchedulerTest {

    private val context = mockk<Context>(relaxed = true)
    private val tracker = mockk<IdentityBackfillStatusTracker>()
    private val authService = mockk<AuthenticationService>()
    private lateinit var enqueuer: RecordingEnqueuer
    private lateinit var scheduler: IdentityBackfillScheduler

    @Before
    fun setUp() {
        enqueuer = RecordingEnqueuer()
        scheduler = IdentityBackfillScheduler(
            applicationContext = context,
            statusTracker = tracker,
            authenticationService = authService,
            enqueuer = enqueuer,
        )
    }

    @Test
    fun `does not enqueue when mode is Firestore`() = runTest {
        scheduler.scheduleIfNeeded(BriarTransportMode.FIRESTORE)

        assertEquals(emptyList<Boolean>(), enqueuer.calls)
    }

    @Test
    fun `enqueues when user is not yet authenticated`() = runTest {
        every { authService.currentUser() } returns null

        scheduler.scheduleIfNeeded(BriarTransportMode.HYBRID)

        assertEquals(listOf(false), enqueuer.calls)
        coVerify(exactly = 0) { tracker.isComplete(any()) }
    }

    @Test
    fun `skips when current user already completed backfill`() = runTest {
        val user = AuthenticationService.User(id = "abc", displayName = null, email = null, photoUrl = null)
        every { authService.currentUser() } returns user
        coEvery { tracker.isComplete("abc") } returns true

        scheduler.scheduleIfNeeded(BriarTransportMode.HYBRID)

        assertEquals(emptyList<Boolean>(), enqueuer.calls)
    }

    @Test
    fun `enqueues when current user still needs backfill`() = runTest {
        val user = AuthenticationService.User(id = "abc", displayName = null, email = null, photoUrl = null)
        every { authService.currentUser() } returns user
        coEvery { tracker.isComplete("abc") } returns false

        scheduler.scheduleIfNeeded(BriarTransportMode.BRIAR_ONLY)

        assertEquals(listOf(false), enqueuer.calls)
        coVerify { tracker.isComplete("abc") }
    }

    @Test
    fun `forceSchedule always enqueues with force=true`() {
        scheduler.forceSchedule()

        assertEquals(listOf(true), enqueuer.calls)
    }

    private class RecordingEnqueuer : IdentityBackfillEnqueuer {
        val calls = mutableListOf<Boolean>()
        override fun enqueue(context: Context, force: Boolean) {
            calls += force
        }
    }
}
