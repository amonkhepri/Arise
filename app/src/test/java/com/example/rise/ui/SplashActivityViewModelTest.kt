package com.example.rise.ui

import app.cash.turbine.test
import com.example.rise.data.auth.AuthStateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    @Test
    fun `routes valid invitation deep link to main when user signed out`() = runTest {
        val viewModel = SplashActivityViewModel(FakeAuthStateProvider(isSignedIn = false))
        val deepLink = "arise://briar/invite?link=${encode("briar://abc123")}&alias=${encode("Alice")}&inviteId=INV-42"

        viewModel.events.test {
            viewModel.determineDestination(rawDeepLink = deepLink)

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToMain)
            event as SplashActivityViewModel.NavigationEvent.ToMain
            assertEquals("briar://abc123", event.onboardingAction?.briarLink)
            assertEquals("Alice", event.onboardingAction?.alias)
            assertEquals("invite:inv-42", event.onboardingAction?.duplicateKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private class FakeAuthStateProvider(private val isSignedIn: Boolean) : AuthStateProvider {
        override fun isSignedIn(): Boolean = isSignedIn

        override fun currentUserId(): String? = if (isSignedIn) "id" else null

        override fun currentUserDisplayName(): String? = if (isSignedIn) "name" else null
    }
}
