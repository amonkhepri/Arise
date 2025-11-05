package com.example.rise.data.myaccount

import android.net.Uri
import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.data.people.PeopleSync
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.TransportRouter
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseMyAccountRepositoryTest {

    private val authService = FakeAuthenticationService()
    private val userDataSource = FakeUserRemoteDataSource()
    private val transportBridge = NoOpTransportRuntimeBridge()
    private lateinit var lastPeopleSync: RecordingPeopleSync
    private lateinit var lastTransportRouter: RecordingTransportRouter

    private fun TestScope.repository() = FirebaseMyAccountRepository(
        authService = authService,
        userRemoteDataSource = userDataSource,
        peopleSync = RecordingPeopleSync().also { lastPeopleSync = it },
        transportRouter = RecordingTransportRouter().also { lastTransportRouter = it },
        ioDispatcher = StandardTestDispatcher(testScheduler),
        transportBridge = transportBridge,
    )

    @Test
    fun `fetchCurrentUser returns user when snapshot exists`() = runTest {
        val user = User(name = "Ada", bio = "Moon lover", profilePicturePath = null, registrationTokens = mutableListOf())
        authService.user = AuthenticationService.User("uid-123", "Ada", "ada@example.com", null)
        userDataSource.users["uid-123"] = user

        val result = repository().fetchCurrentUser()

        assertEquals(user, result)
    }

    @Test
    fun `fetchCurrentUser throws when current user is missing`() = runTest {
        authService.user = null

        try {
            repository().fetchCurrentUser()
            fail("Expected IllegalStateException when no user is authenticated")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `fetchCurrentUser throws when snapshot has no user`() = runTest {
        authService.user = AuthenticationService.User("uid-123", "Ada", null, null)
        userDataSource.users["uid-123"] = null

        try {
            repository().fetchCurrentUser()
            fail("Expected IllegalStateException when user document missing")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `updateCurrentUser updates provided non blank fields`() = runTest {
        val stored = User(name = "Original", bio = "Old bio", profilePicturePath = null, registrationTokens = mutableListOf())
        authService.user = AuthenticationService.User("uid-123", stored.name, null, null)
        userDataSource.users["uid-123"] = stored

        repository().updateCurrentUser(name = "Ada", bio = "Explores the stars")

        val updates = userDataSource.lastUpdates
        assertEquals(mapOf("name" to "Ada", "bio" to "Explores the stars"), updates)
        val updatedUser = userDataSource.users["uid-123"]
        assertEquals("Ada", updatedUser?.name)
        assertEquals("Explores the stars", updatedUser?.bio)
    }

    @Test
    fun `updateCurrentUser skips update when both fields blank`() = runTest {
        authService.user = AuthenticationService.User("uid-123", "Ada", null, null)
        userDataSource.users["uid-123"] = User("Ada", "Bio", null, mutableListOf())

        repository().updateCurrentUser(name = " ", bio = "")

        assertNull(userDataSource.lastUpdates)
    }

    @Test
    fun `updateCurrentUser throws when current user missing`() = runTest {
        authService.user = null

        try {
            repository().updateCurrentUser(name = "Ada", bio = "Bio")
            fail("Expected IllegalStateException when no user is authenticated")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `signOut clears session state and delegates to AuthenticationService`() = runTest {
        repository().signOut()

        assertTrue(authService.signOutCalled)
        assertEquals(1, lastPeopleSync.stopCalls)
        assertEquals(1, lastTransportRouter.resetCalls)
    }

    private class FakeAuthenticationService : AuthenticationService {
        var user: AuthenticationService.User? = null
        var signOutCalled = false

        override fun currentUser(): AuthenticationService.User? = user

        override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle {
            listener(user)
            return AuthStateHandle { }
        }

        override suspend fun signInWithEmail(email: String, password: String) = unsupported()

        override suspend fun createUserWithEmail(email: String, password: String) = unsupported()

        override suspend fun signInWithCustomToken(customToken: String) = unsupported()

        override suspend fun updateProfile(displayName: String?, photoUrl: Uri?) {
            user = user?.copy(displayName = displayName ?: user?.displayName, photoUrl = photoUrl)
        }

        override fun signOut() {
            signOutCalled = true
            user = null
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Not needed in test")
    }

    private class FakeUserRemoteDataSource : UserRemoteDataSource {
        val users = mutableMapOf<String, User?>()
        var lastUpdates: Map<String, Any>? = null

        override suspend fun fetchUser(userId: String): User? = users[userId]

        override suspend fun updateUser(userId: String, updates: Map<String, Any>) {
            lastUpdates = updates
            val current = users[userId] ?: User()
            val updated = current.copy(
                name = (updates["name"] as? String) ?: current.name,
                bio = (updates["bio"] as? String) ?: current.bio,
                profilePicturePath = current.profilePicturePath,
                registrationTokens = current.registrationTokens.toMutableList()
            )
            users[userId] = updated
        }
    }

    private class NoOpTransportRuntimeBridge : TransportRuntimeBridge {
        override val currentMode: StateFlow<BriarTransportMode> = MutableStateFlow(BriarTransportMode.FIRESTORE)
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
        override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val briarChatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(object : BriarChatGateway {
            override val isAvailable: Boolean = false
        })
        override val briarContactService: StateFlow<BriarContactService> = MutableStateFlow(object : BriarContactService {
            override val isAvailable: Boolean = false
        })
        override fun requireFirestore(caller: String) = Unit
    }

    private class RecordingPeopleSync : PeopleSync {
        var stopCalls = 0
        override val errors: Flow<Throwable> = emptyFlow()
        override val currentUserCanonicalId: Flow<String?> = emptyFlow()
        override fun ensureStarted() = Unit
        override fun stop() { stopCalls++ }
    }

    private class RecordingTransportRouter : TransportRouter {
        var resetCalls = 0

        override val currentIdentity: Flow<CanonicalIdentity> = emptyFlow()

        override suspend fun ensureCurrentIdentity(): CanonicalIdentity = throw UnsupportedOperationException()

        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation =
            throw UnsupportedOperationException()

        override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> = emptyFlow()

        override suspend fun send(message: ConnectorOutboundMessage) =
            throw UnsupportedOperationException()

        override suspend fun reset() {
            resetCalls++
        }
    }
}
