package com.example.rise.transport.connectors

import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.data.chat.CachedChatMessage
import com.example.rise.data.chat.ChatLocalCache
import com.example.rise.data.firestore.ChatRemoteDataSource
import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportId
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreConnectorTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `currentIdentity returns cached identity without refetching when auth user unchanged`() = runTest {
        val fixture = ConnectorFixture(dispatcherRule.testDispatcher)
        val connector = fixture.connector

        val first = connector.currentIdentity()
        val second = connector.currentIdentity()

        assertSame(first, second)
        assertEquals("uid-a", first.id)
        assertEquals(listOf("uid-a"), fixture.chatRemoteDataSource.displayNameRequests)
        assertTrue(fixture.localCache.clearedUsers.isEmpty())
    }

    @Test
    fun `currentIdentity clears user scoped caches when auth user changes`() = runTest {
        val fixture = ConnectorFixture(dispatcherRule.testDispatcher)
        val connector = fixture.connector

        val firstIdentity = connector.currentIdentity()
        assertEquals("uid-a", firstIdentity.id)

        connector.channelCache()["other"] = "channel-42"
        connector.messagesCache()["conversation-1"] = listOf(
            ConnectorInboundMessage(
                messageId = "msg-1",
                conversationId = "conversation-1",
                senderId = "uid-a",
                recipientId = "uid-b",
                senderName = "Alice",
                body = "Hello",
                transport = TransportId.FIRESTORE,
                timestamp = Date(0),
            )
        )

        fixture.switchToUserB()

        val secondIdentity = connector.currentIdentity()
        assertEquals("uid-b", secondIdentity.id)
        assertTrue(connector.channelCache().isEmpty())
        assertTrue(connector.messagesCache().isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("uid-a"), fixture.localCache.clearedUsers)
        assertEquals(listOf("uid-a", "uid-b"), fixture.chatRemoteDataSource.displayNameRequests)
    }

    @Test
    fun `caches clear immediately when auth user logs out`() = runTest {
        val fixture = ConnectorFixture(dispatcherRule.testDispatcher)
        val connector = fixture.connector

        connector.currentIdentity()
        connector.channelCache()["other"] = "channel-42"
        connector.messagesCache()["conversation-1"] = emptyList()

        fixture.logout()

        advanceUntilIdle()
        assertTrue(connector.channelCache().isEmpty())
        assertTrue(connector.messagesCache().isEmpty())
        assertEquals(listOf("uid-a"), fixture.localCache.clearedUsers)

        fixture.switchToUserB()
        val identity = connector.currentIdentity()
        assertEquals("uid-b", identity.id)
    }

    @Test
    fun `observeMessages clears local cache when remote snapshot empty`() = runTest {
        val fixture = ConnectorFixture(dispatcherRule.testDispatcher)
        val connector = fixture.connector
        val conversationId = "conversation-1"

        val remoteMessages = listOf(
            ChatRemoteDataSource.RemoteMessage(
                id = "msg-1",
                payload = TextMessage(
                    text = "hello",
                    time = Date(0),
                    senderId = "uid-a",
                    recipientId = "uid-b",
                    senderName = "Alice",
                )
            )
        )

        val collectJob = launch {
            connector.observeMessages(conversationId)
                .take(2)
                .collect { }
        }

        fixture.chatRemoteDataSource.emit(conversationId, remoteMessages)
        advanceUntilIdle()

        fixture.chatRemoteDataSource.emit(conversationId, emptyList())
        advanceUntilIdle()

        collectJob.join()

        val userId = fixture.authService.currentUser()!!.id
        val cacheKey = userId to conversationId

        val writeOps = fixture.localCache.operations.filter { it == "writeMessages:$userId:$conversationId" }
        assertEquals(2, writeOps.size)
        assertEquals(emptyList<CachedChatMessage>(), fixture.localCache.messageStore[cacheKey])
    }

    @Test
    fun `channel cache writes wait for pending user clear`() = runTest {
        val fixture = ConnectorFixture(dispatcherRule.testDispatcher)
        val connector = fixture.connector

        fixture.chatRemoteDataSource.nextChannelId = "new-channel"
        fixture.localCache.onClear = { userId, gate ->
            if (userId == "uid-a") {
                gate.first.complete(Unit)
                gate.second.await()
            }
        }

        connector.currentIdentity()

        val clearGate = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        fixture.localCache.pendingClear = clearGate to releaseGate

        fixture.switchToUserB()
        clearGate.await()

        val ensureJob = async { connector.ensureConversation(fixture.conversation) }

        assertFalse(ensureJob.isCompleted)
        assertFalse("writeChannelId" in fixture.localCache.operations)

        releaseGate.complete(Unit)
        advanceUntilIdle()

        val conversationId = ensureJob.await()

        assertEquals("new-channel", conversationId)
        assertEquals(
            listOf(
                "clear:uid-a",
                "readChannelId:uid-b:uid-a",
                "writeChannelId:uid-b:uid-a"
            ),
            fixture.localCache.operations
        )
    }

    @Test
    fun `observeContacts emits connector contacts`() = runTest {
        val fixture = ConnectorFixture(dispatcherRule.testDispatcher)
        val connector = fixture.connector

        val alice = User(
            name = "Alice",
            bio = "Bio",
            profilePicturePath = "path-a",
            registrationTokens = mutableListOf("token-a"),
        )
        val bob = User(
            name = "Bob",
            bio = "",
            profilePicturePath = null,
            registrationTokens = mutableListOf("token-b"),
        )
        fixture.userRemoteDataSource.emit(
            "uid-a" to alice,
            "uid-b" to bob,
        )

        val contacts = connector.observeContacts().first()

        assertEquals(2, contacts.size)
        val aliceContact = contacts.first { it.canonicalId == "uid-a" }
        assertEquals(TransportId.FIRESTORE, aliceContact.transport)
        assertEquals("uid-a", aliceContact.transportId)
        assertEquals("Alice", aliceContact.displayName)
        assertEquals("Bio", aliceContact.bio)
        assertEquals("path-a", aliceContact.profilePicturePath)
        assertEquals(PresenceStatus.UNKNOWN, aliceContact.presence)
        assertEquals(listOf("token-a"), aliceContact.registrationTokens)
    }

    private class ConnectorFixture(dispatcher: TestDispatcher) {
        val authService = FakeAuthenticationService(
            AuthenticationService.User(
                id = "uid-a",
                displayName = "Alice Display",
                email = "alice@example.com",
                photoUrl = null,
            )
        )
        val chatRemoteDataSource = FakeChatRemoteDataSource().apply {
            displayNames["uid-a"] = "Alice"
            displayNames["uid-b"] = "Bob"
        }
        val userRemoteDataSource = FakeUserRemoteDataSource()
        val transportBridge: TransportRuntimeBridge = NoOpTransportRuntimeBridge()
        val localCache = FakeChatLocalCache()
        val conversation = com.example.rise.transport.router.CanonicalConversation(
            id = "",
            participants = setOf("uid-b", "uid-a"),
            title = "Bob",
        )
        val connector = FirestoreConnector(
            authService = authService,
            chatRemoteDataSource = chatRemoteDataSource,
            userRemoteDataSource = userRemoteDataSource,
            transportBridge = transportBridge,
            localCache = localCache,
            telemetrySink = { },
            cacheDispatcher = dispatcher,
        )

        fun switchToUserB() {
            authService.switchUser(
                AuthenticationService.User(
                    id = "uid-b",
                    displayName = "Bob Display",
                    email = "bob@example.com",
                    photoUrl = null,
                )
            )
        }

        fun logout() {
            authService.switchUser(null)
        }
    }

    private class FakeAuthenticationService(initialUser: AuthenticationService.User?) : AuthenticationService {
        private val listeners = mutableSetOf<(AuthenticationService.User?) -> Unit>()
        private var current: AuthenticationService.User? = initialUser

        override fun currentUser(): AuthenticationService.User? = current

        override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle {
            listeners += listener
            listener(current)
            return AuthStateHandle { listeners -= listener }
        }

        override suspend fun signInWithEmail(email: String, password: String) = unsupported()

        override suspend fun createUserWithEmail(email: String, password: String) = unsupported()

        override suspend fun signInWithCustomToken(customToken: String) = unsupported()

        override suspend fun updateProfile(displayName: String?, photoUrl: android.net.Uri?) {
            current = current?.copy(
                displayName = displayName ?: current?.displayName,
                photoUrl = photoUrl ?: current?.photoUrl,
            )
            notifyListeners()
        }

        override fun signOut() {
            current = null
            notifyListeners()
        }

        fun switchUser(user: AuthenticationService.User?) {
            current = user
            notifyListeners()
        }

        private fun notifyListeners() {
            listeners.forEach { it(current) }
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Not needed in test")
    }

    private class FakeChatRemoteDataSource : ChatRemoteDataSource {
        val displayNames = mutableMapOf<String, String?>()
        val displayNameRequests = mutableListOf<String>()
        val channelIds = mutableMapOf<Pair<String, String>, String?>()
        var nextChannelId: String = "new-channel"
        private val messageFlows = mutableMapOf<String, MutableStateFlow<List<ChatRemoteDataSource.RemoteMessage>>>()
        val sentMessages = mutableListOf<Pair<String, TextMessage>>()

        override suspend fun getUserDisplayName(userId: String): String? {
            displayNameRequests += userId
            return displayNames[userId]
        }

        override suspend fun getExistingChannelId(currentUserId: String, otherUserId: String): String? {
            return channelIds[currentUserId to otherUserId]
        }

        override suspend fun createChannel(currentUserId: String, otherUserId: String): String {
            val channelId = nextChannelId
            channelIds[currentUserId to otherUserId] = channelId
            channelIds[otherUserId to currentUserId] = channelId
            return channelId
        }

        override fun observeMessages(conversationId: String): Flow<List<ChatRemoteDataSource.RemoteMessage>> {
            return messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        }

        fun emit(conversationId: String, messages: List<ChatRemoteDataSource.RemoteMessage>) {
            messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = messages
        }

        override suspend fun sendMessage(conversationId: String, message: TextMessage) {
            sentMessages += conversationId to message
        }
    }

    private class FakeUserRemoteDataSource : UserRemoteDataSource {
        private val state = MutableStateFlow<List<UserRemoteDataSource.UserSnapshot>>(emptyList())
        private val users = mutableMapOf<String, User>()
        val updateRequests = mutableListOf<Pair<String, Map<String, Any>>>()

        override suspend fun fetchUser(userId: String): User? = users[userId]

        override suspend fun updateUser(userId: String, updates: Map<String, Any>) {
            updateRequests += userId to updates
        }

        override suspend fun setUser(userId: String, user: User) {
            users[userId] = user
        }

        override fun observeUsers(): Flow<List<UserRemoteDataSource.UserSnapshot>> = state

        fun emit(vararg entries: Pair<String, User>) {
            entries.forEach { (id, user) -> users[id] = user }
            state.value = entries.map { (id, user) ->
                UserRemoteDataSource.UserSnapshot(id = id, user = user)
            }
        }
    }

    private class FakeChatLocalCache : ChatLocalCache {
        val messageStore = mutableMapOf<Pair<String, String>, List<CachedChatMessage>>()
        val channelStore = mutableMapOf<Pair<String, String>, String?>()
        val operations = mutableListOf<String>()
        val clearedUsers = mutableListOf<String>()
        var pendingClear: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        var onClear: (suspend (String, Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>) -> Unit)? = null

        override suspend fun readMessages(userId: String, channelId: String): List<CachedChatMessage> {
            operations += "readMessages:$userId:$channelId"
            return messageStore[userId to channelId] ?: emptyList()
        }

        override suspend fun writeMessages(userId: String, channelId: String, messages: List<CachedChatMessage>) {
            operations += "writeMessages:$userId:$channelId"
            messageStore[userId to channelId] = messages
        }

        override suspend fun readChannelId(userId: String, otherUserId: String): String? {
            operations += "readChannelId:$userId:$otherUserId"
            return channelStore[userId to otherUserId]
        }

        override suspend fun writeChannelId(userId: String, otherUserId: String, channelId: String) {
            operations += "writeChannelId:$userId:$otherUserId"
            channelStore[userId to otherUserId] = channelId
        }

        override suspend fun clear(userId: String) {
            operations += "clear:$userId"
            clearedUsers += userId
            val gate = pendingClear
            if (gate != null) {
                pendingClear = null
                onClear?.invoke(userId, gate)
            }
            messageStore.keys.removeIf { it.first == userId }
            channelStore.keys.removeIf { it.first == userId }
        }
    }

    private class NoOpTransportRuntimeBridge : TransportRuntimeBridge {
        override val currentMode: StateFlow<BriarTransportMode> = MutableStateFlow(BriarTransportMode.FIRESTORE)
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
        override val diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
        override val briarChatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(object : BriarChatGateway {
            override val isAvailable: Boolean = false
        })
        override val briarContactService: StateFlow<BriarContactService> = MutableStateFlow(object : BriarContactService {
            override val isAvailable: Boolean = false
        })
        override fun requireFirestore(caller: String) = Unit
    }
}

private fun FirestoreConnector.channelCache(): ConcurrentHashMap<String, String> {
    val field = FirestoreConnector::class.java.getDeclaredField("channelCache")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as ConcurrentHashMap<String, String>
}

private fun FirestoreConnector.messagesCache(): ConcurrentHashMap<String, List<ConnectorInboundMessage>> {
    val field = FirestoreConnector::class.java.getDeclaredField("messagesCache")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as ConcurrentHashMap<String, List<ConnectorInboundMessage>>
}
