package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import androidx.lifecycle.viewModelScope
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.data.people.PeopleRepository
import com.example.rise.data.people.PersonSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PeopleViewModel(
    private val repository: PeopleRepository
) : BaseViewModel() {

    data class PeopleUiState(
        val people: List<PersonSummary> = emptyList(),
        val isLoading: Boolean = true,
        val errorMessage: String? = null
    )

    sealed interface PeopleEvent {
        data class OpenChat(val personId: String, val personName: String) : PeopleEvent
    }

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PeopleEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var observeJob: Job? = null

    fun start() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            repository.observePeople()
                .onStart { _uiState.update { it.copy(isLoading = true, errorMessage = null) } }
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
                .collect { people ->
                    _uiState.update {
                        it.copy(
                            people = people,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun onPersonSelected(person: PersonSummary) {
        _events.tryEmit(PeopleEvent.OpenChat(person.id, person.name))
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
