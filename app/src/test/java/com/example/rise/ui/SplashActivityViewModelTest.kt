package com.example.rise.ui

import app.cash.turbine.test
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class SplashActivityViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

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
    fun `routes valid invitation deep link to sign-in when user signed out`() = runTest {
        val viewModel = SplashActivityViewModel(FakeAuthStateProvider(isSignedIn = false))
        val deepLink = "arise://briar/invite?link=${encode("briar://abc123")}&alias=${encode("Alice")}&inviteId=INV-42"

        viewModel.events.test {
            viewModel.determineDestination(rawDeepLink = deepLink)

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToSignIn)
            event as SplashActivityViewModel.NavigationEvent.ToSignIn
            assertEquals("briar://abc123", event.onboardingAction?.briarLink)
            assertEquals("Alice", event.onboardingAction?.alias)
            assertEquals("invite:inv-42", event.onboardingAction?.duplicateKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `routes valid https invitation deep link to sign-in when user signed out`() = runTest {
        val viewModel = SplashActivityViewModel(FakeAuthStateProvider(isSignedIn = false))
        val deepLink = "https://arise.app/briar/invite?link=${encode("briar://abc123")}&alias=${encode("Alice")}&invite_id=INV-99"

        viewModel.events.test {
            viewModel.determineDestination(rawDeepLink = deepLink)

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToSignIn)
            event as SplashActivityViewModel.NavigationEvent.ToSignIn
            assertEquals("briar://abc123", event.onboardingAction?.briarLink)
            assertEquals("Alice", event.onboardingAction?.alias)
            assertEquals("invite:inv-99", event.onboardingAction?.duplicateKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `routes raw briar invitation deep link to sign-in when user signed out`() = runTest {
        val viewModel = SplashActivityViewModel(FakeAuthStateProvider(isSignedIn = false))

        viewModel.events.test {
            viewModel.determineDestination(rawDeepLink = "briar://abc123")

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToSignIn)
            event as SplashActivityViewModel.NavigationEvent.ToSignIn
            assertEquals("briar://abc123", event.onboardingAction?.briarLink)
            assertEquals("link:briar://abc123", event.onboardingAction?.duplicateKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `routes cold-start cached briar identity with persisted account to main`() = runTest {
        val runtimeManager = FakeBriarRuntimeManager(
            statusAfterEnsureStarted = BriarRuntimeStatus(
                phase = BriarRuntimePhase.RUNNING,
                hasPersistedAccount = true,
                hasDatabaseKey = false,
                hasIdentity = false,
            ),
        )
        val viewModel = SplashActivityViewModel(
            authStateProvider = FakeAuthStateProvider(
                isSignedIn = false,
                userId = "briar-id",
                displayName = "Briar User",
            ),
            briarRuntimeManager = runtimeManager,
        )

        viewModel.events.test {
            viewModel.determineDestination(rawDeepLink = "briar://abc123")

            val event = awaitItem()
            assertTrue(event is SplashActivityViewModel.NavigationEvent.ToMain)
            event as SplashActivityViewModel.NavigationEvent.ToMain
            assertEquals("briar://abc123", event.onboardingAction?.briarLink)
            assertEquals(1, runtimeManager.ensureStartedCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private class FakeAuthStateProvider(
        private val isSignedIn: Boolean,
        private val userId: String? = null,
        private val displayName: String? = null,
    ) : AuthStateProvider {
        override fun isSignedIn(): Boolean = isSignedIn

        override fun currentUserId(): String? = userId ?: if (isSignedIn) "id" else null

        override fun currentUserDisplayName(): String? = displayName ?: if (isSignedIn) "name" else null
    }

    private class FakeBriarRuntimeManager(
        private val statusAfterEnsureStarted: BriarRuntimeStatus = BriarRuntimeStatus.stopped,
    ) : BriarRuntimeManager {
        private val statusFlow = MutableStateFlow(BriarRuntimeStatus.stopped)
        override val status: StateFlow<BriarRuntimeStatus> = statusFlow
        override val diagnostics: SharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val chatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(NoOpBriarChatGateway)
        override val contactService: StateFlow<BriarContactService> = MutableStateFlow(NoOpBriarContactService)

        var ensureStartedCalls: Int = 0
            private set

        override suspend fun ensureStarted() {
            ensureStartedCalls += 1
            statusFlow.value = statusAfterEnsureStarted
        }

        override suspend fun createAccount(name: String, password: String): Boolean = true

        override suspend fun signIn(password: String): Boolean = true

        override suspend fun stop() = Unit
    }
}
