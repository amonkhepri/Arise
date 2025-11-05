package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import app.cash.turbine.test
import com.example.rise.data.people.PeopleRepository
import com.example.rise.data.people.PersonSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @org.junit.Test
    fun `start observes people and updates state`() = runTest {
        val repository = FakePeopleRepository()
        val viewModel = PeopleViewModel(repository)

        viewModel.start()
        advanceUntilIdle()
        assertTrue("Expected loading before people emission", viewModel.uiState.value.isLoading)

        val entries = listOf(
            PersonSummary(id = "a", name = "Alice", bio = "Bio", profilePicturePath = null)
        )
        repository.emit(entries)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(entries, state.people)
        assertEquals(false, state.isLoading)
    }

    @org.junit.Test
    fun `onPersonSelected emits navigation event`() = runTest {
        val repository = FakePeopleRepository()
        val viewModel = PeopleViewModel(repository)
        val summary = PersonSummary(id = "a", name = "Alice", bio = "Bio", profilePicturePath = null)

        viewModel.start()
        advanceUntilIdle()
        repository.emit(listOf(summary))
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onPersonSelected(summary)
            val event = awaitItem()
            assertTrue(event is PeopleViewModel.PeopleEvent.OpenChat)
            val openChat = event as PeopleViewModel.PeopleEvent.OpenChat
            assertEquals(summary.id, openChat.personId)
            assertEquals(summary.name, openChat.personName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Test
    fun `start surfaces sync errors`() = runTest {
        val repository = FakePeopleRepository()
        val viewModel = PeopleViewModel(repository)

        viewModel.start()
        advanceUntilIdle()

        repository.emitError(IllegalStateException("boom"))
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.errorMessage)
    }

    private class FakePeopleRepository : PeopleRepository {
        private val people = MutableSharedFlow<List<PersonSummary>>(replay = 1)
        private val errorsFlow = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
        private var latest: List<PersonSummary> = emptyList()

        fun emit(entries: List<PersonSummary>) {
            latest = entries
            people.tryEmit(entries)
        }

        fun emitError(error: Throwable) {
            errorsFlow.tryEmit(error)
        }

        override val errors = errorsFlow

        override fun observePeople() = people

        override fun observePerson(personId: String) =
            people.map { entries -> entries.firstOrNull { it.id == personId } }

        override suspend fun findPerson(personId: String): PersonSummary? =
            latest.firstOrNull { it.id == personId }
    }
}
