package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import app.cash.turbine.test
import com.example.rise.data.people.PersonSummary
import com.example.rise.data.people.RouterPeopleRepository
import com.example.rise.transport.briar.invite.BriarInvitationRejectionReason
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @org.junit.Test
    fun `start observes people and updates state`() = runTest {
        val repository = FakeRouterPeopleRepository()
        val viewModel = PeopleViewModel(repository)

        viewModel.start()
        advanceUntilIdle()
        assertTrue("Expected loading before people emission", viewModel.uiState.value.isLoading)

        val entries = listOf(
            PersonSummary(id = "a", name = "Alice", bio = "Bio", profilePicturePath = null, presence = PresenceStatus.ONLINE)
        )
        repository.emit(entries)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(entries, state.people)
        assertEquals(false, state.isLoading)
    }

    @org.junit.Test
    fun `onPersonSelected emits navigation event`() = runTest {
        val repository = FakeRouterPeopleRepository()
        val viewModel = PeopleViewModel(repository)
        val summary = PersonSummary(id = "a", name = "Alice", bio = "Bio", profilePicturePath = null, presence = PresenceStatus.OFFLINE)

        viewModel.start()
        advanceUntilIdle()
        repository.emit(listOf(summary))
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onPersonSelected(summary)
            val event = awaitItem()
            assertTrue(event is PeopleViewModel.PeopleEvent.OpenChat)
            val openChat = event as PeopleViewModel.PeopleEvent.OpenChat
            assertEquals(summary.id, openChat.personId)
            assertEquals(summary.name, openChat.personName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onPersonSelected emits pending-sync message for pending briar contact`() = runTest {
        val repository = FakeRouterPeopleRepository()
        val viewModel = PeopleViewModel(repository)
        val summary = PersonSummary(
            id = "pending:abc123",
            name = "Alice",
            bio = "Bio",
            profilePicturePath = null,
            presence = PresenceStatus.UNKNOWN,
        )

        viewModel.start()
        advanceUntilIdle()
        repository.emit(listOf(summary))
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onPersonSelected(summary)

            assertEquals(
                PeopleViewModel.PeopleEvent.ShowMessage(
                    "Briar contact added. Wait for Alice to finish connecting, then open the chat from People."
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `start surfaces sync errors`() = runTest {
        val repository = FakeRouterPeopleRepository()
        val viewModel = PeopleViewModel(repository)

        viewModel.start()
        advanceUntilIdle()

        repository.emitError(IllegalStateException("boom"))
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.errorMessage)
    }

    @org.junit.Test
    fun `onShareMyRawBriarLinkRequested emits share event with raw link`() = runTest {
        val repository = FakeRouterPeopleRepository()
        val viewModel = createViewModel(
            repository = repository,
            shareMyRawBriarLink = { "briar://raw-link" },
        )

        viewModel.events.test {
            viewModel.onShareMyRawBriarLinkRequested()
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.ShareMyRawBriarLink("briar://raw-link"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onAddByLinkRequested emits prompt event`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onAddByLinkRequested()

            assertEquals(PeopleViewModel.PeopleEvent.PromptAddByLink, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onRawBriarLinkSubmitted launches chat for accepted invitation`() = runTest {
        val launchContract = ChatLaunchContract(
            userId = "duplicate-key",
            userName = "Alice",
            conversationId = "conversation-1",
        )
        val viewModel = createViewModel(
            addByRawBriarLink = { rawLink ->
                assertEquals("briar://invite", rawLink)
                BriarManualInvitationCoordinator.Result.LaunchChat(launchContract)
            },
        )

        viewModel.events.test {
            viewModel.onRawBriarLinkSubmitted("  briar://invite  ")
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.LaunchChatFromAddedLink(launchContract),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onRawBriarLinkSubmitted emits pending-sync message when contact is added without conversation`() = runTest {
        val viewModel = createViewModel(
            addByRawBriarLink = { rawLink ->
                assertEquals("briar://invite", rawLink)
                BriarManualInvitationCoordinator.Result.ContactAddedPendingSync("Alice")
            },
        )

        viewModel.events.test {
            viewModel.onRawBriarLinkSubmitted("briar://invite")
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.ShowMessage(
                    "Briar contact added. Wait for Alice to finish connecting, then open the chat from People."
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onRawBriarLinkSubmitted emits message for invalid invitation`() = runTest {
        val viewModel = createViewModel(
            addByRawBriarLink = {
                BriarManualInvitationCoordinator.Result.RejectedInvitation(
                    BriarInvitationRejectionReason.INVALID,
                )
            },
        )

        viewModel.events.test {
            viewModel.onRawBriarLinkSubmitted("briar://bad")
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.ShowMessage("Invalid Briar invitation link."),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onRawBriarLinkSubmitted emits message for duplicate invitation`() = runTest {
        val viewModel = createViewModel(
            addByRawBriarLink = {
                BriarManualInvitationCoordinator.Result.RejectedInvitation(
                    BriarInvitationRejectionReason.DUPLICATE,
                )
            },
        )

        viewModel.events.test {
            viewModel.onRawBriarLinkSubmitted("briar://duplicate")
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.ShowMessage(
                    "This Briar invitation link was already used."
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onRawBriarLinkSubmitted emits message for expired invitation`() = runTest {
        val viewModel = createViewModel(
            addByRawBriarLink = {
                BriarManualInvitationCoordinator.Result.RejectedInvitation(
                    BriarInvitationRejectionReason.EXPIRED,
                )
            },
        )

        viewModel.events.test {
            viewModel.onRawBriarLinkSubmitted("briar://expired")
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.ShowMessage(
                    "This Briar invitation link has expired. Ask for a new one."
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `onShareMyRawBriarLinkRequested emits message when sharing fails`() = runTest {
        val viewModel = createViewModel(
            shareMyRawBriarLink = { throw IllegalStateException("runtime unavailable") },
        )

        viewModel.events.test {
            viewModel.onShareMyRawBriarLinkRequested()
            advanceUntilIdle()

            assertEquals(
                PeopleViewModel.PeopleEvent.ShowMessage("runtime unavailable"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(
        repository: FakeRouterPeopleRepository = FakeRouterPeopleRepository(),
        shareMyRawBriarLink: suspend () -> String = { "briar://self-link" },
        addByRawBriarLink: suspend (String) -> BriarManualInvitationCoordinator.Result = {
            BriarManualInvitationCoordinator.Result.InvitationFailed(IllegalStateException("unused"))
        },
    ): PeopleViewModel = PeopleViewModel(
        routerPeopleRepository = repository,
        shareMyRawBriarLink = shareMyRawBriarLink,
        addByRawBriarLink = addByRawBriarLink,
    )

    private class FakeRouterPeopleRepository : RouterPeopleRepository {
        private val people = MutableSharedFlow<List<PersonSummary>>(replay = 1)
        private val errorsFlow = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
        private var latest: List<PersonSummary> = emptyList()

        fun emit(entries: List<PersonSummary>) {
            latest = entries
            people.tryEmit(entries)
        }

        fun emitError(error: Throwable) {
            errorsFlow.tryEmit(error)
        }

        override val syncPeopleErrors = errorsFlow

        override fun observePeople() = people

        override fun observePerson(personId: String) =
            people.map { entries -> entries.firstOrNull { it.id == personId } }

        override suspend fun findPerson(personId: String): PersonSummary? =
            latest.firstOrNull { it.id == personId }
    }
}
