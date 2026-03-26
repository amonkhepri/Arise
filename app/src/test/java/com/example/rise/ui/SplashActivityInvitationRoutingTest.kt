package com.example.rise.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.testutil.stubBriarRuntimeManager
import com.example.rise.transport.briar.invite.BriarInvitationLinkParser
import com.example.rise.ui.mainActivity.BriarInvitationDeepLinkEntrypoint
import com.example.rise.ui.mainActivity.BriarInvitationOnboardingAction
import com.example.rise.ui.signInActivity.SignInActivity
import com.example.rise.util.MainDispatcherRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SplashActivityInvitationRoutingTest {

  @get:Rule
  val dispatcherRule = MainDispatcherRule()

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `signed-out invitation deep link launches sign-in with onboarding extras`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    startKoin {
      androidContext(application)
      modules(
        module {
          single<AuthStateProvider> { SignedOutAuthStateProvider() }
          single<BriarRuntimeManager> { stubBriarRuntimeManager() }
          factory { BriarInvitationLinkParser() }
          factory { BriarInvitationDeepLinkEntrypoint(parser = get()) }
          viewModelOf(::SplashActivityViewModel)
        },
      )
    }

    val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(validInvitationDeepLink()))
      .setClass(application, SplashActivity::class.java)
    val controller = Robolectric.buildActivity(SplashActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      drainActivityWork()

      val startedIntent = requireNotNull(shadowOf(activity).nextStartedActivity)
      assertEquals(SignInActivity::class.java.name, startedIntent.component?.className)

      val onboardingAction = requireNotNull(BriarInvitationOnboardingAction.consumeFrom(startedIntent))
      assertEquals("briar://abc123", onboardingAction.briarLink)
      assertEquals("Alice", onboardingAction.alias)
      assertEquals("invite:inv-42", onboardingAction.duplicateKey)
      assertTrue(activity.isFinishing)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun `signed-in invitation deep link launches main with onboarding extras`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    startKoin {
      androidContext(application)
      modules(
        module {
          single<AuthStateProvider> { SignedInAuthStateProvider() }
          single<BriarRuntimeManager> { stubBriarRuntimeManager() }
          factory { BriarInvitationLinkParser() }
          factory { BriarInvitationDeepLinkEntrypoint(parser = get()) }
          viewModelOf(::SplashActivityViewModel)
        },
      )
    }

    val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(validInvitationDeepLink()))
      .setClass(application, SplashActivity::class.java)
    val controller = Robolectric.buildActivity(SplashActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      drainActivityWork()

      val startedIntent = requireNotNull(shadowOf(activity).nextStartedActivity)
      assertEquals(com.example.rise.ui.mainActivity.MainActivity::class.java.name, startedIntent.component?.className)

      val onboardingAction = requireNotNull(BriarInvitationOnboardingAction.consumeFrom(startedIntent))
      assertEquals("briar://abc123", onboardingAction.briarLink)
      assertEquals("Alice", onboardingAction.alias)
      assertEquals("invite:inv-42", onboardingAction.duplicateKey)
      assertTrue(activity.isFinishing)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  private fun drainActivityWork() {
    dispatcherRule.testDispatcher.scheduler.runCurrent()
    shadowOf(Looper.getMainLooper()).idle()
    dispatcherRule.testDispatcher.scheduler.runCurrent()
  }

  private fun validInvitationDeepLink(): String =
    "arise://briar/invite?link=briar%3A%2F%2Fabc123&alias=Alice&inviteId=INV-42"

  private class SignedOutAuthStateProvider : AuthStateProvider {
    override fun isSignedIn(): Boolean = false

    override fun currentUserId(): String? = null

    override fun currentUserDisplayName(): String? = null
  }

  private class SignedInAuthStateProvider : AuthStateProvider {
    override fun isSignedIn(): Boolean = true

    override fun currentUserId(): String? = "signed-in-user"

    override fun currentUserDisplayName(): String? = "Signed In User"
  }
}
