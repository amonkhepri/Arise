package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import android.text.TextUtils
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.auth.TelegramAuthData
import com.example.rise.data.auth.TelegramAuthRepository
import com.example.rise.data.auth.TelegramAuthResponse
import com.example.rise.ui.signInActivity.SignInViewModel
import com.example.rise.util.MainDispatcherRule
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Before
import java.util.regex.Pattern

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val repository = mockk<SignInRepository>(relaxed = true)
    private val telegramRepository = mockk<TelegramAuthRepository>(relaxed = true)

    private fun createViewModel(
        emailValidator: SignInViewModel.EmailValidator = SignInViewModel.EmailValidator { email ->
            email.isNotBlank() && EMAIL_REGEX.matcher(email).matches()
        }
    ) = SignInViewModel(firebaseAuth, repository, telegramRepository, emailValidator)

    @Before
    fun setUp() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @After
    fun tearDown() {
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun `toggleMode switches between SignIn and Register`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SignInViewModel.Mode.SignIn, awaitItem().mode)
            viewModel.toggleMode()
            assertEquals(SignInViewModel.Mode.Register, awaitItem().mode)
            viewModel.toggleMode()
            assertEquals(SignInViewModel.Mode.SignIn, awaitItem().mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitPrimaryAction delegates to sign in when mode is SignIn`() = runTest {
        val viewModel = spyk(createViewModel(), recordPrivateCalls = true)
        every { viewModel.signInWithEmail(any(), any()) } returns Unit
        every { viewModel.register(any(), any(), any()) } returns Unit

        viewModel.submitPrimaryAction("Test User", "test@example.com", "password123")

        verify(exactly = 1) { viewModel.signInWithEmail("test@example.com", "password123") }
        verify(exactly = 0) { viewModel.register(any(), any(), any()) }
    }

    @Test
    fun `submitPrimaryAction delegates to register when mode is Register`() = runTest {
        val viewModel = spyk(createViewModel(), recordPrivateCalls = true)
        every { viewModel.signInWithEmail(any(), any()) } returns Unit
        every { viewModel.register(any(), any(), any()) } returns Unit

        viewModel.toggleMode()
        viewModel.submitPrimaryAction("Test User", "test@example.com", "password123")

        verify(exactly = 1) { viewModel.register("Test User", "test@example.com", "password123") }
        verify(exactly = 0) { viewModel.signInWithEmail(any(), any()) }
    }

    @Test
    fun `signInWithEmail validates email format`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.signInWithEmail("invalid-email", "password123")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Please enter a valid email", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithEmail validates password is not blank`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.signInWithEmail("test@example.com", "")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Please enter your password", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register validates name is not blank`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.register("", "test@example.com", "password123")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Please enter your name", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register validates minimum password length`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.register("Test User", "test@example.com", "12345")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Password must be at least 6 characters", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful sign in flow toggles loading and navigates`() = runTest {
        val authResult = mockk<AuthResult>()
        val authTask = TaskCompletionSource<AuthResult>().apply {
            setResult(authResult)
        }.task

        every { firebaseAuth.signInWithEmailAndPassword(any(), any()) } returns authTask
        coEvery { repository.ensureUserInitialized() } returns Unit
        coEvery { repository.fetchMessagingToken() } returns "test-token"
        coEvery { repository.storeMessagingToken(any()) } returns Unit

        val viewModel = createViewModel()

        turbineScope {
            val stateTurbine = viewModel.uiState.testIn(this)
            val eventTurbine = viewModel.events.testIn(this)

            assertFalse(stateTurbine.awaitItem().isLoading)
            viewModel.signInWithEmail("test@example.com", "password123")
            assertTrue(stateTurbine.awaitItem().isLoading)
            assertFalse(stateTurbine.awaitItem().isLoading)

            val saveCredentials = eventTurbine.awaitItem() as SignInViewModel.Event.SaveCredentials
            assertEquals("test@example.com", saveCredentials.email)
            assertEquals("password123", saveCredentials.password)
            assertEquals(SignInViewModel.Event.NavigateToMain, eventTurbine.awaitItem())

            coVerify { repository.storeMessagingToken("test-token") }

            stateTurbine.cancel()
            eventTurbine.cancel()
        }
    }

    @Test
    fun `successful registration flow creates user and updates profile`() = runTest {
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>(relaxed = true)
        val authTask = TaskCompletionSource<AuthResult>().apply {
            setResult(authResult)
        }.task
        val updateTask = TaskCompletionSource<Void>().apply {
            setResult(null)
        }.task

        every { firebaseAuth.createUserWithEmailAndPassword(any(), any()) } returns authTask
        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.updateProfile(any()) } returns updateTask
        coEvery { repository.ensureUserInitialized() } returns Unit
        coEvery { repository.fetchMessagingToken() } returns "test-token"
        coEvery { repository.storeMessagingToken(any()) } returns Unit

        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.register("Test User", "test@example.com", "password123")
            val saveCredentials = awaitItem() as SignInViewModel.Event.SaveCredentials
            assertEquals("test@example.com", saveCredentials.email)
            assertEquals("password123", saveCredentials.password)
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { firebaseUser.updateProfile(any()) }
    }

    @Test
    fun `register surfaces weak password message`() = runTest {
        val weakPassword = FirebaseAuthWeakPasswordException(
            "weak-password",
            "Password must be at least 6 characters",
            "pass"
        )
        val authTask = TaskCompletionSource<AuthResult>().apply {
            setException(weakPassword)
        }.task

        every { firebaseAuth.createUserWithEmailAndPassword(any(), any()) } returns authTask

        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.register("Test User", "test@example.com", "pass")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals(weakPassword.localizedMessage, event.message)
            assertNotEquals(weakPassword.reason, event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register surfaces collision message`() = runTest {
        val collision = FirebaseAuthUserCollisionException(
            "email-already-in-use",
            "Firebase says email exists"
        )
        val authTask = TaskCompletionSource<AuthResult>().apply {
            setException(collision)
        }.task

        every { firebaseAuth.createUserWithEmailAndPassword(any(), any()) } returns authTask

        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.register("Test User", "test@example.com", "password123")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("An account already exists with this email", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithTelegram signs in and navigates`() = runTest {
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>(relaxed = true) {
            every { displayName } returns null
            every { updateProfile(any()) } returns TaskCompletionSource<Void>().apply { setResult(null) }.task
        }
        val signInTask = TaskCompletionSource<AuthResult>().apply { setResult(authResult) }.task

        coEvery { telegramRepository.exchange(any()) } returns TelegramAuthResponse(
            customToken = "custom-token",
            displayName = "Telegram User",
            photoUrl = null,
        )
        every { firebaseAuth.signInWithCustomToken("custom-token") } returns signInTask
        every { firebaseAuth.currentUser } returns firebaseUser
        coEvery { repository.ensureUserInitialized() } returns Unit
        coEvery { repository.fetchMessagingToken() } returns null

        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.signInWithTelegram(
                TelegramAuthData(
                    id = 123L,
                    firstName = "Telegram",
                    lastName = "User",
                    username = "telegram_user",
                    photoUrl = null,
                    authDate = 1000L,
                    hash = "hash",
                )
            )
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { telegramRepository.exchange(any()) }
        verify { firebaseAuth.signInWithCustomToken("custom-token") }
    }

    @Test
    fun `signInWithTelegram surfaces repository errors`() = runTest {
        coEvery { telegramRepository.exchange(any()) } throws IllegalStateException("backend down")

        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.signInWithTelegram(
                TelegramAuthData(
                    id = 1,
                    firstName = "T",
                    lastName = null,
                    username = null,
                    photoUrl = null,
                    authDate = 10,
                    hash = "hash",
                )
            )
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("backend down", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCredentialError shows error message`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            val error = Exception("Credential failed")
            viewModel.onCredentialError(error)
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Credential failed", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private val EMAIL_REGEX: Pattern = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
