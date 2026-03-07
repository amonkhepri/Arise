package com.example.rise.ui.dashboardNavigation.people.chatActivity

import app.cash.turbine.test
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.ChatUser
import com.example.rise.data.people.PersonSummary
import com.example.rise.data.people.RouterPeopleRepository
import com.example.rise.models.TextMessage
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)

    @org.junit.Test
    fun `initialiseConversation loads user, channel, and messages`() = runTest {
        val repository = FakeChatRepository().apply {
            messages.tryEmit(emptyList())
        }
        val peopleRepository = FakeRouterPeopleRepository()
        val viewModel = ChatViewModel(repository, peopleRepository, fixedClock)

        viewModel.initialiseConversation(otherUserId = "other", otherUserName = "Bob")
        advanceUntilIdle()
        val initialState = viewModel.uiState.value
        assertTrue("Expected input enabled. State: $initialState", initialState.inputEnabled)
        assertEquals("Expected title to update. State: $initialState", "Bob", initialState.title)
        assertEquals(
            "Expected conversation id to match. State: $initialState",
            repository.conversationId,
            initialState.conversationId,
        )

        val newMessages = listOf(repository.sampleMessage)
        repository.messages.tryEmit(newMessages)
        advanceUntilIdle()

        assertEquals(newMessages, viewModel.uiState.value.messages)
    }

    @org.junit.Test
    fun `initialiseConversation uses provided conversation id and still enables input plus message observation`() = runTest {
        val repository = FakeChatRepository().apply {
            messages.tryEmit(emptyList())
        }
        val peopleRepository = FakeRouterPeopleRepository()
        val viewModel = ChatViewModel(repository, peopleRepository, fixedClock)
        val resolvedConversationId = "resolved-conversation-456"

        viewModel.initialiseConversation(
            otherUserId = "other",
            otherUserName = "Bob",
            conversationId = resolvedConversationId,
        )
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertTrue("Expected input enabled. State: $initialState", initialState.inputEnabled)
        assertEquals(
            "Expected provided conversation id to be used. State: $initialState",
            resolvedConversationId,
            initialState.conversationId,
        )
        assertEquals(
            "Expected getOrCreateConversation to be skipped when a conversation id is supplied.",
            0,
            repository.getOrCreateConversationCalls,
        )
        assertEquals(listOf(resolvedConversationId), repository.observedConversationIds)

        val newMessages = listOf(repository.sampleMessage)
        repository.messages.tryEmit(newMessages)
        advanceUntilIdle()

        assertEquals(newMessages, viewModel.uiState.value.messages)
    }

    @org.junit.Test
    fun `sendMessage delegates to repository`() = runTest {
        val repository = FakeChatRepository().apply { messages.tryEmit(emptyList()) }
        val peopleRepository = FakeRouterPeopleRepository()
        val viewModel = ChatViewModel(repository, peopleRepository, fixedClock)

        viewModel.initialiseConversation("other", "Bob")
        advanceUntilIdle()
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        val sent = repository.sentMessages.first()
        assertEquals("Hello", sent.text)
        assertEquals("self", sent.senderId)
        assertEquals("other", sent.recipientId)
    }

    @org.junit.Test
    fun `scheduleMessage emits time picker event and confirm schedules alarm`() = runTest {
        val repository = FakeChatRepository().apply { messages.tryEmit(emptyList()) }
        val peopleRepository = FakeRouterPeopleRepository()
        val viewModel = ChatViewModel(repository, peopleRepository, fixedClock)
        viewModel.initialiseConversation("other", "Bob")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.scheduleMessage("Later")
            val showPickerEvent = awaitItem()
            assertTrue(showPickerEvent is ChatViewModel.ChatEvent.ShowTimePicker)
            val picker = showPickerEvent as ChatViewModel.ChatEvent.ShowTimePicker
            assertEquals("Later", picker.messageText)

            viewModel.confirmScheduleMessage("Later", 42_000L)
            val scheduleEvent = awaitItem()
            assertTrue(scheduleEvent is ChatViewModel.ChatEvent.ScheduleDelayedMessage)
            val schedule = scheduleEvent as ChatViewModel.ChatEvent.ScheduleDelayedMessage
            assertEquals("other", schedule.otherUserId)
            assertEquals(repository.conversationId, schedule.conversationId)
            assertEquals("Later", schedule.message.text)
            assertEquals(42_000L, schedule.timeInMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `presence updates when repository emits changes`() = runTest {
        val chatRepository = FakeChatRepository().apply { messages.tryEmit(emptyList()) }
        val peopleRepository = FakeRouterPeopleRepository()
        val viewModel = ChatViewModel(chatRepository, peopleRepository, fixedClock)

        viewModel.initialiseConversation("other", "Bob")
        advanceUntilIdle()

        peopleRepository.emitPerson(
            PersonSummary(
                id = "other",
                name = "Bob",
                bio = "",
                profilePicturePath = null,
                presence = PresenceStatus.ONLINE,
            )
        )
        advanceUntilIdle()
        assertEquals(PresenceStatus.ONLINE, viewModel.uiState.value.presence)

        peopleRepository.emitPerson(
            PersonSummary(
                id = "other",
                name = "Bob",
                bio = "",
                profilePicturePath = null,
                presence = PresenceStatus.OFFLINE,
            )
        )
        advanceUntilIdle()
        assertEquals(PresenceStatus.OFFLINE, viewModel.uiState.value.presence)
    }

    private class FakeChatRepository : ChatRepository {
        val sampleMessage = TextMessage(
            text = "Hi",
            time = Date.from(Instant.parse("2024-01-01T00:00:00Z")),
            senderId = "other",
            recipientId = "self",
            senderName = "Bob"
        )
        val messages = MutableSharedFlow<List<TextMessage>>(replay = 1)
        val sentMessages = mutableListOf<TextMessage>()
        val conversationId = "conversation-123"
        val observedConversationIds = mutableListOf<String>()
        var getOrCreateConversationCalls = 0
        override suspend fun getCurrentUser(): ChatUser = ChatUser(id = "self", displayName = "Alice")
        override suspend fun getOrCreateConversation(otherUserId: String, otherUserName: String): String {
            getOrCreateConversationCalls += 1
            return conversationId
        }
        override fun observeMessages(conversationId: String): Flow<List<TextMessage>> {
            observedConversationIds += conversationId
            return messages
        }
        override suspend fun sendMessage(conversationId: String, message: TextMessage) {
            sentMessages += message
        }
    }

    private class FakeRouterPeopleRepository : RouterPeopleRepository {
        private val peopleFlow = MutableSharedFlow<List<PersonSummary>>(replay = 1)
        private val personFlows = mutableMapOf<String, MutableSharedFlow<PersonSummary?>>()
        override val syncPeopleErrors: Flow<Throwable> = MutableSharedFlow()

        override fun observePeople(): Flow<List<PersonSummary>> = peopleFlow

        override fun observePerson(personId: String): Flow<PersonSummary?> =
            personFlows.getOrPut(personId) {
                MutableSharedFlow<PersonSummary?>(replay = 1).also { flow ->
                    flow.tryEmit(null)
                }
            }

        override suspend fun findPerson(personId: String): PersonSummary? = null

        fun emitPerson(summary: PersonSummary) {
            val flow = personFlows.getOrPut(summary.id) {
                MutableSharedFlow<PersonSummary?>(replay = 1)
            }
            flow.tryEmit(summary)
        }
    }
}
