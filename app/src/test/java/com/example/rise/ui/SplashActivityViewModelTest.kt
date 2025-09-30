package com.example.rise.ui

import app.cash.turbine.test
import com.example.rise.data.auth.AuthStateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashActivityViewModelTest {

    @Test
    fun `navigates to main when user signed in`() = runTest {
        val viewModel = SplashActivityViewModel(FakeAuthStateProvider(isSignedIn = true))

        viewModel.events.test {
            viewModel.determineDestination()

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToMain)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigates to sign-in when user signed out`() = runTest {
        val viewModel = SplashActivityViewModel(FakeAuthStateProvider(isSignedIn = false))

        viewModel.events.test {
            viewModel.determineDestination()

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeAuthStateProvider(private val isSignedIn: Boolean) : AuthStateProvider {
        override fun isSignedIn(): Boolean = isSignedIn

        override fun currentUserId(): String? = if (isSignedIn) "id" else null

        override fun currentUserDisplayName(): String? = if (isSignedIn) "name" else null
    }
}
