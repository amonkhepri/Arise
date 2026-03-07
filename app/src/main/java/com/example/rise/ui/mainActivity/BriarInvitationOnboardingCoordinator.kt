package com.example.rise.ui.mainActivity

import android.content.Context
import android.content.Intent
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract

internal class BriarInvitationOnboardingCoordinator(
  private val acceptInvitation: suspend (String) -> BriarInvitationAcceptanceResult,
  private val createChatIntent: (Context, ChatLaunchContract) -> Intent = { context, launchContract ->
    ChatActivity.createLaunchIntent(context, launchContract)
  },
) {

  constructor(useCase: BriarInvitationAcceptanceUseCase) : this(useCase::accept)

  suspend fun accept(
    context: Context,
    action: BriarInvitationOnboardingAction,
  ): Result = when (val result = acceptInvitation(action.briarLink)) {
    is BriarInvitationAcceptanceResult.Accepted -> {
      val invitation = result.invitation
      Result.LaunchChat(
        intent = createChatIntent(
          context,
          ChatLaunchContract(
            userId = invitation.duplicateKey,
            userName = invitation.alias ?: invitation.duplicateKey,
            conversationId = result.conversationId,
          ),
        ),
      )
    }
    is BriarInvitationAcceptanceResult.InvalidLink -> Result.InvalidInvitation(result.reason)
    is BriarInvitationAcceptanceResult.Failed -> Result.InvitationFailed(result.error)
  }

  sealed interface Result {
    data class LaunchChat(val intent: Intent) : Result
    data class InvalidInvitation(
      val reason: BriarInvitationLinkParseResult.InvalidReason,
    ) : Result
    data class InvitationFailed(val error: Throwable) : Result
  }

  companion object {
    fun consumePendingAction(intent: Intent?): BriarInvitationOnboardingAction? {
      if (intent?.action != BriarInvitationOnboardingAction.ACTION) return null

      val briarLink = intent.getStringExtra(BriarInvitationOnboardingAction.EXTRA_BRIAR_LINK)
        ?.trim()
        .orEmpty()
      val duplicateKey = intent.getStringExtra(BriarInvitationOnboardingAction.EXTRA_DUPLICATE_KEY)
        ?.trim()
        .orEmpty()
      val alias = intent.getStringExtra(BriarInvitationOnboardingAction.EXTRA_ALIAS)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

      intent.action = null
      intent.removeExtra(BriarInvitationOnboardingAction.EXTRA_BRIAR_LINK)
      intent.removeExtra(BriarInvitationOnboardingAction.EXTRA_ALIAS)
      intent.removeExtra(BriarInvitationOnboardingAction.EXTRA_DUPLICATE_KEY)

      if (briarLink.isEmpty() || duplicateKey.isEmpty()) return null

      return BriarInvitationOnboardingAction(
        briarLink = briarLink,
        alias = alias,
        duplicateKey = duplicateKey,
      )
    }
  }
}
