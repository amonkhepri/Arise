package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.util.Event
import com.google.firebase.firestore.ListenerRegistration

class PeopleViewModel(
    private val repository: PeopleRepository
) : BaseViewModel() {

    sealed class PeopleEvent {
        data class OpenChat(val userId: String, val userName: String) : PeopleEvent()
    }

    private val _people = MutableLiveData<List<PersonRecord>>(emptyList())
    val people: LiveData<List<PersonRecord>> = _people

    private val _events = MutableLiveData<Event<PeopleEvent>>()
    val events: LiveData<Event<PeopleEvent>> = _events

    private var listenerRegistration: ListenerRegistration? = null

    fun startListening() {
        if (listenerRegistration != null) return
        listenerRegistration = repository.listenForPeople { records ->
            _people.postValue(records)
        }
    }

    fun stopListening() {
        listenerRegistration?.let { repository.removeListener(it) }
        listenerRegistration = null
    }

    fun onPersonSelected(record: PersonRecord) {
        _events.value = Event(PeopleEvent.OpenChat(record.userId, record.user.name))
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
