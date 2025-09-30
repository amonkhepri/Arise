package com.example.rise.ui.mainActivity

import android.app.Activity
import android.content.Intent
import app.cash.turbine.test
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.auth.SignInIntentProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.rise.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val defaultIntent = mockk<android.content.Intent> {
        every { action } returns "sign-in"
    }

    @Test
    fun `emits sign-in event when user is signed out`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = false)
        val signInProvider = FakeSignInIntentProvider(defaultIntent)
        val viewModel = MainActivityViewModel(authState, signInProvider)

        viewModel.events.test {
            viewModel.onStart()

            val event = awaitItem()
            assertTrue(event is MainActivityViewModel.MainActivityEvent.LaunchSignIn)
            assertEquals(defaultIntent.action, (event as MainActivityViewModel.MainActivityEvent.LaunchSignIn).intent.action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not relaunch sign-in when result succeeds`() = runTest {
        val authState = FakeAuthStateProvider(signedIn = false)
        val signInProvider = FakeSignInIntentProvider(defaultIntent)
        val viewModel = MainActivityViewModel(authState, signInProvider)

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
        val signInProvider = FakeSignInIntentProvider(defaultIntent)
        val viewModel = MainActivityViewModel(authState, signInProvider)

        viewModel.events.test {
            viewModel.onStart()
            awaitItem()

            viewModel.onSignInResult(Activity.RESULT_CANCELED)

            val retry = awaitItem()
            assertTrue(retry is MainActivityViewModel.MainActivityEvent.LaunchSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeAuthStateProvider(private var signedIn: Boolean) : AuthStateProvider {
        override fun isSignedIn(): Boolean = signedIn

        override fun currentUserId(): String? = if (signedIn) "id" else null

        override fun currentUserDisplayName(): String? = if (signedIn) "name" else null

        fun setSignedIn(value: Boolean) {
            signedIn = value
        }
    }

    private class FakeSignInIntentProvider(private val intent: Intent) : SignInIntentProvider {
        override fun createSignInIntent(): Intent = intent
    }
}
