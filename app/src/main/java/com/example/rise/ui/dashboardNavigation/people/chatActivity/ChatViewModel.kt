package com.example.rise.ui.dashboardNavigation.people.chatActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.chat.ChatRepository
import com.example.rise.data.chat.ChatUser
import com.example.rise.models.TextMessage
import java.time.Clock
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    data class ChatUiState(
        val title: String = "",
        val otherUserId: String? = null,
        val channelId: String? = null,
        val currentUser: ChatUser? = null,
        val messages: List<TextMessage> = emptyList(),
        val isLoading: Boolean = true,
        val inputEnabled: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface ChatEvent {
        data class LaunchSchedule(
            val message: TextMessage,
            val otherUserId: String,
            val channelId: String,
        ) : ChatEvent
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var messagesJob: Job? = null

    fun initialiseConversation(otherUserId: String, otherUserName: String) {
        val currentState = _uiState.value
        if (currentState.otherUserId == otherUserId && currentState.channelId != null) {
            return
        }
        messagesJob?.cancel()
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
                val user = repository.getCurrentUser()
                val channelId = repository.getOrCreateChannel(otherUserId)
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        channelId = channelId,
                        isLoading = false,
                        inputEnabled = true,
                    )
                }
                messagesJob = launch {
                    repository.observeMessages(channelId).collect { messages ->
                        _uiState.update { state -> state.copy(messages = messages) }
                    }
                }
            } catch (error: Throwable) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        val trimmed = text.trim()
        val channelId = state.channelId
        val currentUser = state.currentUser
        val otherUserId = state.otherUserId
        if (trimmed.isEmpty() || channelId == null || currentUser == null || otherUserId == null) {
            return
        }
        viewModelScope.launch {
            val message = createMessage(trimmed, currentUser, otherUserId)
            repository.sendMessage(channelId, message)
        }
    }

    fun scheduleMessage(text: String) {
        val state = _uiState.value
        val trimmed = text.trim()
        val channelId = state.channelId
        val currentUser = state.currentUser
        val otherUserId = state.otherUserId
        if (trimmed.isEmpty() || channelId == null || currentUser == null || otherUserId == null) {
            return
        }
        val message = createMessage(trimmed, currentUser, otherUserId)
        _events.tryEmit(ChatEvent.LaunchSchedule(message, otherUserId, channelId))
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
        super.onCleared()
    }
}
