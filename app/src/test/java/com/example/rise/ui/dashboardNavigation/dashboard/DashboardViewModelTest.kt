package com.example.rise.ui.dashboardNavigation.dashboard

import android.net.Uri
import app.cash.turbine.test
import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.models.TextMessage
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportRouter
import com.example.rise.ui.alarm.models.Alarm
import com.example.rise.util.MainDispatcherRule
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

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
    private val transportBridge = FakeTransportRuntimeBridge()
    private val transportRouter = FakeTransportRouter()
    private val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)

    @org.junit.Test
    fun `initialise configures query and active user`() {
        val viewModel = DashboardViewModel(repository, auth, transportRouter, transportBridge, clock)

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
        val viewModel = DashboardViewModel(repository, auth, transportRouter, transportBridge, clock)
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

    @org.junit.Test
    fun `initialise handles firestore disabled mode without crashing`() {
        val repository = FirestoreDisabledAlarmRepository()
        val viewModel = DashboardViewModel(repository, auth, transportRouter, transportBridge, clock)

        val result = runCatching {
            viewModel.initialise(
                byBottomNavigation = true,
                explicitUserId = null,
                chatChannel = null,
                message = null,
            )
        }

        val state = viewModel.uiState.value
        assertTrue(
            "Expected initialise to handle Firestore being disabled without throwing but got ${result.exceptionOrNull()?.message}",
            result.isSuccess
        )
        assertEquals(
            "Firestore path disabled in mode BRIAR_ONLY (called from FirestoreAlarmRepository#alarmsQuery)",
            state.errorMessage
        )
        assertEquals(false, state.isLoading)
    }

    @org.junit.Test
    fun `initialise falls back to briar identity when auth user missing in briar only mode`() {
        val auth = FakeAuthenticationService(null)
        val bridge = FakeTransportRuntimeBridge().apply { setMode(BriarTransportMode.BRIAR_ONLY) }
        val router = FakeTransportRouter(CanonicalIdentity(id = "briar-user", displayName = "Briar User"))
        val viewModel = DashboardViewModel(repository, auth, router, bridge, clock)

        viewModel.initialise(byBottomNavigation = true, explicitUserId = null, chatChannel = null, message = null)

        val state = viewModel.uiState.value
        assertEquals("briar-user", state.activeUserId)
        assertEquals(query, state.alarmQuery)
        assertEquals(false, state.isLoading)
    }

    private class FakeAlarmRepository(private val query: AlarmRepository.AlarmQuery) : AlarmRepository {
        val saved = mutableListOf<Alarm>()
        override fun alarmsQuery(userId: String): AlarmRepository.AlarmQuery = query
        override suspend fun saveAlarm(userId: String, alarm: Alarm) {
            saved += alarm
        }
    }

    private class FirestoreDisabledAlarmRepository : AlarmRepository {
        override fun alarmsQuery(userId: String): AlarmRepository.AlarmQuery {
            throw IllegalStateException(
                "Firestore path disabled in mode BRIAR_ONLY (called from FirestoreAlarmRepository#alarmsQuery)"
            )
        }

        override suspend fun saveAlarm(userId: String, alarm: Alarm) {
            throw UnsupportedOperationException("Not needed in test")
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

        override suspend fun updateProfile(displayName: String?, photoUrl: Uri?) {
            current = current?.copy(displayName = displayName ?: current?.displayName, photoUrl = photoUrl)
        }

        override fun signOut() {
            current = null
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Not needed in test")
    }

    private class FakeTransportRouter(
        private val identity: CanonicalIdentity = CanonicalIdentity(id = "self", displayName = "Self"),
    ) : TransportRouter {
        override val currentIdentity: kotlinx.coroutines.flow.Flow<CanonicalIdentity>
            get() = throw UnsupportedOperationException("Not needed")

        override suspend fun ensureCurrentIdentity(): CanonicalIdentity = identity

        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): com.example.rise.transport.router.CanonicalConversation {
            throw UnsupportedOperationException("Not needed")
        }

        override fun observeConversation(conversationId: String): kotlinx.coroutines.flow.Flow<List<CanonicalMessage>> {
            throw UnsupportedOperationException("Not needed")
        }

        override suspend fun sendMessage(message: ConnectorOutboundMessage) {
            throw UnsupportedOperationException("Not needed")
        }

        override suspend fun reset() {}
    }

    private class FakeTransportRuntimeBridge(
        initialMode: BriarTransportMode = BriarTransportMode.FIRESTORE,
    ) : TransportRuntimeBridge {
        private val mode = MutableStateFlow(initialMode)
        override val currentMode: StateFlow<BriarTransportMode> = mode
        override val runtimeStatus: StateFlow<com.example.rise.briar.runtime.BriarRuntimeStatus>
            get() = throw UnsupportedOperationException("Not needed")
        override val diagnostics: kotlinx.coroutines.flow.SharedFlow<com.example.rise.briar.runtime.BriarRuntimeEvent>
            get() = throw UnsupportedOperationException("Not needed")
        override val briarChatGateway: StateFlow<com.example.rise.briar.runtime.BriarChatGateway>
            get() = throw UnsupportedOperationException("Not needed")
        override val briarContactService: StateFlow<com.example.rise.briar.runtime.BriarContactService>
            get() = throw UnsupportedOperationException("Not needed")

        override fun requireFirestore(caller: String) {
            if (mode.value != BriarTransportMode.FIRESTORE) {
                throw IllegalStateException("Firestore path disabled")
            }
        }

        fun setMode(mode: BriarTransportMode) {
            this.mode.value = mode
        }
    }
}
