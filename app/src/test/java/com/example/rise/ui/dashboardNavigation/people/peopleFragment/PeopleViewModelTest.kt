package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.rise.models.User
import com.google.firebase.firestore.ListenerRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PeopleViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: FakePeopleRepository
    private lateinit var viewModel: PeopleViewModel

    @Before
    fun setUp() {
        repository = FakePeopleRepository()
        viewModel = PeopleViewModel(repository)
    }

    @Test
    fun `startListening updates people list`() {
        viewModel.people.observeForever { }

        viewModel.startListening()

        val person = PersonRecord("id", User("Alice", "Bio", null, mutableListOf()))
        repository.emit(listOf(person))

        assertEquals(listOf(person), viewModel.people.value)
    }

    @Test
    fun `stopListening removes listener`() {
        viewModel.startListening()
        viewModel.stopListening()

        assertTrue(repository.listenerRemoved)
    }

    @Test
    fun `onPersonSelected emits navigation event`() {
        viewModel.startListening()

        var event: PeopleViewModel.PeopleEvent.OpenChat? = null
        viewModel.events.observeForever { emitted ->
            val content = emitted.getContentIfNotHandled()
            if (content is PeopleViewModel.PeopleEvent.OpenChat) {
                event = content
            }
        }

        val record = PersonRecord("id", User("Bob", "Bio", null, mutableListOf()))
        viewModel.onPersonSelected(record)

        val emitted = event
        assertEquals("id", emitted?.userId)
        assertEquals("Bob", emitted?.userName)
    }

    private class FakePeopleRepository : PeopleRepository {
        private var listener: ((List<PersonRecord>) -> Unit)? = null
        var listenerRemoved = false

        override fun listenForPeople(onPeopleChanged: (List<PersonRecord>) -> Unit): ListenerRegistration {
            listener = onPeopleChanged
            return object : ListenerRegistration {
                override fun remove() {
                    listenerRemoved = true
                }
            }
        }

        override fun removeListener(registration: ListenerRegistration) {
            registration.remove()
        }

        fun emit(records: List<PersonRecord>) {
            listener?.invoke(records)
        }
    }
}
