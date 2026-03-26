package com.example.rise.ui.dashboardNavigation.people.chatActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.ChatUser
import com.example.rise.data.people.RouterPeopleRepository
import com.example.rise.models.TextMessage
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.BriarOnlyOperationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.*

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val routerPeopleRepository: RouterPeopleRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    data class ChatUiState(
        val title: String = "",
        val otherUserId: String? = null,
        val conversationId: String? = null,
        val currentUser: ChatUser? = null,
        val messages: List<TextMessage> = emptyList(),
        val isLoading: Boolean = true,
        val inputEnabled: Boolean = false,
        val errorMessage: String? = null,
        val presence: PresenceStatus = PresenceStatus.UNKNOWN,
    )

    sealed interface ChatEvent {
        data class ShowTimePicker(
            val messageText: String,
        ) : ChatEvent
        data class ScheduleDelayedMessage(
            val message: TextMessage,
            val otherUserId: String,
            val conversationId: String,
            val timeInMillis: Long,
        ) : ChatEvent
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(replay = 0, extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var messagesJob: Job? = null
    private var presenceJob: Job? = null

    fun initialiseConversation(
        otherUserId: String,
        otherUserName: String,
        conversationId: String? = null,
    ) {
        val currentState = _uiState.value
        val resolvedConversationId = conversationId?.takeIf { it.isNotBlank() }
        if (
            currentState.otherUserId == otherUserId &&
            currentState.conversationId != null &&
            (
                resolvedConversationId == null ||
                    currentState.conversationId == resolvedConversationId
                )
        ) {
            return
        }
        messagesJob?.cancel()
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            routerPeopleRepository.observePerson(otherUserId).collect { summary ->
                _uiState.update { state ->
                    state.copy(presence = summary?.presence ?: PresenceStatus.UNKNOWN)
                }
            }
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    title = otherUserName,
                    otherUserId = otherUserId,
                    isLoading = true,
                    errorMessage = null,
                    inputEnabled = false,
                )
            }
            try {
                val currentUserDeferred = async { chatRepository.getCurrentUser() }
                val activeConversationId =
                    resolvedConversationId
                        ?: chatRepository.getOrCreateConversation(otherUserId, otherUserName)
                _uiState.update {
                    it.copy(
                        conversationId = activeConversationId,
                        isLoading = false,
                    )
                }
                messagesJob = launch {
                    chatRepository.observeMessages(activeConversationId).collect { messages ->
                        _uiState.update { state -> state.copy(messages = messages) }
                    }
                }
                val user = currentUserDeferred.await()
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        inputEnabled = true,
                    )
                }
            } catch (error: Throwable) {
                if (error is BriarOnlyOperationException) {
                    throw error
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        val trimmed = text.trim()
        val conversationId = state.conversationId
        val currentUser = state.currentUser
        val otherUserId = state.otherUserId
        if (trimmed.isEmpty() || conversationId == null || currentUser == null || otherUserId == null) {
            return
        }
        viewModelScope.launch {
            val message = createMessage(trimmed, currentUser, otherUserId)
            chatRepository.sendMessage(conversationId, message)
        }
    }

    fun scheduleMessage(text: String) {
        val state = _uiState.value
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return // Don't schedule empty messages
        }
        if (state.conversationId == null || state.currentUser == null || state.otherUserId == null) {
            _uiState.update { it.copy(errorMessage = "Chat not ready. Please wait.") }
            return // State not ready yet
        }
        val emitted = _events.tryEmit(ChatEvent.ShowTimePicker(trimmed))
        if (!emitted) {
            _uiState.update { it.copy(errorMessage = "Failed to show time picker") }
        }
    }

    fun confirmScheduleMessage(messageText: String, timeInMillis: Long) {
        val state = _uiState.value
        val currentUser = state.currentUser
        val otherUserId = state.otherUserId
        val conversationId = state.conversationId
        if (currentUser == null || otherUserId == null || conversationId == null) {
            return
        }
        val message = createMessage(messageText, currentUser, otherUserId)
        _events.tryEmit(ChatEvent.ScheduleDelayedMessage(message, otherUserId, conversationId, timeInMillis))
    }

    private fun createMessage(
        text: String,
        currentUser: ChatUser,
        otherUserId: String,
    ): TextMessage {
        val timestamp = Date.from(Instant.now(clock))
        return TextMessage(
            text = text,
            time = timestamp,
            senderId = currentUser.id,
            recipientId = otherUserId,
            senderName = currentUser.displayName,
        )
    }

    override fun onCleared() {
        messagesJob?.cancel()
        presenceJob?.cancel()
        super.onCleared()
    }
}
