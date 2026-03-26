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
class MyAccountViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loadProfile populates ui state`() = runTest {
        val repository = FakeMyAccountRepository()
        repository.user = repository.user.copy(profilePicturePath = "content://profile.jpg")
        val viewModel = MyAccountViewModel(repository)

        viewModel.loadProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(repository.user.name, state.name)
        assertEquals(repository.user.bio, state.bio)
        assertEquals(repository.user.profilePicturePath, state.profilePicturePath)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `updateProfile updates repository and emits toast`() = runTest {
        val repository = FakeMyAccountRepository()
        val viewModel = MyAccountViewModel(repository)

        viewModel.events.test {
            viewModel.updateProfile("New Name", "New Bio")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("New Name", state.name)
            assertEquals("New Bio", state.bio)
            assertEquals(listOf(UpdateCall("New Name", "New Bio", null)), repository.updateCalls)

            val event = awaitItem()
            assertTrue(event is MyAccountViewModel.Event.ShowMessage)
            val messageEvent = event as MyAccountViewModel.Event.ShowMessage
            assertEquals("saving", messageEvent.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateProfile forwards profile picture path when provided`() = runTest {
        val repository = FakeMyAccountRepository()
        val viewModel = MyAccountViewModel(repository)

        viewModel.updateProfile(
            name = "New Name",
            bio = "New Bio",
            profilePicturePath = "file:///tmp/new-profile.jpg",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("file:///tmp/new-profile.jpg", state.profilePicturePath)
        assertEquals(
            listOf(UpdateCall("New Name", "New Bio", "file:///tmp/new-profile.jpg")),
            repository.updateCalls,
        )
    }

    @Test
    fun `updateProfile keeps existing ui fields when blank inputs are submitted`() = runTest {
        val repository = FakeMyAccountRepository().apply {
            user = user.copy(profilePicturePath = "content://profile.jpg")
        }
        val viewModel = MyAccountViewModel(repository)
        viewModel.loadProfile()
        advanceUntilIdle()

        viewModel.updateProfile(name = " ", bio = "", profilePicturePath = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Jane", state.name)
        assertEquals("Bio", state.bio)
        assertEquals("content://profile.jpg", state.profilePicturePath)
        assertEquals(listOf(UpdateCall(" ", "", null)), repository.updateCalls)
    }

    @Test
    fun `signOut emits navigation event`() = runTest {
        val repository = FakeMyAccountRepository()
        val viewModel = MyAccountViewModel(repository)

        viewModel.events.test {
            viewModel.signOut()
            advanceUntilIdle()

            val first = awaitItem()
            assertTrue(first is MyAccountViewModel.Event.ShowMessage)
            val second = awaitItem()
            assertTrue(second is MyAccountViewModel.Event.NavigateToSignIn)
            assertEquals(1, repository.signOutCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private data class UpdateCall(
        val name: String,
        val bio: String,
        val profilePicturePath: String?,
    )

    private class FakeMyAccountRepository : MyAccountRepository {
        var user = User(name = "Jane", bio = "Bio", profilePicturePath = null, registrationTokens = mutableListOf())
        val updateCalls = mutableListOf<UpdateCall>()
        var signOutCalls = 0

        override suspend fun fetchCurrentUser(): User = user

        override suspend fun updateCurrentUser(
            name: String,
            bio: String,
            profilePicturePath: String?,
        ) {
            updateCalls += UpdateCall(name, bio, profilePicturePath)
            user = user.copy(
                name = name,
                bio = bio,
                profilePicturePath = profilePicturePath ?: user.profilePicturePath,
            )
        }

        override suspend fun signOut() {
            signOutCalls++
        }
    }
}
