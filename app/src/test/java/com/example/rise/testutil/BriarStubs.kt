package com.example.rise.testutil

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarConversation
import com.example.rise.briar.runtime.BriarConversationDescriptor
import com.example.rise.briar.runtime.BriarMessage
import com.example.rise.briar.runtime.BriarOutboundMessage
import com.example.rise.briar.runtime.BriarPresenceStatus
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.CapabilityDescriptor
import com.example.rise.transport.router.ConnectorCapabilities
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.transport.router.DefaultConnectorRegistry
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
import com.example.rise.transport.router.TransportRouterImpl
import com.example.rise.transport.store.ConversationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.util.concurrent.ConcurrentHashMap

fun stubBriarChatGateway(
    isAvailable: Boolean = false,
    messagesFlowFactory: (String) -> Flow<List<BriarMessage>> = { flowOf(emptyList()) },
    onSend: (BriarOutboundMessage) -> Unit = {},
): BriarChatGateway {
    return object : BriarChatGateway {
        override val isAvailable: Boolean = isAvailable

        override suspend fun currentIdentity() = null

        override suspend fun ensureConversation(
            descriptor: BriarConversationDescriptor
        ): BriarConversation {
            return BriarConversation(descriptor.canonicalConversationId, descriptor.canonicalConversationId)
        }

        override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
            messagesFlowFactory(conversationId)

        override suspend fun sendMessage(message: BriarOutboundMessage) {
            onSend(message)
        }
    }
}

fun stubBriarContactService(
    isAvailable: Boolean = false,
    contactsFlow: Flow<List<BriarContact>> = flowOf(emptyList()),
    onAddContactByLink: (String, String?) -> Unit = { _, _ -> },
): BriarContactService {
    return object : BriarContactService {
        override val isAvailable: Boolean = isAvailable
        override suspend fun addContactByLink(link: String, alias: String?) {
            onAddContactByLink(link, alias)
        }

        override fun observeContacts(): Flow<List<BriarContact>> = contactsFlow
    }
}

fun stubTransportRuntimeBridge(
    mode: BriarTransportMode = BriarTransportMode.BRIAR_ONLY,
    runtimeStatus: BriarRuntimeStatus = BriarRuntimeStatus(BriarRuntimePhase.RUNNING),
    chatGateway: BriarChatGateway = stubBriarChatGateway(),
    contactService: BriarContactService = stubBriarContactService(),
): TransportRuntimeBridge {
    val modeFlow = MutableStateFlow(mode)
    val runtimeStatusFlow = MutableStateFlow(runtimeStatus)
    val diagnosticsFlow = MutableSharedFlow<BriarRuntimeEvent>()
    val chatGatewayFlow = MutableStateFlow(chatGateway)
    val contactServiceFlow = MutableStateFlow(contactService)
    return object : TransportRuntimeBridge {
        override val currentMode = modeFlow
        override val runtimeStatus = runtimeStatusFlow
        override val diagnostics = diagnosticsFlow
        override val briarChatGateway = chatGatewayFlow
        override val briarContactService = contactServiceFlow

        override fun requireFirestore(caller: String) = Unit
    }
}

fun stubBriarRuntimeManager(
    runtimeStatus: BriarRuntimeStatus = BriarRuntimeStatus.stopped,
    onEnsureStarted: (() -> Unit)? = null,
): BriarRuntimeManager {
    val statusFlow = MutableStateFlow(runtimeStatus)
    val diagnosticsFlow = MutableSharedFlow<BriarRuntimeEvent>()
    val chatGatewayFlow = MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway)
    val contactServiceFlow = MutableStateFlow<BriarContactService>(NoOpBriarContactService)
    return object : BriarRuntimeManager {
        override val status: StateFlow<BriarRuntimeStatus> = statusFlow
        override val diagnostics: SharedFlow<BriarRuntimeEvent> = diagnosticsFlow
        override val chatGateway: StateFlow<BriarChatGateway> = chatGatewayFlow
        override val contactService: StateFlow<BriarContactService> = contactServiceFlow

        override suspend fun ensureStarted() {
            onEnsureStarted?.invoke()
        }

        override suspend fun createAccount(name: String, password: String): Boolean = true

        override suspend fun signIn(password: String): Boolean = true

        override suspend fun stop() = Unit
    }
}

fun briarContact(
    canonicalId: String = "briar-user",
    alias: String = "briar-alias",
    presence: BriarPresenceStatus = BriarPresenceStatus.UNKNOWN,
): BriarContact = BriarContact(
    canonicalId = canonicalId,
    transportAlias = canonicalId,
    displayName = alias,
    presence = presence,
)

data class RecordingTransportRouterFixture(
    val router: TransportRouterImpl,
    val identityRegistry: IdentityRegistryImpl,
    val briarConnector: RecordingTransportConnector,
    val firestoreConnector: RecordingTransportConnector,
)

fun createRecordingTransportRouterFixture(
    transportBridge: TransportRuntimeBridge,
    testScheduler: TestCoroutineScheduler,
): RecordingTransportRouterFixture {
    val identityRegistry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
    val briarConnector = RecordingTransportConnector(transport = TransportId.BRIAR)
    val firestoreConnector = RecordingTransportConnector(transport = TransportId.FIRESTORE).apply {
        transportAliasGenerator = { canonicalId -> "firestore-$canonicalId" }
    }
    val router = TransportRouterImpl(
        transportBridge = transportBridge,
        connectorRegistry = DefaultConnectorRegistry(setOf(briarConnector, firestoreConnector)),
        conversationStore = InMemoryConversationStore(),
        identityRegistry = identityRegistry,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
    return RecordingTransportRouterFixture(
        router = router,
        identityRegistry = identityRegistry,
        briarConnector = briarConnector,
        firestoreConnector = firestoreConnector,
    )
}

class RecordingTransportConnector(
    override val transport: TransportId,
) : TransportConnector {
    private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)
    private val lifecycleFlow = MutableStateFlow(ConnectorLifecycleState.READY)
    private val capabilityFlow = MutableStateFlow(
        ConnectorCapabilities(
            mapOf("messages" to CapabilityDescriptor(1, mapOf("enabled" to "true"))),
        ),
    )
    private val messagesFlow =
        MutableSharedFlow<List<ConnectorInboundMessage>>(replay = 1, extraBufferCapacity = 1)

    var ensureConversationCalls = 0
    val sentMessages = mutableListOf<ConnectorOutboundMessage>()
    var transportAliasGenerator: ((String) -> String)? = null

    override val status: StateFlow<ConnectorStatus>
        get() = statusFlow

    override val lifecycle: StateFlow<ConnectorLifecycleState>
        get() = lifecycleFlow

    override val capabilities: StateFlow<ConnectorCapabilities>
        get() = capabilityFlow

    override suspend fun currentIdentity(): CanonicalIdentity =
        CanonicalIdentity(id = "self", displayName = "Self")

    override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId {
        ensureConversationCalls += 1
        val canonicalId = conversation.id.ifBlank { "conversation-1" }
        val transportAlias = transportAliasGenerator?.invoke(canonicalId) ?: canonicalId
        return TransportConversationId(
            canonicalId = canonicalId,
            transportConversationId = transportAlias,
        )
    }

    override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> =
        messagesFlow

    override suspend fun sendMessage(message: ConnectorOutboundMessage) {
        sentMessages += message
    }
}

private class InMemoryConversationStore : ConversationStore {
    private val conversations = ConcurrentHashMap<String, CanonicalConversation>()
    private val messages = ConcurrentHashMap<String, MutableList<CanonicalMessage>>()
    private val observers = ConcurrentHashMap<String, MutableSharedFlow<List<CanonicalMessage>>>()
    private val aliases = ConcurrentHashMap<Pair<String, TransportId>, String>()

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
            val existingIndex = list.indexOfFirst { it.canonicalMessageId == message.canonicalMessageId }
            if (existingIndex >= 0) {
                list[existingIndex] = message
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

    override suspend fun upsertAlias(
        conversationId: String,
        transportId: TransportId,
        alias: String,
    ) {
        aliases[conversationId to transportId] = alias
    }

    override suspend fun getAlias(
        conversationId: String,
        transportId: TransportId,
    ): String? = aliases[conversationId to transportId]

    override suspend fun clearAll() {
        conversations.clear()
        messages.clear()
        observers.values.forEach { it.tryEmit(emptyList()) }
        observers.clear()
        aliases.clear()
    }
}

private class InMemoryIdentityRegistryStore(
    initialState: IdentityRegistryStore.StoredState =
        IdentityRegistryStore.StoredState(emptyMap(), null),
) : IdentityRegistryStore {
    private var state: IdentityRegistryStore.StoredState = initialState

    override fun load(): IdentityRegistryStore.StoredState = state

    override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
        val copiedRecords = records.mapValues { (_, record) ->
            IdentityRecord(
                canonicalIdentity = record.canonicalIdentity,
                aliases = record.aliases.toMap(),
                profile = record.profile,
            )
        }
        state = IdentityRegistryStore.StoredState(copiedRecords, currentIdentityId)
    }
}
