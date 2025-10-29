package com.example.rise.ui.dashboardNavigation.people.chatActivity

import app.cash.turbine.test
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.ChatUser
import com.example.rise.models.TextMessage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle

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
        val viewModel = ChatViewModel(repository, fixedClock)

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
    fun `sendMessage delegates to repository`() = runTest {
        val repository = FakeChatRepository().apply { messages.tryEmit(emptyList()) }
        val viewModel = ChatViewModel(repository, fixedClock)

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
        val viewModel = ChatViewModel(repository, fixedClock)
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
        override suspend fun getCurrentUser(): ChatUser = ChatUser(id = "self", displayName = "Alice")
        override suspend fun getOrCreateConversation(otherUserId: String, otherUserName: String): String = conversationId
        override fun observeMessages(conversationId: String) = messages
        override suspend fun sendMessage(conversationId: String, message: TextMessage) {
            sentMessages += message
        }
    }
}
