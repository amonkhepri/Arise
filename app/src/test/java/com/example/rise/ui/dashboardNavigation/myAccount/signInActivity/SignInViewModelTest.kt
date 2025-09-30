package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import android.content.Intent
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.example.rise.data.auth.SignInIntentProvider
import com.example.rise.data.auth.SignInRepository
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val intent = Intent("test-action")
    private val intentProvider = SignInIntentProvider { intent }

    @Test
    fun `onSignInClicked emits launch event`() = runTest {
        val repository = FakeSignInRepository()
        val viewModel = SignInViewModel(intentProvider, repository)

        viewModel.events.test {
            viewModel.onSignInClicked()
            val event = awaitItem() as SignInViewModel.Event.LaunchSignIn
            assertEquals(intent, event.intent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSignInSuccess toggles loading state and navigates to main`() = runTest {
        val repository = FakeSignInRepository()
        val viewModel = SignInViewModel(intentProvider, repository)

        turbineScope {
            val stateTurbine = viewModel.uiState.testIn(this)
            val eventTurbine = viewModel.events.testIn(this)

            assertFalse(stateTurbine.awaitItem().isLoading)
            viewModel.onSignInSuccess()
            assertTrue(stateTurbine.awaitItem().isLoading)
            assertFalse(stateTurbine.awaitItem().isLoading)
            assertEquals(SignInViewModel.Event.NavigateToMain, eventTurbine.awaitItem())
            assertEquals("token", repository.storedToken)

            stateTurbine.cancel()
            eventTurbine.cancel()
        }
    }

    @Test
    fun `onSignInSuccess reports failure when initialization fails`() = runTest {
        val repository = FakeSignInRepository(ensureUserInitializedError = IllegalStateException("boom"))
        val viewModel = SignInViewModel(intentProvider, repository)

        viewModel.events.test {
            viewModel.onSignInSuccess()
            val message = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("boom", message.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSignInFailure emits snackbar message for no network`() = runTest {
        val repository = FakeSignInRepository()
        val viewModel = SignInViewModel(intentProvider, repository)

        viewModel.events.test {
            viewModel.onSignInFailure(SignInViewModel.SignInFailure.NoNetwork)
            val message = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("No network", message.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSignInSuccess shows warning when token registration fails`() = runTest {
        val repository = FakeSignInRepository(tokenError = IllegalArgumentException("token failure"))
        val viewModel = SignInViewModel(intentProvider, repository)

        viewModel.events.test {
            viewModel.onSignInSuccess()
            val showMessage = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Failed to register for notifications", showMessage.message)
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeSignInRepository(
        private val ensureUserInitializedError: Throwable? = null,
        private val token: String? = "token",
        private val tokenError: Throwable? = null,
    ) : SignInRepository {

        var storedToken: String? = null

        override suspend fun ensureUserInitialized() {
            ensureUserInitializedError?.let { throw it }
        }

        override suspend fun fetchMessagingToken(): String? {
            tokenError?.let { throw it }
            return token
        }

        override suspend fun storeMessagingToken(token: String) {
            storedToken = token
        }
    }
}
