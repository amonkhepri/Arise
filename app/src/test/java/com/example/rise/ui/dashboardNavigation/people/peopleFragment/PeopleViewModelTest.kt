package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import app.cash.turbine.test
import com.example.rise.data.people.PeopleRepository
import com.example.rise.data.people.PersonSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle

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
        repository.people.tryEmit(entries)
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
        repository.people.tryEmit(listOf(summary))
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

    private class FakePeopleRepository : PeopleRepository {
        val people = MutableSharedFlow<List<PersonSummary>>(replay = 1)
        override fun observePeople() = people
    }
}
