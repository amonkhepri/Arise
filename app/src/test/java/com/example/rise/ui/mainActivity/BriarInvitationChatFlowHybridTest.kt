package com.example.rise.ui.mainActivity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.rise.data.chat.TransportBackedChatRepository
import com.example.rise.data.people.PersonSummary
import com.example.rise.data.people.RouterPeopleRepository
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.helpers.AppConstants
import com.example.rise.testutil.stubBriarContactService
import com.example.rise.testutil.stubTransportRuntimeBridge
import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
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
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
import com.example.rise.transport.router.TransportRouterImpl
import com.example.rise.transport.store.ConversationStore
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.ui.dashboardNavigation.people.chatActivity.resolveChatLaunchContract
import com.example.rise.util.MainDispatcherRule
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BriarInvitationChatFlowHybridTest {

  @get:Rule
  val dispatcherRule = MainDispatcherRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)

  @Test
  fun `resolved invitation launches hybrid chat and sends first message through router`() = runTest {
    val addedContacts = mutableListOf<Pair<String, String?>>()
    val contactService = stubBriarContactService(
      isAvailable = true,
      onAddContactByLink = { link, alias -> addedContacts += link to alias },
    )
    val bridge = stubTransportRuntimeBridge(
      mode = BriarTransportMode.HYBRID,
      contactService = contactService,
    )
    val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
    val briarConnector = RecordingConnector(transport = TransportId.BRIAR)
    val firestoreConnector = RecordingConnector(transport = TransportId.FIRESTORE).apply {
      transportAliasGenerator = { canonicalId -> "firestore-$canonicalId" }
    }
    val router = TransportRouterImpl(
      transportBridge = bridge,
      connectorRegistry = DefaultConnectorRegistry(setOf(briarConnector, firestoreConnector)),
      conversationStore = InMemoryConversationStore(),
      identityRegistry = identityRegistry,
      dispatcher = StandardTestDispatcher(testScheduler),
    )
    val coordinator = BriarInvitationOnboardingCoordinator(
      useCase = BriarInvitationAcceptanceUseCase(
        contactRepository = BriarContactRepository(bridge),
        identityRegistry = identityRegistry,
        transportRouter = router,
      ),
    )
    val action = BriarInvitationDeepLinkEntrypoint().resolve(
      wrappedInvite(alias = "Alice", inviteId = "INV-42"),
    )

    assertNotNull(action)
    val onboardingAction = requireNotNull(action)
    assertEquals(validLink(), onboardingAction.briarLink)
    assertEquals("Alice", onboardingAction.alias)
    assertEquals("invite:inv-42", onboardingAction.duplicateKey)

    val result = coordinator.accept(context, onboardingAction)
    advanceUntilIdle()

    assertTrue(result is BriarInvitationOnboardingCoordinator.Result.LaunchChat)
    val launchIntent = (result as BriarInvitationOnboardingCoordinator.Result.LaunchChat).intent
    val launchContract = resolveChatLaunchContract(
      userId = launchIntent.getStringExtra(AppConstants.USER_ID),
      userName = launchIntent.getStringExtra(AppConstants.USER_NAME),
      conversationId = launchIntent.getStringExtra(AppConstants.CONVERSATION_ID),
    )
    val conversationId = requireNotNull(launchContract.conversationId)
    val chatViewModel = ChatViewModel(
      chatRepository = TransportBackedChatRepository(router),
      routerPeopleRepository = FakeRouterPeopleRepository(),
      clock = fixedClock,
    )

    chatViewModel.initialiseConversation(
      otherUserId = launchContract.userId,
      otherUserName = launchContract.userName,
      conversationId = conversationId,
    )
    advanceUntilIdle()
    chatViewModel.sendMessage("Hello from hybrid invite")
    advanceUntilIdle()

    assertEquals(listOf(validLink() to null), addedContacts)
    assertEquals(1, briarConnector.ensureConversationCalls)
    assertEquals(1, firestoreConnector.ensureConversationCalls)
    assertTrue(chatViewModel.uiState.value.inputEnabled)
    assertEquals(conversationId, chatViewModel.uiState.value.conversationId)
    assertEquals("link:${validLink()}", launchContract.userId)
    assertEquals("link:${validLink()}", launchContract.userName)
    assertEquals(1, briarConnector.sentMessages.size)
    assertEquals(1, firestoreConnector.sentMessages.size)
    assertEquals(conversationId, briarConnector.sentMessages.single().conversationId)
    assertEquals(
      "firestore-$conversationId",
      firestoreConnector.sentMessages.single().conversationId,
    )
    assertEquals(
      setOf(launchContract.userId),
      briarConnector.sentMessages.single().recipientIds,
    )
    assertEquals(
      setOf(launchContract.userId),
      firestoreConnector.sentMessages.single().recipientIds,
    )
    assertEquals("Hello from hybrid invite", briarConnector.sentMessages.single().body)
  }

  @Test
  fun `resolved invitation stays pending sync in hybrid mode when bootstrap returns whitespace conversation id`() = runTest {
    val addedContacts = mutableListOf<Pair<String, String?>>()
    val contactService = stubBriarContactService(
      isAvailable = true,
      onAddContactByLink = { link, alias -> addedContacts += link to alias },
    )
    val bridge = stubTransportRuntimeBridge(
      mode = BriarTransportMode.HYBRID,
      contactService = contactService,
    )
    val identityRegistry = IdentityRegistryImpl(FakeIdentityRegistryStore())
    val briarConnector = RecordingConnector(transport = TransportId.BRIAR)
    val firestoreConnector = RecordingConnector(transport = TransportId.FIRESTORE).apply {
      transportAliasGenerator = { canonicalId -> "firestore-$canonicalId" }
    }
    val router = TransportRouterImpl(
      transportBridge = bridge,
      connectorRegistry = DefaultConnectorRegistry(setOf(briarConnector, firestoreConnector)),
      conversationStore = InMemoryConversationStore(),
      identityRegistry = identityRegistry,
      dispatcher = StandardTestDispatcher(testScheduler),
    )
    val whitespaceConversationIdRouter = WhitespaceConversationIdRouter(
      delegate = router,
      conversationId = "   ",
    )
    val coordinator = BriarInvitationOnboardingCoordinator(
      useCase = BriarInvitationAcceptanceUseCase(
        contactRepository = BriarContactRepository(bridge),
        identityRegistry = identityRegistry,
        transportRouter = whitespaceConversationIdRouter,
      ),
    )
    val action = BriarInvitationDeepLinkEntrypoint().resolve(
      wrappedInvite(alias = "Alice", inviteId = "INV-42"),
    )

    assertNotNull(action)
    val onboardingAction = requireNotNull(action)

    val result = coordinator.accept(context, onboardingAction)
    advanceUntilIdle()

    assertEquals(listOf(validLink() to null), addedContacts)
    assertEquals(1, whitespaceConversationIdRouter.ensureConversationCalls)
    assertEquals(
      BriarInvitationOnboardingCoordinator.Result.ContactAddedPendingSync("link:${validLink()}"),
      result,
    )
    assertTrue(briarConnector.sentMessages.isEmpty())
    assertTrue(firestoreConnector.sentMessages.isEmpty())
  }

  private fun validLink(): String = "briar://${"b".repeat(53)}"

  private fun wrappedInvite(alias: String, inviteId: String): String {
    return "arise://briar/invite?link=${encode(validLink())}&alias=${encode(alias)}&inviteId=${encode(inviteId)}"
  }

  private fun encode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
  }

  private class FakeRouterPeopleRepository : RouterPeopleRepository {
    private val personFlows = mutableMapOf<String, MutableSharedFlow<PersonSummary?>>()

    override val syncPeopleErrors: Flow<Throwable> = MutableSharedFlow()

    override fun observePeople(): Flow<List<PersonSummary>> = MutableSharedFlow()

    override fun observePerson(personId: String): Flow<PersonSummary?> =
      personFlows.getOrPut(personId) {
        MutableSharedFlow<PersonSummary?>(replay = 1).also { flow ->
          flow.tryEmit(null)
        }
      }

    override suspend fun findPerson(personId: String): PersonSummary? = null
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

  private class RecordingConnector(
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

  private class WhitespaceConversationIdRouter(
    private val delegate: TransportRouterImpl,
    private val conversationId: String,
  ) : com.example.rise.transport.router.TransportRouter by delegate {
    var ensureConversationCalls = 0

    override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
      ensureConversationCalls += 1
      return delegate.ensureConversation(otherIdentity).copy(id = conversationId)
    }
  }

  private class FakeIdentityRegistryStore(
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
}
