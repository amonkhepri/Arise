package com.example.rise.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.rise.auth.AuthenticationService
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.data.auth.AuthStateProvider
import com.example.rise.data.auth.BriarAccountRepository
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.auth.TelegramAuthRepository
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.testutil.stubBriarRuntimeManager
import com.example.rise.testutil.stubTransportRuntimeBridge
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.briar.invite.BriarInvitationLinkParser
import com.example.rise.ui.mainActivity.BriarInvitationDeepLinkEntrypoint
import com.example.rise.ui.mainActivity.BriarInvitationOnboardingAction
import com.example.rise.ui.mainActivity.MainActivity
import com.example.rise.ui.signInActivity.SignInActivity
import com.example.rise.ui.signInActivity.SignInViewModel
import com.example.rise.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SignedOutInvitationActivityChainTest {

  @get:Rule
  val dispatcherRule = MainDispatcherRule()

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `signed-out invitation onboarding survives splash to sign-in to main locally`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    startTestKoin(application)

    val splashIntent = Intent(Intent.ACTION_VIEW, Uri.parse(validInvitationDeepLink()))
      .setClass(application, SplashActivity::class.java)
    val splashController = Robolectric.buildActivity(SplashActivity::class.java, splashIntent)
      .create()
      .start()
      .resume()

    try {
      val splashActivity = splashController.get()
      drainActivityWork()

      val signInIntent = requireNotNull(shadowOf(splashActivity).nextStartedActivity)
      assertEquals(SignInActivity::class.java.name, signInIntent.component?.className)

      val signInController = Robolectric.buildActivity(SignInActivity::class.java, signInIntent)
        .create()
        .start()
        .resume()

      try {
        val signInActivity = signInController.get()

        invokeNavigateToMain(signInActivity)

        val mainIntent = requireNotNull(shadowOf(signInActivity).nextStartedActivity)
        assertEquals(MainActivity::class.java.name, mainIntent.component?.className)

        val onboardingAction = requireNotNull(BriarInvitationOnboardingAction.consumeFrom(mainIntent))
        assertEquals("briar://abc123", onboardingAction.briarLink)
        assertEquals("Alice", onboardingAction.alias)
        assertEquals("invite:inv-42", onboardingAction.duplicateKey)
      } finally {
        signInController.pause().stop().destroy()
      }
    } finally {
      splashController.pause().stop().destroy()
    }
  }

  private fun startTestKoin(application: Application) {
    val telegramFlagProvider = mockk<TelegramAuthFlagProvider>()
    every { telegramFlagProvider.observeEnabled() } returns flowOf(false)

    startKoin {
      androidContext(application)
      modules(
        module {
          single<AuthStateProvider> { SignedOutAuthStateProvider() }
          single<BriarRuntimeManager> { stubBriarRuntimeManager() }
          factory { BriarInvitationLinkParser() }
          factory { BriarInvitationDeepLinkEntrypoint(parser = get()) }
          single { telegramFlagProvider }
          single<AuthenticationService> { mockk(relaxed = true) }
          single<SignInRepository> { mockk(relaxed = true) }
          single<TelegramAuthRepository> { mockk(relaxed = true) }
          single<BriarAccountRepository> { mockk(relaxed = true) }
          single<TransportRuntimeBridge> { stubTransportRuntimeBridge() }
          viewModelOf(::SplashActivityViewModel)
          viewModel { SignInViewModel(get(), get(), get(), get(), get()) }
        },
      )
    }
  }

  private fun drainActivityWork() {
    dispatcherRule.testDispatcher.scheduler.runCurrent()
    shadowOf(Looper.getMainLooper()).idle()
    dispatcherRule.testDispatcher.scheduler.runCurrent()
  }

  private fun invokeNavigateToMain(activity: SignInActivity) {
    val method = SignInActivity::class.java.getDeclaredMethod("navigateToMain")
    method.isAccessible = true
    method.invoke(activity)
  }

  private fun validInvitationDeepLink(): String =
    "arise://briar/invite?link=briar%3A%2F%2Fabc123&alias=Alice&inviteId=INV-42"

  private class SignedOutAuthStateProvider : AuthStateProvider {
    override fun isSignedIn(): Boolean = false

    override fun currentUserId(): String? = null

    override fun currentUserDisplayName(): String? = null
  }
}
