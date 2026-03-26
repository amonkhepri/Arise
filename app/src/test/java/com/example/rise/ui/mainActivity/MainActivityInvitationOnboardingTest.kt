package com.example.rise.ui.mainActivity

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.rise.App
import com.example.rise.R
import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.helpers.AppConstants
import com.example.rise.testutil.stubTransportRuntimeBridge
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.briar.invite.BriarInvitationLink
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportRouter
import com.example.rise.ui.alarm.models.Alarm
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import com.example.rise.ui.signInActivity.SignInActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MainActivityInvitationOnboardingTest {

  @get:Rule
  val dispatcherRule = com.example.rise.util.MainDispatcherRule()

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `signed-in invitation onboarding queued on launch reaches chat through started collector`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val invitationAction = BriarInvitationOnboardingAction(
      briarLink = "briar://invite?c=abc",
      alias = "Alice",
      duplicateKey = "peer-123",
    )
    val invitationUseCase = mockk<BriarInvitationAcceptanceUseCase>()
    val acceptanceCompleted = CountDownLatch(1)
    coEvery {
      invitationUseCase.accept(invitationAction.briarLink)
    } coAnswers {
      try {
        BriarInvitationAcceptanceResult.Accepted(
          invitation = BriarInvitationLink(
            briarLink = invitationAction.briarLink,
            alias = invitationAction.alias,
            duplicateKey = invitationAction.duplicateKey,
          ),
          conversationId = "conversation-42",
        )
      } finally {
        acceptanceCompleted.countDown()
      }
    }
    startSignedInKoin(application, invitationUseCase)

    val launchIntent = Intent(application, MainActivity::class.java).also(invitationAction::applyTo)
    val controller = Robolectric.buildActivity(MainActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      drainLifecycleWork()
      assertTrue(acceptanceCompleted.await(5, TimeUnit.SECONDS))
      drainLifecycleWork()

      assertNotNull(activity.findViewById<BottomNavigationView>(R.id.bottomNavigation))

      val startedIntent = requireNotNull(awaitStartedActivity(activity))

      assertEquals(ChatActivity::class.java.name, startedIntent.component?.className)
      assertEquals("peer-123", startedIntent.getStringExtra(AppConstants.USER_ID))
      assertEquals("Alice", startedIntent.getStringExtra(AppConstants.USER_NAME))
      assertEquals("conversation-42", startedIntent.getStringExtra(AppConstants.CONVERSATION_ID))
      coVerify(exactly = 1) { invitationUseCase.accept(invitationAction.briarLink) }
    } finally {
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun `signed-in raw invitation onboarding stays visible before timeout budget and hard-fails when acceptance hangs`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val invitationAction = BriarInvitationOnboardingAction(
      briarLink = "briar://abmpblthdxon5e3luksgivgvcfplw6mawzuumw6h54jxrvyvvohvy",
      alias = null,
      duplicateKey = "link:briar://abmpblthdxon5e3luksgivgvcfplw6mawzuumw6h54jxrvyvvohvy",
    )
    val invitationUseCase = mockk<BriarInvitationAcceptanceUseCase>()
    coEvery {
      invitationUseCase.accept(invitationAction.briarLink)
    } coAnswers {
      delay(Long.MAX_VALUE)
      error("unreachable")
    }
    startSignedInKoin(application, invitationUseCase)

    val launchIntent = Intent(application, MainActivity::class.java).also(invitationAction::applyTo)
    val controller = Robolectric.buildActivity(MainActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      drainLifecycleWork()
      assertNotNull(activity.findViewById<BottomNavigationView>(R.id.bottomNavigation))

      dispatcherRule.testDispatcher.scheduler.advanceTimeBy(20_001L)
      drainLifecycleWork()

      assertNull(ShadowToast.getTextOfLatestToast())
      assertNull(shadowOf(activity).nextStartedActivity)

      dispatcherRule.testDispatcher.scheduler.advanceTimeBy(25_000L)
      drainLifecycleWork()

      assertEquals(
        BriarInvitationOnboardingResultHandler.CHAT_OPEN_FAILED_MESSAGE,
        ShadowToast.getTextOfLatestToast(),
      )
      assertNull(shadowOf(activity).nextStartedActivity)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun `signed-in invitation onboarding retries pending sync result and later launches chat`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val invitationAction = BriarInvitationOnboardingAction(
      briarLink = "briar://invite?c=abc",
      alias = "Alice",
      duplicateKey = "peer-123",
    )
    val invitationUseCase = mockk<BriarInvitationAcceptanceUseCase>()
    var acceptanceCalls = 0
    coEvery {
      invitationUseCase.accept(invitationAction.briarLink)
    } coAnswers {
      acceptanceCalls += 1
      when (acceptanceCalls) {
        1 -> BriarInvitationAcceptanceResult.Accepted(
          invitation = BriarInvitationLink(
            briarLink = invitationAction.briarLink,
            alias = invitationAction.alias,
            duplicateKey = invitationAction.duplicateKey,
          ),
          conversationId = null,
        )
        else -> BriarInvitationAcceptanceResult.Accepted(
          invitation = BriarInvitationLink(
            briarLink = invitationAction.briarLink,
            alias = invitationAction.alias,
            duplicateKey = invitationAction.duplicateKey,
          ),
          conversationId = "conversation-42",
        )
      }
    }
    startSignedInKoin(application, invitationUseCase)

    val launchIntent = Intent(application, MainActivity::class.java).also(invitationAction::applyTo)
    val controller = Robolectric.buildActivity(MainActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      drainLifecycleWork()
      assertNotNull(activity.findViewById<BottomNavigationView>(R.id.bottomNavigation))

      assertEquals(
        BriarInvitationOnboardingResultHandler.pendingSyncMessage("Alice"),
        ShadowToast.getTextOfLatestToast(),
      )
      assertNull(shadowOf(activity).nextStartedActivity)

      dispatcherRule.testDispatcher.scheduler.advanceTimeBy(4_999L)
      drainLifecycleWork()

      assertNull(shadowOf(activity).nextStartedActivity)

      dispatcherRule.testDispatcher.scheduler.advanceTimeBy(1L)
      drainLifecycleWork()

      val startedIntent = requireNotNull(awaitStartedActivity(activity))
      assertEquals(ChatActivity::class.java.name, startedIntent.component?.className)
      assertEquals("peer-123", startedIntent.getStringExtra(AppConstants.USER_ID))
      assertEquals("Alice", startedIntent.getStringExtra(AppConstants.USER_NAME))
      assertEquals("conversation-42", startedIntent.getStringExtra(AppConstants.CONVERSATION_ID))
      assertEquals(2, acceptanceCalls)
      coVerify(exactly = 2) { invitationUseCase.accept(invitationAction.briarLink) }
    } finally {
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun `signed-out invitation onboarding queued on launch relays into sign-in handoff`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val invitationAction = BriarInvitationOnboardingAction(
      briarLink = "briar://invite?c=abc",
      alias = "Alice",
      duplicateKey = "peer-123",
    )
    val invitationUseCase = mockk<BriarInvitationAcceptanceUseCase>(relaxed = true)
    startSignedOutKoin(application, invitationUseCase)

    val launchIntent = Intent(application, MainActivity::class.java).also(invitationAction::applyTo)
    val controller = Robolectric.buildActivity(MainActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      drainLifecycleWork()

      val startedIntent = requireNotNull(awaitStartedActivity(activity))
      assertEquals(SignInActivity::class.java.name, startedIntent.component?.className)
      coVerify(exactly = 0) { invitationUseCase.accept(any()) }
    } finally {
      controller.pause().stop().destroy()
    }
  }

  private fun startSignedInKoin(
    application: Application,
    invitationUseCase: BriarInvitationAcceptanceUseCase,
  ) {
    startKoin {
      androidContext(application)
      allowOverride(true)
      modules(
        listOf(
          App().appModule,
          module {
            single<AuthStateProvider> { SignedInAuthStateProvider() }
            single<AuthenticationService> {
              FakeAuthenticationService(
                AuthenticationService.User(
                  id = "signed-in-user",
                  displayName = "Signed In User",
                  email = null,
                  photoUrl = null,
                ),
              )
            }
            single<AlarmRepository> { FailingAlarmRepository() }
            single<TransportRouter> { FakeTransportRouter() }
            single<TransportRuntimeBridge> { stubTransportRuntimeBridge() }
            factory<BriarInvitationAcceptanceUseCase> { invitationUseCase }
          },
        ),
      )
    }
  }

  private fun startSignedOutKoin(
    application: Application,
    invitationUseCase: BriarInvitationAcceptanceUseCase,
  ) {
    startKoin {
      androidContext(application)
      allowOverride(true)
      modules(
        listOf(
          App().appModule,
          module {
            single<AuthStateProvider> { SignedOutAuthStateProvider() }
            single<AuthenticationService> { FakeAuthenticationService(null) }
            single<AlarmRepository> { FailingAlarmRepository() }
            single<TransportRouter> { FakeTransportRouter() }
            single<TransportRuntimeBridge> { stubTransportRuntimeBridge() }
            factory<BriarInvitationAcceptanceUseCase> { invitationUseCase }
          },
        ),
      )
    }
  }

  private fun drainLifecycleWork() {
    dispatcherRule.testDispatcher.scheduler.runCurrent()
    shadowOf(Looper.getMainLooper()).idle()
    dispatcherRule.testDispatcher.scheduler.runCurrent()
  }

  private fun awaitStartedActivity(activity: MainActivity): Intent? {
    val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    do {
      drainLifecycleWork()
      shadowOf(activity).peekNextStartedActivity()?.let { return it }
      Thread.sleep(10)
    } while (System.nanoTime() < deadlineNanos)
    return shadowOf(activity).peekNextStartedActivity()
  }

  private class SignedInAuthStateProvider : AuthStateProvider {
    override fun isSignedIn(): Boolean = true

    override fun currentUserId(): String? = "signed-in-user"

    override fun currentUserDisplayName(): String? = "Signed In User"
  }

  private class SignedOutAuthStateProvider : AuthStateProvider {
    override fun isSignedIn(): Boolean = false

    override fun currentUserId(): String? = null

    override fun currentUserDisplayName(): String? = null
  }

  private class FailingAlarmRepository : AlarmRepository {
    override fun alarmsQuery(userId: String): AlarmRepository.AlarmQuery {
      throw IllegalStateException("Dashboard alarms are not needed in MainActivity onboarding tests")
    }

    override suspend fun saveAlarm(userId: String, alarm: Alarm) {
      throw UnsupportedOperationException("Not needed in test")
    }
  }

  private class FakeAuthenticationService(
    private var current: AuthenticationService.User?,
  ) : AuthenticationService {
    override fun currentUser(): AuthenticationService.User? = current

    override fun addAuthStateListener(listener: (AuthenticationService.User?) -> Unit): AuthStateHandle {
      listener(current)
      return AuthStateHandle { }
    }

    override suspend fun signInWithEmail(email: String, password: String) = unsupported()

    override suspend fun createUserWithEmail(email: String, password: String) = unsupported()

    override suspend fun signInWithCustomToken(customToken: String) = unsupported()

    override suspend fun updateProfile(displayName: String?, photoUrl: Uri?) {
      current = current?.copy(displayName = displayName ?: current?.displayName, photoUrl = photoUrl)
    }

    override fun signOut() {
      current = null
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not needed in test")
  }

  private class FakeTransportRouter : TransportRouter {
    override val currentIdentity: Flow<CanonicalIdentity> =
      flowOf(CanonicalIdentity(id = "self", displayName = "Self"))

    override suspend fun ensureCurrentIdentity(): CanonicalIdentity =
      CanonicalIdentity(id = "self", displayName = "Self")

    override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
      throw UnsupportedOperationException("Not needed in test")
    }

    override fun observeConversation(conversationId: String): Flow<List<CanonicalMessage>> =
      flowOf(emptyList())

    override suspend fun sendMessage(message: ConnectorOutboundMessage) {
      throw UnsupportedOperationException("Not needed in test")
    }

    override suspend fun reset() = Unit
  }
}
