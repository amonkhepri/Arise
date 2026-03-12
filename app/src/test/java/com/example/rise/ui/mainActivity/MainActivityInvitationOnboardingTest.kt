package com.example.rise.ui.mainActivity

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.rise.App
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.helpers.AppConstants
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.briar.invite.BriarInvitationLink
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
  fun `signed-in invitation onboarding launches chat directly when accept returns conversation id`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val invitationAction = BriarInvitationOnboardingAction(
      briarLink = "briar://invite?c=abc",
      alias = "Alice",
      duplicateKey = "peer-123",
    )
    val invitationUseCase = mockk<BriarInvitationAcceptanceUseCase>()
    coEvery {
      invitationUseCase.accept(invitationAction.briarLink)
    } returns BriarInvitationAcceptanceResult.Accepted(
      invitation = BriarInvitationLink(
        briarLink = invitationAction.briarLink,
        alias = invitationAction.alias,
        duplicateKey = invitationAction.duplicateKey,
      ),
      conversationId = "conversation-42",
    )

    startKoin {
      androidContext(application)
      allowOverride(true)
      modules(
        listOf(
          App().appModule,
          module {
            single<AuthStateProvider> { SignedInAuthStateProvider() }
            factory<BriarInvitationAcceptanceUseCase> { invitationUseCase }
          },
        ),
      )
    }

    val launchIntent = Intent(application, MainActivity::class.java).also(invitationAction::applyTo)
    val controller = Robolectric.buildActivity(MainActivity::class.java, launchIntent).create()

    try {
      val activity = controller.get()
      invokeMaybeHandlePendingInvitationOnboarding(
        activity = activity,
        state = MainActivityViewModel.MainActivityUiState(
          isUserSignedIn = true,
          pendingInvitationOnboardingAction = invitationAction,
        ),
      )
      dispatcherRule.testDispatcher.scheduler.runCurrent()

      val startedIntent = shadowOf(activity).nextStartedActivity

      assertNotNull(startedIntent)
      assertEquals(ChatActivity::class.java.name, startedIntent.component?.className)
      assertEquals("peer-123", startedIntent.getStringExtra(AppConstants.USER_ID))
      assertEquals("Alice", startedIntent.getStringExtra(AppConstants.USER_NAME))
      assertEquals("conversation-42", startedIntent.getStringExtra(AppConstants.CONVERSATION_ID))
      coVerify(exactly = 1) { invitationUseCase.accept(invitationAction.briarLink) }
    } finally {
      controller.pause().stop().destroy()
    }
  }

  private class SignedInAuthStateProvider : AuthStateProvider {
    override fun isSignedIn(): Boolean = true

    override fun currentUserId(): String? = "signed-in-user"

    override fun currentUserDisplayName(): String? = "Signed In User"
  }

  private fun invokeMaybeHandlePendingInvitationOnboarding(
    activity: MainActivity,
    state: MainActivityViewModel.MainActivityUiState,
  ) {
    val method = MainActivity::class.java.getDeclaredMethod(
      "maybeHandlePendingInvitationOnboarding",
      MainActivityViewModel.MainActivityUiState::class.java,
    )
    method.isAccessible = true
    method.invoke(activity, state)
  }
}
