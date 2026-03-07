package com.example.rise.ui.mainActivity

import android.app.Activity
import app.cash.turbine.test
import com.example.rise.data.auth.AuthStateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import com.example.rise.util.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `emits sign-in event when user is signed out`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = false)
        val viewModel = MainActivityViewModel(authState)

        viewModel.events.test {
            viewModel.onStart()

            val event = awaitItem()
            assertTrue(event is MainActivityViewModel.MainActivityEvent.LaunchSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not relaunch sign-in when result succeeds`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = false)
        val viewModel = MainActivityViewModel(authState)

        viewModel.events.test {
            viewModel.onStart()
            awaitItem()

            authState.setSignedIn(true)
            viewModel.onSignInResult(Activity.RESULT_OK)

            assertTrue(viewModel.uiState.value.isUserSignedIn)
            assertFalse(viewModel.uiState.value.isSigningIn)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retries sign-in when result fails`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = false)
        val viewModel = MainActivityViewModel(authState)

        viewModel.events.test {
            viewModel.onStart()
            awaitItem()

            viewModel.onSignInResult(Activity.RESULT_CANCELED)

            val retry = awaitItem()
            assertTrue(retry is MainActivityViewModel.MainActivityEvent.LaunchSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps pending invitation onboarding action after sign-in succeeds`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = false)
        val viewModel = MainActivityViewModel(authState)
        val action = BriarInvitationOnboardingAction(
            briarLink = "briar://invite?c=abc",
            alias = "Ada",
            duplicateKey = "dup-key",
        )

        viewModel.onPendingInvitationOnboarding(action)

        viewModel.events.test {
            viewModel.onStart()
            awaitItem()

            authState.setSignedIn(true)
            viewModel.onSignInResult(Activity.RESULT_OK)

            assertTrue(viewModel.uiState.value.isUserSignedIn)
            assertEquals(action, viewModel.uiState.value.pendingInvitationOnboardingAction)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps pending invitation onboarding action through startup when already signed in`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = true)
        val viewModel = MainActivityViewModel(authState)
        val action = BriarInvitationOnboardingAction(
            briarLink = "briar://invite?c=abc",
            alias = "Ada",
            duplicateKey = "dup-key",
        )

        viewModel.onPendingInvitationOnboarding(action)

        viewModel.events.test {
            viewModel.onStart()

            assertTrue(viewModel.uiState.value.isUserSignedIn)
            assertEquals(action, viewModel.uiState.value.pendingInvitationOnboardingAction)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clears pending invitation onboarding action when handled`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = true)
        val viewModel = MainActivityViewModel(authState)
        val action = BriarInvitationOnboardingAction(
            briarLink = "briar://invite?c=abc",
            alias = "Ada",
            duplicateKey = "dup-key",
        )

        viewModel.onPendingInvitationOnboarding(action)

        assertEquals(action, viewModel.uiState.value.pendingInvitationOnboardingAction)

        viewModel.onPendingInvitationOnboardingHandled()

        assertNull(viewModel.uiState.value.pendingInvitationOnboardingAction)
    }

    private class FakeAuthStateProvider(private var signedIn: Boolean) : AuthStateProvider {
        override fun isSignedIn(): Boolean = signedIn

        override fun currentUserId(): String? = if (signedIn) "id" else null

        override fun currentUserDisplayName(): String? = if (signedIn) "name" else null

        fun setSignedIn(value: Boolean) {
            signedIn = value
        }
    }

}
