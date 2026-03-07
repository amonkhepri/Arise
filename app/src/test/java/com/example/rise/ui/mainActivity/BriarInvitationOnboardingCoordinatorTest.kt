package com.example.rise.ui.mainActivity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.rise.helpers.AppConstants
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationLink
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BriarInvitationOnboardingCoordinatorTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun acceptAcceptedInvitationReturnsChatLaunchIntentUsingConversationIdWhenAvailable() = runBlocking {
    var acceptedLink: String? = null
    val coordinator = BriarInvitationOnboardingCoordinator(
      acceptInvitation = { rawLink ->
        acceptedLink = rawLink
        BriarInvitationAcceptanceResult.Accepted(
          invitation = invitation(
            briarLink = rawLink,
            alias = "Alice",
            duplicateKey = "peer-123",
          ),
          conversationId = "conversation-42",
        )
      },
    )

    val result = coordinator.accept(context, onboardingAction())

    assertEquals("briar://invite?c=abc", acceptedLink)
    assertTrue(result is BriarInvitationOnboardingCoordinator.Result.LaunchChat)
    val launchResult = result as BriarInvitationOnboardingCoordinator.Result.LaunchChat
    assertEquals(ChatActivity::class.java.name, launchResult.intent.component?.className)
    assertEquals("peer-123", launchResult.intent.getStringExtra(AppConstants.USER_ID))
    assertEquals("Alice", launchResult.intent.getStringExtra(AppConstants.USER_NAME))
    assertEquals("conversation-42", launchResult.intent.getStringExtra(AppConstants.CONVERSATION_ID))
  }

  @Test
  fun acceptInvalidInvitationReturnsExplicitInvalidResult() = runBlocking {
    val reason = enumValues<BriarInvitationLinkParseResult.InvalidReason>().first()
    val coordinator = BriarInvitationOnboardingCoordinator(
      acceptInvitation = { BriarInvitationAcceptanceResult.InvalidLink(reason) },
    )

    val result = coordinator.accept(context, onboardingAction())

    assertEquals(
      BriarInvitationOnboardingCoordinator.Result.InvalidInvitation(reason),
      result,
    )
  }

  @Test
  fun acceptFailedInvitationReturnsExplicitFailureResult() = runBlocking {
    val error = IllegalStateException("Briar unavailable")
    val coordinator = BriarInvitationOnboardingCoordinator(
      acceptInvitation = {
        BriarInvitationAcceptanceResult.Failed(
          invitation = invitation(),
          error = error,
        )
      },
    )

    val result = coordinator.accept(context, onboardingAction())

    assertTrue(result is BriarInvitationOnboardingCoordinator.Result.InvitationFailed)
    val failedResult = result as BriarInvitationOnboardingCoordinator.Result.InvitationFailed
    assertSame(error, failedResult.error)
  }

  private fun onboardingAction(): BriarInvitationOnboardingAction = BriarInvitationOnboardingAction(
    briarLink = "briar://invite?c=abc",
    alias = "Alice",
    duplicateKey = "peer-123",
  )

  private fun invitation(
    briarLink: String = "briar://invite?c=abc",
    alias: String? = "Alice",
    duplicateKey: String = "peer-123",
  ): BriarInvitationLink = BriarInvitationLink(
    briarLink = briarLink,
    alias = alias,
    duplicateKey = duplicateKey,
  )
}
