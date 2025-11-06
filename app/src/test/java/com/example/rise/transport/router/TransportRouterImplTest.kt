package com.example.rise.transport.router

import app.cash.turbine.test
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.store.ConversationStore
import java.util.ArrayDeque
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransportRouterImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `ensureConversation delegates to primary connector`() = scope.runTest {
        val store = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
        val connector: RecordingConnector = RecordingConnector(TransportId.FIRESTORE)
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(setOf(connector)),
            conversationStore = store,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        identityRegistry.upsertIdentity(
            identity = CanonicalIdentity(id = "self", displayName = "Self"),
            aliases = mapOf(TransportId.FIRESTORE to "self"),
            setAsCurrent = true,
        )

        val conversation = router.ensureConversation(CanonicalIdentity("other", "Other"))

        assertEquals("conversation-1", conversation.id)
        assertEquals(1, connector.ensureConversationCalls)
        assertEquals("conversation-1", store.getConversation("conversation-1")?.id)
    }

    @Test
    fun `ensureCurrentIdentity refreshes cached identity when connector reports different user`() = scope.runTest {
        val oldIdentity = IdentityRecord(
            canonicalIdentity = CanonicalIdentity(id = "old-user", displayName = "Old User"),
            aliases = mapOf(TransportId.FIRESTORE to "old-user"),
        )
        val store = FakeIdentityRegistryStore(
            IdentityRegistryStore.StoredState(
                records = mapOf(oldIdentity.canonicalIdentity.id to oldIdentity),
                currentIdentityId = oldIdentity.canonicalIdentity.id,
            )
        )
        val conversationStore = InMemoryConversationStore()
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        connectorIdentity = CanonicalIdentity(id = "new-user", displayName = "New User")
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = IdentityRegistryImpl(store),
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )
        val observation = launch {
            router.observeConversation("conversation-1").collect { }
        }
        advanceUntilIdle()
        assertEquals(1, router.observationJobCount())

        val resolved = router.ensureCurrentIdentity()

        assertEquals("new-user", resolved.id)
        assertEquals("new-user", store.state.currentIdentityId)
        val persisted = store.state.records["new-user"]
        requireNotNull(persisted)
        assertEquals("New User", persisted.canonicalIdentity.displayName)
        assertEquals(1, conversationStore.clearAllCalls)
        advanceUntilIdle()
        assertEquals(0, router.observationJobCount())
        observation.cancel()
    }

    @Test
    fun `ensureCurrentIdentity falls back to cached identity when connector lookup fails`() = scope.runTest {
        val cachedIdentity = CanonicalIdentity(id = "cached-user", displayName = "Cached User")
        val store = FakeIdentityRegistryStore(
            IdentityRegistryStore.StoredState(
                records = mapOf(
                    cachedIdentity.id to IdentityRecord(
                        canonicalIdentity = cachedIdentity,
                        aliases = mapOf(TransportId.FIRESTORE to cachedIdentity.id),
                    )
                ),
                currentIdentityId = cachedIdentity.id,
            )
        )
        val conversationStore = InMemoryConversationStore()
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        identityError = IllegalStateException("offline")
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = IdentityRegistryImpl(store),
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val resolved = router.ensureCurrentIdentity()

        assertEquals(cachedIdentity, resolved)
        assertEquals(0, conversationStore.clearAllCalls)
    }

    @Test
    fun  `ensureCurrentIdentity propagates auth sign-out errors instead of returning stale identity`() = scope.runTest {
        val cachedIdentity = CanonicalIdentity(id = "cached-user", displayName = "Cached User")
        val store = FakeIdentityRegistryStore(
            IdentityRegistryStore.StoredState(
                records = mapOf(
                    cachedIdentity.id to IdentityRecord(
                        canonicalIdentity = cachedIdentity,
                        aliases = mapOf(TransportId.FIRESTORE to cachedIdentity.id),
                    )
                ),
                currentIdentityId = cachedIdentity.id,
            )
        )
        val conversationStore = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(store)

        val transportRouter = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        identityError = IllegalStateException("User must be signed in")
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        try {
            transportRouter.ensureCurrentIdentity()
            error("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            assertEquals("User must be signed in", error.message)
        }
        assertEquals(1, conversationStore.clearAllCalls)
        assertTrue(store.state.records.isEmpty())
        assertEquals(null, store.state.currentIdentityId)
    }

    @Test
    fun `ensureCurrentIdentity seeds identity when cache empty`() = scope.runTest {
        val conversationStore = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())

        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        connectorIdentity = CanonicalIdentity(id = "fresh-user", displayName = "Fresh")
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val resolved = router.ensureCurrentIdentity()

        assertEquals("fresh-user", resolved.id)
        assertEquals("fresh-user", identityRegistry.currentIdentitySnapshot()?.id)
        assertEquals(0, conversationStore.clearAllCalls)
    }

    @Test
    fun `ensureCurrentIdentity returns cached identity when connector reports same user`() = scope.runTest {
        val identity = CanonicalIdentity(id = "cached-user", displayName = "Same User")
        val store = FakeIdentityRegistryStore(
            IdentityRegistryStore.StoredState(
                records = mapOf(
                    identity.id to IdentityRecord(
                        canonicalIdentity = identity,
                        aliases = mapOf(TransportId.FIRESTORE to identity.id),
                    )
                ),
                currentIdentityId = identity.id,
            )
        )
        val conversationStore = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(store)
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        connectorIdentity = identity
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val resolved = router.ensureCurrentIdentity()

        assertEquals(identity, resolved)
        assertEquals(identity, identityRegistry.currentIdentitySnapshot())
        assertEquals(0, conversationStore.clearAllCalls)
    }

    @Test
    fun `ensureCurrentIdentity updates display name when connector reports same id`() = scope.runTest {
        val cachedIdentity = CanonicalIdentity(id = "user", displayName = "Old Name")
        val store = FakeIdentityRegistryStore(
            IdentityRegistryStore.StoredState(
                records = mapOf(
                    cachedIdentity.id to IdentityRecord(
                        canonicalIdentity = cachedIdentity,
                        aliases = mapOf(TransportId.FIRESTORE to cachedIdentity.id),
                    )
                ),
                currentIdentityId = cachedIdentity.id,
            )
        )
        val conversationStore = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(store)
        val updatedIdentity = CanonicalIdentity(id = "user", displayName = "New Name")
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        connectorIdentity = updatedIdentity
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val resolved = router.ensureCurrentIdentity()

        assertEquals(updatedIdentity, resolved)
        assertEquals("New Name", identityRegistry.currentIdentitySnapshot()?.displayName)
        assertEquals(0, conversationStore.clearAllCalls)
    }

    @Test
    fun `ensureCurrentIdentity returns cached identity on non auth error`() = scope.runTest {
        val cachedIdentity = CanonicalIdentity(id = "cached-user", displayName = "Cached User")
        val store = FakeIdentityRegistryStore(
            IdentityRegistryStore.StoredState(
                records = mapOf(
                    cachedIdentity.id to IdentityRecord(
                        canonicalIdentity = cachedIdentity,
                        aliases = mapOf(TransportId.FIRESTORE to cachedIdentity.id),
                    )
                ),
                currentIdentityId = cachedIdentity.id,
            )
        )
        val conversationStore = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(store)
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(
                setOf(
                    RecordingConnector(TransportId.FIRESTORE).apply {
                        identityError = IllegalStateException("Connector offline")
                    }
                )
            ),
            conversationStore = conversationStore,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val resolved = router.ensureCurrentIdentity()

        assertEquals(cachedIdentity, resolved)
        assertEquals(cachedIdentity, identityRegistry.currentIdentitySnapshot())
        assertEquals(0, conversationStore.clearAllCalls)
    }

    @Test
    fun `observeConversation surfaces messages persisted via connectors`() = scope.runTest {
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
        val connector: RecordingConnector = RecordingConnector(TransportId.FIRESTORE)
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(setOf(connector)),
            conversationStore = InMemoryConversationStore(),
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        identityRegistry.upsertIdentity(
            identity = CanonicalIdentity(id = "self", displayName = "Self"),
            aliases = mapOf(TransportId.FIRESTORE to "self"),
            setAsCurrent = true,
        )
        router.ensureConversation(CanonicalIdentity("other", "Other"))

        router.observeConversation("conversation-1").test {
            advanceUntilIdle()
            connector.emitMessages(
                listOf(
                    ConnectorInboundMessage(
                        messageId = "msg-1",
                        conversationId = "conversation-1",
                        senderId = "self",
                        recipientId = "other",
                        senderName = "Self",
                        body = "Hello",
                        transport = TransportId.FIRESTORE,
                        timestamp = Date(0),
                    )
                )
            )

            advanceUntilIdle()
            var messages = awaitItem()
            if (messages.isEmpty()) {
                messages = awaitItem()
            }
            assertEquals(1, messages.size)
            assertEquals("Hello", messages.first().body)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeConversation clears stored timeline when connector emits empty snapshot`() = scope.runTest {
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore()).apply {
            upsertIdentity(
                identity = CanonicalIdentity(id = "self", displayName = "Self"),
                aliases = mapOf(TransportId.FIRESTORE to "self"),
                setAsCurrent = true,
            )
        }
        val store = InMemoryConversationStore()
        val connector = RecordingConnector(TransportId.FIRESTORE)
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(setOf(connector)),
            conversationStore = store,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val conversation = router.ensureConversation(CanonicalIdentity("other", "Other"))
        advanceUntilIdle()

        val inbound = ConnectorInboundMessage(
            messageId = "msg-1",
            conversationId = conversation.id,
            senderId = "self",
            recipientId = "other",
            senderName = "Self",
            body = "Hello",
            transport = TransportId.FIRESTORE,
            timestamp = Date(0),
        )

        store.observeMessages(conversation.id).test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())

            connector.emitMessages(listOf(inbound))
            advanceUntilIdle()

            val populated = awaitItem()
            assertEquals(1, populated.size)
            assertEquals("Hello", populated.first().body)

            connector.emitMessages(emptyList())
            advanceUntilIdle()

            val cleared = awaitItem()
            assertTrue(cleared.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `send only targets primary connector when hybrid mode falls back to firestore`() = scope.runTest {
        val bridge = fakeBridge(BriarTransportMode.HYBRID)
        val store = InMemoryConversationStore()
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
        val connector = RecordingConnector(TransportId.FIRESTORE)
        val registry = DefaultConnectorRegistry(setOf(connector))
        val router = TransportRouterImpl(
            transportBridge = bridge,
            connectorRegistry = registry,
            conversationStore = store,
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        val message = ConnectorOutboundMessage(
            conversationId = "conversation-1",
            senderId = "self",
            senderName = "Self",
            recipientIds = setOf("other"),
            body = "Hello",
            timestamp = Date(0),
        )

        router.send(message)
        advanceUntilIdle()

        assertEquals(1, connector.sentMessages.size)
        assertEquals(message, connector.sentMessages.first())
    }

    @Test
    fun `observeConversation restarts connector when flow completes`() = scope.runTest {
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
        identityRegistry.upsertIdentity(
            identity = CanonicalIdentity(id = "self", displayName = "Self"),
            aliases = mapOf(TransportId.FIRESTORE to "self"),
            setAsCurrent = true,
        )
        val connector = CompletingConnector(TransportId.FIRESTORE).apply {
            queueMessages(
                listOf(
                    ConnectorInboundMessage(
                        messageId = "msg-1",
                        conversationId = "conversation-1",
                        senderId = "self",
                        recipientId = "other",
                        senderName = "Self",
                        body = "hello",
                        transport = TransportId.FIRESTORE,
                        timestamp = Date(0),
                    )
                )
            )
        }
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(setOf(connector)),
            conversationStore = InMemoryConversationStore(),
            identityRegistry = identityRegistry,
            bridgeOrchestrator = BridgeOrchestrator.NoOp,
            dispatcher = dispatcher,
        )

        router.ensureConversation(CanonicalIdentity("other", "Other"))
        router.observeConversation("conversation-1").test {
            advanceUntilIdle()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, connector.observeCalls)
        advanceUntilIdle()
        assertEquals(0, router.observationJobCount())

        connector.queueMessages(
            listOf(
                ConnectorInboundMessage(
                    messageId = "msg-2",
                    conversationId = "conversation-1",
                    senderId = "self",
                    recipientId = "other",
                    senderName = "Self",
                    body = "world",
                    transport = TransportId.FIRESTORE,
                    timestamp = Date(1),
                )
            )
        )

        router.observeConversation("conversation-1").test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        assertEquals(
            "Router should resubscribe after the prior flow completed",
            2,
            connector.observeCalls,
        )
    }

    @Test
    fun `ensureObservation only installs one connector stream under contention`() = runBlocking {
        // Arrange the registry and seed the default identity the router expects.
        val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore()).apply {
            upsertIdentity(
                identity = CanonicalIdentity(id = "self", displayName = "Self"),
                aliases = mapOf(TransportId.FIRESTORE to "self"),
                setAsCurrent = true,
            )
        }

        // Simple connector that just counts how many times observeMessages is invoked.
        val observeCounter = AtomicInteger(0)
        val connector = CountingConnector(
            transport = TransportId.FIRESTORE,
            counter = observeCounter,
        )

        // Router under test, using the default dispatcher so threads can run concurrently.
        val router = TransportRouterImpl(
            transportBridge = fakeBridge(BriarTransportMode.FIRESTORE),
            connectorRegistry = DefaultConnectorRegistry(setOf(connector)),
            conversationStore = InMemoryConversationStore(),
            identityRegistry = identityRegistry,
            dispatcher = Dispatchers.Default,
        )
        router.ensureConversation(CanonicalIdentity("other", "Other"))

        // Swap in a ConcurrentHashMap whose first put blocks until a second put happens.
        // This forces two threads to race between compute-if-absent style logic.
        // Kick off two observeConversation calls on separate threads so they overlap.
        val startBarrier = CyclicBarrier(3)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(
                executor.submit<Unit> {
                    startBarrier.await()
                    router.observeConversation("conversation-1")
                },
                executor.submit<Unit> {
                    startBarrier.await()
                    router.observeConversation("conversation-1")
                },
            )
            startBarrier.await()
            tasks.forEach { it.get(3, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        // Wait until the first observation job increments the counter.
        withTimeout(3_000) {
            while (observeCounter.get() < 1) {
                delay(10)
            }
        }
        // Give the second thread a moment to attempt its own registration.
        delay(100)

        // If the implementation is correct, the counter stays at 1.
        assertEquals(
            "Only one connector observation should be started per conversation",
            1,
            observeCounter.get(),
        )
    }

    private fun fakeBridge(mode: BriarTransportMode): TransportRuntimeBridge {
        return object : TransportRuntimeBridge {
            private val modeFlow = MutableStateFlow(mode)
            override val currentMode = modeFlow.asStateFlow()
            override val runtimeStatus = MutableStateFlow(com.example.rise.briar.runtime.BriarRuntimeStatus.stopped
            ).asStateFlow()
            override val diagnostics = MutableSharedFlow<com.example.rise.briar.runtime.BriarRuntimeEvent>()
            override val briarChatGateway = MutableStateFlow<com.example.rise.briar.runtime.BriarChatGateway>(
                com.example.rise.briar.runtime.NoOpBriarChatGateway
            ).asStateFlow()
            override val briarContactService = MutableStateFlow<com.example.rise.briar.runtime.BriarContactService>(
                com.example.rise.briar.runtime.NoOpBriarContactService
            ).asStateFlow()
            override fun requireFirestore(caller: String) = Unit
        }
    }

    private class InMemoryConversationStore : ConversationStore {
        private val conversations = ConcurrentHashMap<String, CanonicalConversation>()
        private val messages = ConcurrentHashMap<String, MutableList<CanonicalMessage>>()
        private val observers = ConcurrentHashMap<String, MutableSharedFlow<List<CanonicalMessage>>>()
        var clearAllCalls = 0

        override suspend fun upsertConversation(conversation: CanonicalConversation) {
            conversations[conversation.id] = conversation
        }

        override suspend fun upsertMessages(conversationId: String, messages: List<CanonicalMessage>) {
            if (messages.isEmpty()) {
                this.messages.remove(conversationId)
                observers[conversationId]?.emit(emptyList())
                return
            }
            val list = this.messages.getOrPut(conversationId) { mutableListOf() }
            messages.forEach { message ->
                val index = list.indexOfFirst { it.canonicalMessageId == message.canonicalMessageId }
                if (index >= 0) {
                    list[index] = message
                } else {
                    list += message
                }
            }
            observers[conversationId]?.emit(list.toList())
        }

        override fun observeMessages(conversationId: String): Flow<List<CanonicalMessage>> {
            val flow = observers.getOrPut(conversationId) { MutableSharedFlow(replay = 1) }
            flow.tryEmit(messages[conversationId]?.toList().orEmpty())
            return flow
        }

        override suspend fun getConversation(conversationId: String): CanonicalConversation? {
            return conversations[conversationId]
        }

        override suspend fun clearAll() {
            clearAllCalls += 1
            conversations.clear()
            messages.clear()
            observers.values.forEach { it.tryEmit(emptyList()) }
            observers.clear()
        }
    }

    private class CountingConnector(
        override val transport: TransportId,
        private val counter: AtomicInteger,
    ) : TransportConnector {

        private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)

        override val status: Flow<ConnectorStatus> = statusFlow

        override suspend fun currentIdentity(): CanonicalIdentity =
            CanonicalIdentity(id = "self", displayName = "Self")

        override suspend fun ensureConversation(conversation: CanonicalConversation): String =
            conversation.id.ifBlank { "conversation-1" }

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> = flow {
            counter.incrementAndGet()
            awaitCancellation()
        }

        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
    }

    private class CompletingConnector(
        override val transport: TransportId,
    ) : TransportConnector {

        private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)
        private val emissions = ArrayDeque<List<ConnectorInboundMessage>>()

        var observeCalls: Int = 0
            private set

        override val status: Flow<ConnectorStatus> = statusFlow

        fun queueMessages(messages: List<ConnectorInboundMessage>) {
            emissions += messages
        }

        override suspend fun currentIdentity(): CanonicalIdentity =
            CanonicalIdentity(id = "self", displayName = "Self")

        override suspend fun ensureConversation(conversation: CanonicalConversation): String =
            conversation.id.ifBlank { "conversation-1" }

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> {
            observeCalls += 1
            val next = if (emissions.isEmpty()) null else emissions.removeFirst()
            return if (next == null) {
                emptyFlow()
            } else {
                flow { emit(next) }
            }
        }

        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
    }

    private class RecordingConnector(
        override val transport: TransportId,
    ) : TransportConnector {

        private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)
        private val messagesFlow = MutableSharedFlow<List<ConnectorInboundMessage>>(replay = 1, extraBufferCapacity = 1)

        var ensureConversationCalls = 0
        val sentMessages = mutableListOf<ConnectorOutboundMessage>()
        var connectorIdentity: CanonicalIdentity = CanonicalIdentity(id = "self", displayName = "Self")
        var identityError: Throwable? = null

        fun emitMessages(messages: List<ConnectorInboundMessage>) {
            messagesFlow.tryEmit(messages)
        }

        override val status: Flow<ConnectorStatus> = statusFlow

        override suspend fun currentIdentity(): CanonicalIdentity {
            identityError?.let { throw it }
            return connectorIdentity
        }

        override suspend fun ensureConversation(conversation: CanonicalConversation): String {
            ensureConversationCalls += 1
            return conversation.id.ifBlank { "conversation-1" }
        }

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> = messagesFlow

        override suspend fun sendMessage(message: ConnectorOutboundMessage) {
            sentMessages += message
        }

    }
    private class FakeIdentityRegistryStore(
        initialState: IdentityRegistryStore.StoredState = IdentityRegistryStore.StoredState(emptyMap(), null)
    ) : IdentityRegistryStore {
        var state: IdentityRegistryStore.StoredState = initialState
            private set

        override fun load(): IdentityRegistryStore.StoredState = state

        override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
            val snapshot = records.mapValues { (_, record) ->
                IdentityRecord(
                    canonicalIdentity = record.canonicalIdentity,
                    aliases = record.aliases.toMap(),
                )
            }
            state = IdentityRegistryStore.StoredState(snapshot, currentIdentityId)
        }
    }
    /**
     * Reflectively inspects the router to determine how many observation jobs are currently
     * registered. Used by tests that validate the sign-out/reset paths cancel existing watchers.
     */
    private fun TransportRouterImpl.observationJobCount(): Int {
        val field = TransportRouterImpl::class.java.getDeclaredField("observationJobs")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(this) as MutableMap<*, *>
        return map.size
    }
}
