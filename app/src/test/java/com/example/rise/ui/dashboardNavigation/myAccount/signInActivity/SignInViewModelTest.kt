package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import android.net.Uri
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.auth.TelegramAuthData
import com.example.rise.data.auth.TelegramAuthRepository
import com.example.rise.data.auth.TelegramAuthResponse
import com.example.rise.ui.signInActivity.SignInViewModel
import com.example.rise.util.MainDispatcherRule
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.example.rise.auth.AuthenticationService
import com.example.rise.auth.AuthStateHandle
import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.data.auth.BriarAccountRepository
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.testutil.stubBriarChatGateway
import com.example.rise.testutil.stubBriarContactService
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.util.regex.Pattern

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var authService: FakeAuthenticationService
    private lateinit var repository: FakeSignInRepository
    private lateinit var telegramRepository: FakeTelegramAuthRepository
    private lateinit var briarAccountRepository: FakeBriarAccountRepository
    private lateinit var transportRuntimeBridge: FakeTransportRuntimeBridge

    private fun createViewModel(
        emailValidator: SignInViewModel.EmailValidator = SignInViewModel.EmailValidator { email ->
            email.isNotBlank() && EMAIL_REGEX.matcher(email).matches()
        },
        briarRepo: FakeBriarAccountRepository = briarAccountRepository,
        bridge: FakeTransportRuntimeBridge = transportRuntimeBridge,
    ) = SignInViewModel(authService, repository, telegramRepository, briarRepo, bridge, emailValidator)

    @Before
    fun setUp() {
        authService = FakeAuthenticationService()
        repository = FakeSignInRepository()
        telegramRepository = FakeTelegramAuthRepository()
        briarAccountRepository = FakeBriarAccountRepository()
        transportRuntimeBridge = FakeTransportRuntimeBridge()
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
        repository.messagingTokenResult = Result.success(null)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.submitPrimaryAction("Test User", "test@example.com", "password123")
            val saveCredentials = awaitItem() as SignInViewModel.Event.SaveCredentials
            assertEquals("test@example.com", saveCredentials.email)
            assertEquals("password123", saveCredentials.password)
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("test@example.com" to "password123"), authService.emailSignIns)
        assertTrue(repository.ensureUserInitializedCalls >= 1)
        assertTrue(repository.storedTokens.isEmpty())
    }

    @Test
    fun `submitPrimaryAction delegates to register when mode is Register`() = runTest {
        repository.messagingTokenResult = Result.success(null)
        val viewModel = createViewModel()
        viewModel.toggleMode()

        viewModel.events.test {
            viewModel.submitPrimaryAction("Test User", "test@example.com", "password123")
            val saveCredentials = awaitItem() as SignInViewModel.Event.SaveCredentials
            assertEquals("test@example.com", saveCredentials.email)
            assertEquals("password123", saveCredentials.password)
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(authService.emailSignIns.isEmpty())
        assertEquals(listOf("test@example.com" to "password123"), authService.createdUsers)
        assertEquals("Test User", authService.currentUser?.displayName)
    }

    @Test
    fun `register without email creates briar account and navigates`() = runTest {
        transportRuntimeBridge.setMode(BriarTransportMode.BRIAR_ONLY)
        briarAccountRepository.result = Result.success(Unit)
        val viewModel = createViewModel()
        viewModel.toggleMode()

        viewModel.events.test {
            viewModel.submitPrimaryAction("Briar User", "", "password123")
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(authService.createdUsers.isEmpty())
        assertEquals(listOf("Briar User" to "password123"), briarAccountRepository.createCalls)
        assertEquals(0, repository.ensureUserInitializedCalls)
        assertTrue(repository.storedTokens.isEmpty())
    }

    @Test
    fun `register without email surfaces briar creation error`() = runTest {
        transportRuntimeBridge.setMode(BriarTransportMode.BRIAR_ONLY)
        briarAccountRepository.result = Result.failure(IllegalStateException("briar failed"))
        val viewModel = createViewModel()
        viewModel.toggleMode()

        viewModel.events.test {
            viewModel.submitPrimaryAction("Briar User", "", "password123")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("briar failed", event.message)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(authService.createdUsers.isEmpty())
        assertEquals(listOf("Briar User" to "password123"), briarAccountRepository.createCalls)
        assertEquals(0, repository.ensureUserInitializedCalls)
    }

    @Test
    fun `sign in without email uses briar account in briar only mode`() = runTest {
        transportRuntimeBridge.setMode(BriarTransportMode.BRIAR_ONLY)
        briarAccountRepository.signInResult = Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.submitPrimaryAction("Briar User", "", "password123")
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(authService.emailSignIns.isEmpty())
        assertEquals(listOf("Briar User" to "password123"), briarAccountRepository.signInCalls)
    }

    @Test
    fun `sign in without email requires name`() = runTest {
        transportRuntimeBridge.setMode(BriarTransportMode.BRIAR_ONLY)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.submitPrimaryAction("", "", "password123")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Please enter your name", event.message)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(briarAccountRepository.signInCalls.isEmpty())
    }

    @Test
    fun `sign in without email surfaces briar error`() = runTest {
        transportRuntimeBridge.setMode(BriarTransportMode.BRIAR_ONLY)
        briarAccountRepository.signInResult = Result.failure(IllegalStateException("bad password"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.submitPrimaryAction("Briar User", "", "wrong")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("bad password", event.message)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("Briar User" to "wrong"), briarAccountRepository.signInCalls)
    }

    @Test
    fun `signInWithEmail validates email format`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.signInWithEmail(name = "Test", email = "invalid-email", password = "password123")
            val event = awaitItem() as SignInViewModel.Event.ShowMessage
            assertEquals("Please enter a valid email", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithEmail validates password is not blank`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.signInWithEmail(name = "Test", email = "test@example.com", password = "")
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
        authService.signInWithEmailAction = { email, password ->
            authService.emailSignIns += email to password
            authService.currentUser = AuthenticationService.User(
                id = "uid-$email",
                displayName = "Alice",
                email = email,
                photoUrl = null,
            )
        }
        repository.messagingTokenResult = Result.success("test-token")

        val viewModel = createViewModel()

        turbineScope {
            val stateTurbine = viewModel.uiState.testIn(this)
            val eventTurbine = viewModel.events.testIn(this)

            assertFalse(stateTurbine.awaitItem().isLoading)
            viewModel.signInWithEmail(name = "Test", email = "test@example.com", password = "password123")
            assertTrue(stateTurbine.awaitItem().isLoading)
            assertFalse(stateTurbine.awaitItem().isLoading)

            val saveCredentials = eventTurbine.awaitItem() as SignInViewModel.Event.SaveCredentials
            assertEquals("test@example.com", saveCredentials.email)
            assertEquals("password123", saveCredentials.password)
            assertEquals(SignInViewModel.Event.NavigateToMain, eventTurbine.awaitItem())

            assertEquals(listOf("test-token"), repository.storedTokens)
            assertEquals(1, repository.ensureUserInitializedCalls)

            stateTurbine.cancel()
            eventTurbine.cancel()
        }
    }

    @Test
    fun `successful registration flow creates user and updates profile`() = runTest {
        authService.createUserAction = { email, password ->
            authService.createdUsers += email to password
            authService.currentUser = AuthenticationService.User(
                id = "uid-$email",
                displayName = null,
                email = email,
                photoUrl = null,
            )
        }
        repository.messagingTokenResult = Result.success("test-token")

        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.register("Test User", "test@example.com", "password123")
            val saveCredentials = awaitItem() as SignInViewModel.Event.SaveCredentials
            assertEquals("test@example.com", saveCredentials.email)
            assertEquals("password123", saveCredentials.password)
            assertEquals(SignInViewModel.Event.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("Test User"), authService.updateProfileCalls.map { it.first })
        assertEquals("Test User", authService.currentUser?.displayName)
        assertEquals(listOf("test-token"), repository.storedTokens)
        assertEquals(1, repository.ensureUserInitializedCalls)
    }

    @Test
    fun `register surfaces weak password message`() = runTest {
        val weakPassword = FirebaseAuthWeakPasswordException(
            "weak-password",
            "Password must be at least 6 characters",
            "pass"
        )
        authService.createUserAction = { _, _ -> throw weakPassword }

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
        authService.createUserAction = { _, _ -> throw collision }

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
        telegramRepository.response = TelegramAuthResponse(
            customToken = "custom-token",
            displayName = "Telegram User",
            photoUrl = null,
        )
        authService.signInWithCustomTokenAction = { token ->
            authService.customTokenSignIns += token
            authService.currentUser = AuthenticationService.User(
                id = "uid-$token",
                displayName = null,
                email = null,
                photoUrl = null,
            )
        }
        repository.messagingTokenResult = Result.success(null)

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

        assertEquals(1, telegramRepository.requests.size)
        assertEquals(listOf("custom-token"), authService.customTokenSignIns)
        assertEquals(listOf("Telegram User"), authService.updateProfileCalls.map { it.first })
    }

    @Test
    fun `signInWithTelegram surfaces repository errors`() = runTest {
        telegramRepository.error = IllegalStateException("backend down")

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

private class FakeBriarAccountRepository : BriarAccountRepository {
    var result: Result<Unit> = Result.success(Unit)
    var signInResult: Result<Unit> = Result.success(Unit)
    val createCalls = mutableListOf<Pair<String, String>>()
    val signInCalls = mutableListOf<Pair<String, String>>()

    override suspend fun createAccount(name: String, password: String) {
        createCalls += name to password
        result.getOrThrow()
    }

    override suspend fun signIn(name: String, password: String) {
        signInCalls += name to password
        signInResult.getOrThrow()
    }
}

private class FakeTransportRuntimeBridge(
    initialMode: BriarTransportMode = BriarTransportMode.FIRESTORE,
) : TransportRuntimeBridge {
    private val modeState = MutableStateFlow(initialMode)
    override val currentMode: StateFlow<BriarTransportMode> = modeState.asStateFlow()
    override val runtimeStatus: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
    override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
    override val briarChatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(stubBriarChatGateway(isAvailable = true))
    override val briarContactService: StateFlow<BriarContactService> = MutableStateFlow(stubBriarContactService(isAvailable = true))

    override fun requireFirestore(caller: String) = Unit

    fun setMode(mode: BriarTransportMode) {
        modeState.value = mode
    }
}

private class FakeSignInRepository : SignInRepository {
    var ensureUserInitializedCalls: Int = 0
    var ensureUserInitializedError: Throwable? = null
    var messagingTokenResult: Result<String?> = Result.success(null)
    val storedTokens = mutableListOf<String>()
    var storeMessagingTokenError: Throwable? = null

    override suspend fun ensureUserInitialized() {
        ensureUserInitializedCalls += 1
        ensureUserInitializedError?.let { throw it }
    }

    override suspend fun fetchMessagingToken(): String? {
        return messagingTokenResult.getOrElse { throw it }
    }

    override suspend fun storeMessagingToken(token: String) {
        storedTokens += token
        storeMessagingTokenError?.let { throw it }
    }
}

private class FakeTelegramAuthRepository : TelegramAuthRepository {
    val requests = mutableListOf<TelegramAuthData>()
    var response: TelegramAuthResponse = TelegramAuthResponse(
        customToken = "default-token",
        displayName = null,
        photoUrl = null,
    )
    var error: Throwable? = null

    override suspend fun exchange(authData: TelegramAuthData): TelegramAuthResponse {
        requests += authData
        error?.let { throw it }
        return response
    }
}

private class FakeAuthenticationService : AuthenticationService {
    private val listeners = mutableSetOf<(AuthenticationService.User?) -> Unit>()

    var currentUser: AuthenticationService.User? = null
    val emailSignIns = mutableListOf<Pair<String, String>>()
    val createdUsers = mutableListOf<Pair<String, String>>()
    val customTokenSignIns = mutableListOf<String>()
    val updateProfileCalls = mutableListOf<Pair<String?, Uri?>>()

    var signInWithEmailAction: suspend (String, String) -> Unit = { email, password ->
        emailSignIns += email to password
        currentUser = AuthenticationService.User(
            id = "uid-$email",
            displayName = null,
            email = email,
            photoUrl = null,
        )
        notifyListeners()
    }

    var createUserAction: suspend (String, String) -> Unit = { email, password ->
        createdUsers += email to password
        currentUser = AuthenticationService.User(
            id = "uid-$email",
            displayName = null,
            email = email,
            photoUrl = null,
        )
        notifyListeners()
    }

    var signInWithCustomTokenAction: suspend (String) -> Unit = { token ->
        customTokenSignIns += token
        currentUser = AuthenticationService.User(
            id = "uid-$token",
            displayName = null,
            email = null,
            photoUrl = null,
        )
        notifyListeners()
    }

    var updateProfileAction: suspend (String?, Uri?) -> Unit = { displayName, photoUrl ->
        updateProfileCalls += displayName to photoUrl
        currentUser = currentUser?.copy(
            displayName = displayName ?: currentUser?.displayName,
            photoUrl = photoUrl ?: currentUser?.photoUrl,
        )
        notifyListeners()
    }

    override fun currentUser(): AuthenticationService.User? = currentUser

    override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle {
        listeners += listener
        listener(currentUser)
        return AuthStateHandle { listeners -= listener }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        signInWithEmailAction(email, password)
    }

    override suspend fun createUserWithEmail(email: String, password: String) {
        createUserAction(email, password)
    }

    override suspend fun signInWithCustomToken(customToken: String) {
        signInWithCustomTokenAction(customToken)
    }

    override suspend fun updateProfile(displayName: String?, photoUrl: Uri?) {
        updateProfileAction(displayName, photoUrl)
    }

    override fun signOut() {
        currentUser = null
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it(currentUser) }
    }
}
