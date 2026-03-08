package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rise.data.people.RouterPeopleRepository
import com.example.rise.data.people.PersonSummary
import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.briar.invite.BriarInvitationRejectionReason
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PeopleViewModel internal constructor(
    private val routerPeopleRepository: RouterPeopleRepository,
    private val shareMyRawBriarLink: suspend () -> String,
    private val addByRawBriarLink: suspend (String) -> BriarManualInvitationCoordinator.Result,
) : ViewModel() {

    constructor(
        routerPeopleRepository: RouterPeopleRepository,
    ) : this(
        routerPeopleRepository = routerPeopleRepository,
        shareMyRawBriarLink = {
            throw IllegalStateException("Briar contact repository is not configured.")
        },
        addByRawBriarLink = {
            throw IllegalStateException("Briar invitation coordinator is not configured.")
        },
    )

    constructor(
        routerPeopleRepository: RouterPeopleRepository,
        briarContactRepository: BriarContactRepository,
        briarManualInvitationCoordinator: BriarManualInvitationCoordinator,
    ) : this(
        routerPeopleRepository = routerPeopleRepository,
        shareMyRawBriarLink = { briarContactRepository.getHandshakeLink() },
        addByRawBriarLink = { rawBriarLink -> briarManualInvitationCoordinator.accept(rawBriarLink) },
    )

    data class PeopleUiState(
        val people: List<PersonSummary> = emptyList(),
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
    )

    interface PeopleEvent {
        data class OpenChat(val personId: String, val personName: String) : PeopleEvent
        data class ShareMyRawBriarLink(val rawBriarLink: String) : PeopleEvent
        data object PromptAddByLink : PeopleEvent
        data class LaunchChatFromAddedLink(val launchContract: ChatLaunchContract) : PeopleEvent
        data class ShowMessage(val message: String) : PeopleEvent
    }

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PeopleEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var observeJob: Job? = null

    fun start() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            launch {
                routerPeopleRepository.syncPeopleErrors.collect { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
            }
            routerPeopleRepository.observePeople()
                .onStart { _uiState.update { it.copy(isLoading = true, errorMessage = null) } }
                .collect { people ->
                    _uiState.update {
                        it.copy(
                            people = people,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    fun onPersonSelected(person: PersonSummary) {
        _events.tryEmit(PeopleEvent.OpenChat(person.id, person.name))
    }

    fun onShareMyRawBriarLinkRequested() {
        viewModelScope.launch {
            runCatching { shareMyRawBriarLink() }
                .onSuccess { rawBriarLink ->
                    _events.emit(PeopleEvent.ShareMyRawBriarLink(rawBriarLink))
                }
                .onFailure { error ->
                    emitMessage(error.message ?: "Unable to share Briar invitation link.")
                }
        }
    }

    fun onAddByLinkRequested() {
        _events.tryEmit(PeopleEvent.PromptAddByLink)
    }

    fun onRawBriarLinkSubmitted(rawBriarLink: String) {
        val normalizedLink = rawBriarLink.trim()
        if (normalizedLink.isBlank()) {
            _events.tryEmit(PeopleEvent.ShowMessage(INVALID_INVITATION_MESSAGE))
            return
        }

        viewModelScope.launch {
            runCatching { addByRawBriarLink(normalizedLink) }
                .onSuccess { result ->
                    when (result) {
                        is BriarManualInvitationCoordinator.Result.LaunchChat -> {
                            _events.emit(PeopleEvent.LaunchChatFromAddedLink(result.launchContract))
                        }
                        is BriarManualInvitationCoordinator.Result.ContactAddedPendingSync -> {
                            emitMessage(
                                "Briar contact added. Wait for ${result.displayName} to finish connecting, then open the chat from People."
                            )
                        }
                        is BriarManualInvitationCoordinator.Result.RejectedInvitation -> {
                            emitMessage(messageFor(result.reason))
                        }
                        is BriarManualInvitationCoordinator.Result.InvitationFailed -> {
                            emitMessage(result.error.message ?: "Unable to add Briar contact.")
                        }
                    }
                }
                .onFailure { error ->
                    emitMessage(error.message ?: "Unable to add Briar contact.")
                }
        }
    }

    private suspend fun emitMessage(message: String) {
        _events.emit(PeopleEvent.ShowMessage(message))
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    private fun messageFor(reason: BriarInvitationRejectionReason): String = when (reason) {
        BriarInvitationRejectionReason.DUPLICATE -> DUPLICATE_INVITATION_MESSAGE
        BriarInvitationRejectionReason.EXPIRED -> EXPIRED_INVITATION_MESSAGE
        BriarInvitationRejectionReason.INVALID -> INVALID_INVITATION_MESSAGE
    }

    private companion object {
        const val DUPLICATE_INVITATION_MESSAGE = "This Briar invitation link was already used."
        const val EXPIRED_INVITATION_MESSAGE = "This Briar invitation link has expired. Ask for a new one."
        const val INVALID_INVITATION_MESSAGE = "Invalid Briar invitation link."
    }
}
