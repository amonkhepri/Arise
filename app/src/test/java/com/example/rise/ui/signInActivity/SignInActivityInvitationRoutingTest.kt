package com.example.rise.ui.signInActivity

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.rise.auth.AuthenticationService
import com.example.rise.data.auth.BriarAccountRepository
import com.example.rise.data.auth.SignInRepository
import com.example.rise.data.auth.TelegramAuthRepository
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.testutil.stubTransportRuntimeBridge
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.ui.mainActivity.BriarInvitationOnboardingAction
import com.example.rise.ui.mainActivity.MainActivity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SignInActivityInvitationRoutingTest {

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `navigateToMain relays invitation onboarding extras to main activity`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    startTestKoin(application)

    val onboardingAction = BriarInvitationOnboardingAction(
      briarLink = "briar://abc123",
      alias = "Alice",
      duplicateKey = "invite:inv-42",
    )
    val launchIntent = Intent(application, SignInActivity::class.java).also(onboardingAction::applyTo)
    val controller = Robolectric.buildActivity(SignInActivity::class.java, launchIntent)
      .create()
      .start()
      .resume()

    try {
      val activity = controller.get()

      invokeNavigateToMain(activity)

      val startedIntent = requireNotNull(shadowOf(activity).nextStartedActivity)
      assertEquals(MainActivity::class.java.name, startedIntent.component?.className)

      val forwardedAction = requireNotNull(BriarInvitationOnboardingAction.consumeFrom(startedIntent))
      assertEquals(onboardingAction, forwardedAction)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  private fun startTestKoin(application: Application) {
    val telegramFlagProvider = mockk<TelegramAuthFlagProvider>()
    every { telegramFlagProvider.observeEnabled() } returns flowOf(false)

    startKoin {
      androidContext(application)
      modules(
        module {
          single { telegramFlagProvider }
          single<AuthenticationService> { mockk(relaxed = true) }
          single<SignInRepository> { mockk(relaxed = true) }
          single<TelegramAuthRepository> { mockk(relaxed = true) }
          single<BriarAccountRepository> { mockk(relaxed = true) }
          single<TransportRuntimeBridge> { stubTransportRuntimeBridge() }
          viewModel { SignInViewModel(get(), get(), get(), get(), get()) }
        },
      )
    }
  }

  private fun invokeNavigateToMain(activity: SignInActivity) {
    val method = SignInActivity::class.java.getDeclaredMethod("navigateToMain")
    method.isAccessible = true
    method.invoke(activity)
  }
}
