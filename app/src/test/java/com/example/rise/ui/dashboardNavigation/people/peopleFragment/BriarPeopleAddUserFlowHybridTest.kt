package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import app.cash.turbine.test
import com.example.rise.data.chat.TransportBackedChatRepository
import com.example.rise.data.people.PersonSummary
import com.example.rise.data.people.RouterPeopleRepository
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.testutil.createRecordingTransportRouterFixture
import com.example.rise.testutil.stubBriarContactService
import com.example.rise.testutil.stubTransportRuntimeBridge
import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatViewModel
import com.example.rise.util.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BriarPeopleAddUserFlowHybridTest {

  @get:Rule
  val dispatcherRule = MainDispatcherRule()

  private val fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)

  @Test
  fun `raw briar link add from people screen opens hybrid chat and mirrors first message through briar and firestore`() = runTest {
    val addedContacts = mutableListOf<Pair<String, String?>>()
    val contactService = stubBriarContactService(
      isAvailable = true,
      onAddContactByLink = { link, alias -> addedContacts += link to alias },
    )
    val bridge = stubTransportRuntimeBridge(
      mode = BriarTransportMode.HYBRID,
      contactService = contactService,
    )
    val routerFixture = createRecordingTransportRouterFixture(
      transportBridge = bridge,
      testScheduler = testScheduler,
    )
    val peopleViewModel = PeopleViewModel(
      routerPeopleRepository = FakeRouterPeopleRepository(),
      briarContactRepository = BriarContactRepository(bridge),
      briarManualInvitationCoordinator = BriarManualInvitationCoordinator(
        BriarInvitationAcceptanceUseCase(
          contactRepository = BriarContactRepository(bridge),
          identityRegistry = routerFixture.identityRegistry,
          transportRouter = routerFixture.router,
        ),
      ),
    )
    val promptCopy = samplePromptCopy()
    var launchContract: ChatLaunchContract? = null

    peopleViewModel.events.test {
      peopleViewModel.onAddByLinkRequested()

      assertEquals(
        PeopleInviteUiAction.PromptAddByLink(promptCopy),
        resolvePeopleInviteUiAction(
          event = awaitItem(),
          chooserTitle = "unused",
          promptCopy = promptCopy,
        ),
      )

      peopleViewModel.onRawBriarLinkSubmitted(validLink())
      advanceUntilIdle()

      val inviteAction = resolvePeopleInviteUiAction(
        event = awaitItem(),
        chooserTitle = "unused",
        promptCopy = promptCopy,
      )
      assertTrue(inviteAction is PeopleInviteUiAction.OpenAddedContactChat)
      launchContract = (inviteAction as PeopleInviteUiAction.OpenAddedContactChat).launchContract
      cancelAndIgnoreRemainingEvents()
    }

    val resolvedLaunchContract = requireNotNull(launchContract)
    val resolvedConversationId = requireNotNull(resolvedLaunchContract.conversationId)
    val chatViewModel = ChatViewModel(
      chatRepository = TransportBackedChatRepository(routerFixture.router),
      routerPeopleRepository = FakeRouterPeopleRepository(),
      clock = fixedClock,
    )

    chatViewModel.initialiseConversation(
      otherUserId = resolvedLaunchContract.userId,
      otherUserName = resolvedLaunchContract.userName,
      conversationId = resolvedConversationId,
    )
    advanceUntilIdle()
    chatViewModel.sendMessage("Hello from people invite")
    advanceUntilIdle()

    assertEquals(listOf(validLink() to null), addedContacts)
    assertTrue(chatViewModel.uiState.value.inputEnabled)
    assertEquals(resolvedConversationId, chatViewModel.uiState.value.conversationId)
    assertEquals(resolvedConversationId, resolvedLaunchContract.conversationId)
    assertEquals("link:${validLink()}", resolvedLaunchContract.userId)
    assertEquals("link:${validLink()}", resolvedLaunchContract.userName)
    assertEquals(1, routerFixture.briarConnector.ensureConversationCalls)
    assertEquals(1, routerFixture.firestoreConnector.ensureConversationCalls)
    assertEquals(1, routerFixture.briarConnector.sentMessages.size)
    assertEquals(1, routerFixture.firestoreConnector.sentMessages.size)
    assertEquals(resolvedConversationId, routerFixture.briarConnector.sentMessages.single().conversationId)
    assertEquals(
      "firestore-$resolvedConversationId",
      routerFixture.firestoreConnector.sentMessages.single().conversationId,
    )
    assertEquals(
      setOf(resolvedLaunchContract.userId),
      routerFixture.briarConnector.sentMessages.single().recipientIds,
    )
    assertEquals(
      setOf(resolvedLaunchContract.userId),
      routerFixture.firestoreConnector.sentMessages.single().recipientIds,
    )
    assertEquals("Hello from people invite", routerFixture.briarConnector.sentMessages.single().body)
    assertEquals("Hello from people invite", routerFixture.firestoreConnector.sentMessages.single().body)
  }

  private fun validLink(): String = "briar://${"b".repeat(53)}"

  private fun samplePromptCopy() = AddByRawBriarLinkPromptCopy(
    title = "Add contact by Briar link",
    message = "Paste a raw Briar invitation link to add a contact and start chatting.",
    hint = "briar://...",
    confirmLabel = "Add contact",
  )

  private class FakeRouterPeopleRepository : RouterPeopleRepository {
    private val personFlows = mutableMapOf<String, MutableSharedFlow<PersonSummary?>>()

    override val syncPeopleErrors: Flow<Throwable> = MutableSharedFlow()

    override fun observePeople(): Flow<List<PersonSummary>> = MutableSharedFlow()

    override fun observePerson(personId: String): Flow<PersonSummary?> =
      personFlows.getOrPut(personId) {
        MutableSharedFlow<PersonSummary?>(replay = 1).also { flow ->
          flow.tryEmit(
            PersonSummary(
              id = personId,
              name = personId,
              bio = "",
              profilePicturePath = null,
              presence = PresenceStatus.UNKNOWN,
            ),
          )
        }
      }

    override suspend fun findPerson(personId: String): PersonSummary? = null
  }
}
