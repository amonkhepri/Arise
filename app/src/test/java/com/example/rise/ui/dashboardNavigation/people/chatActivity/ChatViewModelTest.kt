package com.example.rise.ui.dashboardNavigation.people.chatActivity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.google.firebase.firestore.ListenerRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: FakeChatRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        repository = FakeChatRepository()
        viewModel = ChatViewModel(repository)
    }

    @Test
    fun `loadConversation emits messages with initialization flag`() {
        val observer = Observer<ChatViewModel.ChatUiState> { }
        viewModel.uiState.observeForever(observer)

        viewModel.loadConversation("other-user")

        val firstMessage = sampleMessage("first")
        repository.emitMessages(listOf(firstMessage))

        assertEquals(listOf(firstMessage), viewModel.uiState.value?.messages)
        assertTrue(viewModel.uiState.value?.shouldInitRecycler == true)

        val secondMessage = sampleMessage("second")
        repository.emitMessages(listOf(secondMessage))

        assertEquals(listOf(secondMessage), viewModel.uiState.value?.messages)
        assertFalse(viewModel.uiState.value?.shouldInitRecycler ?: true)

        viewModel.uiState.removeObserver(observer)
    }

    @Test
    fun `sendMessage delegates to repository and clears input`() {
        viewModel.loadConversation("other-user")
        repository.emitMessages(emptyList())

        val events = mutableListOf<ChatViewModel.ChatEvent>()
        viewModel.events.observeForever { event ->
            event.getContentIfNotHandled()?.let { events.add(it) }
        }

        viewModel.sendMessage(" hello ")

        val sentMessage = repository.lastSentMessage
        assertNotNull(sentMessage)
        assertEquals("hello", sentMessage?.text)
        assertEquals(repository.currentUser.id, sentMessage?.senderId)
        assertEquals("other-user", sentMessage?.recipientId)

        assertTrue(events.lastOrNull() is ChatViewModel.ChatEvent.ClearMessageInput)
    }

    @Test
    fun `scheduleMessage emits navigation event`() {
        viewModel.loadConversation("other-user")
        repository.emitMessages(emptyList())

        var scheduledEvent: ChatViewModel.ChatEvent.LaunchDelayedMessage? = null
        viewModel.events.observeForever { event ->
            val content = event.getContentIfNotHandled()
            if (content is ChatViewModel.ChatEvent.LaunchDelayedMessage) {
                scheduledEvent = content
            }
        }

        viewModel.scheduleMessage("Reminder")

        val event = scheduledEvent
        assertNotNull(event)
        assertEquals("other-user", event?.otherUserId)
        assertEquals(repository.channelId, event?.channelId)
        assertEquals("Reminder", event?.message?.text)
    }

    private fun sampleMessage(text: String) = TextMessage(
        text = text,
        time = Date(),
        senderId = "sender",
        recipientId = "recipient",
        senderName = "Sender"
    )

    private class FakeChatRepository : ChatRepository {
        val currentUser = ChatUser(
            "sender-id",
            User(name = "Sender", bio = "", profilePicturePath = null, registrationTokens = mutableListOf())
        )
        var channelId: String = "channel-id"
        var lastSentMessage: TextMessage? = null
        private var listener: ((List<TextMessage>) -> Unit)? = null

        override fun fetchCurrentUser(onResult: (ChatUser) -> Unit) {
            onResult(currentUser)
        }

        override fun getOrCreateChatChannel(otherUserId: String, onResult: (String) -> Unit) {
            onResult(channelId)
        }

        override fun listenForMessages(
            channelId: String,
            onMessages: (List<TextMessage>) -> Unit
        ): ListenerRegistration {
            listener = onMessages
            return object : ListenerRegistration {
                override fun remove() {}
            }
        }

        override fun sendMessage(channelId: String, message: TextMessage) {
            this.lastSentMessage = message
        }

        override fun removeListener(registration: ListenerRegistration) {
            registration.remove()
        }

        fun emitMessages(messages: List<TextMessage>) {
            listener?.invoke(messages)
        }
    }
}
