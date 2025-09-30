package com.example.rise.ui.dashboardNavigation.myAccount

import app.cash.turbine.test
import com.example.rise.data.myaccount.MyAccountRepository
import com.example.rise.models.User
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyAccountBaseViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loadProfile populates ui state`() = runTest {
        val repository = FakeMyAccountRepository()
        val viewModel = MyAccountBaseViewModel(repository)

        viewModel.loadProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(repository.user.name, state.name)
        assertEquals(repository.user.bio, state.bio)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `updateProfile updates repository and emits toast`() = runTest {
        val repository = FakeMyAccountRepository()
        val viewModel = MyAccountBaseViewModel(repository)

        viewModel.events.test {
            viewModel.updateProfile("New Name", "New Bio")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("New Name", state.name)
            assertEquals("New Bio", state.bio)
            assertEquals(listOf("New Name" to "New Bio"), repository.updateCalls)

            val event = awaitItem()
            assertTrue(event is MyAccountBaseViewModel.Event.ShowMessage)
            val messageEvent = event as MyAccountBaseViewModel.Event.ShowMessage
            assertEquals("saving", messageEvent.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut emits navigation event`() = runTest {
        val repository = FakeMyAccountRepository()
        val viewModel = MyAccountBaseViewModel(repository)

        viewModel.events.test {
            viewModel.signOut()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is MyAccountBaseViewModel.Event.NavigateToSignIn)
            assertEquals(1, repository.signOutCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeMyAccountRepository : MyAccountRepository {
        var user = User(name = "Jane", bio = "Bio", profilePicturePath = null, registrationTokens = mutableListOf())
        val updateCalls = mutableListOf<Pair<String, String>>()
        var signOutCalls = 0

        override suspend fun fetchCurrentUser(): User = user

        override suspend fun updateCurrentUser(name: String, bio: String) {
            updateCalls += name to bio
            user = user.copy(name = name, bio = bio)
        }

        override suspend fun signOut() {
            signOutCalls++
        }
    }
}
