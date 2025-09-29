package com.example.rise.ui.dashboardNavigation.people.chatActivity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.models.TextMessage
import com.example.rise.util.Event
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class ChatViewModel(
    private val repository: ChatRepository
) : BaseViewModel() {

    data class ChatUiState(
        val messages: List<TextMessage> = emptyList(),
        val shouldInitRecycler: Boolean = true
    )

    sealed class ChatEvent {
        object ClearMessageInput : ChatEvent()
        data class LaunchDelayedMessage(
            val otherUserId: String,
            val channelId: String,
            val message: TextMessage
        ) : ChatEvent()
    }

    private val _uiState = MutableLiveData(ChatUiState())
    val uiState: LiveData<ChatUiState> = _uiState

    private val _events = MutableLiveData<Event<ChatEvent>>()
    val events: LiveData<Event<ChatEvent>> = _events

    private var chatUser: ChatUser? = null
    private var channelId: String? = null
    private var otherUserId: String? = null
    private var listenerRegistration: ListenerRegistration? = null
    private var firstEmission = true

    fun loadConversation(otherUserId: String) {
        if (otherUserId == this.otherUserId && channelId != null) {
            return
        }
        this.otherUserId = otherUserId
        repository.fetchCurrentUser { currentUser ->
            chatUser = currentUser
            repository.getOrCreateChatChannel(otherUserId) { id ->
                channelId = id
                listenerRegistration?.let { repository.removeListener(it) }
                firstEmission = true
                listenerRegistration = repository.listenForMessages(id) { messages ->
                    val state = ChatUiState(
                        messages = messages,
                        shouldInitRecycler = firstEmission
                    )
                    firstEmission = false
                    _uiState.postValue(state)
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val message = buildMessage(text) ?: return
        val channel = channelId ?: return
        repository.sendMessage(channel, message)
        _events.value = Event(ChatEvent.ClearMessageInput)
    }

    fun scheduleMessage(text: String) {
        val message = buildMessage(text) ?: return
        val channel = channelId ?: return
        val receiver = otherUserId ?: return
        _events.value = Event(ChatEvent.LaunchDelayedMessage(receiver, channel, message))
    }

    private fun buildMessage(rawText: String): TextMessage? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null
        val user = chatUser ?: return null
        val receiver = otherUserId ?: return null
        return TextMessage(
            text = trimmed,
            time = Calendar.getInstance().time,
            senderId = user.id,
            recipientId = receiver,
            senderName = user.user.name
        )
    }

    override fun onCleared() {
        listenerRegistration?.let { repository.removeListener(it) }
        super.onCleared()
    }
}
