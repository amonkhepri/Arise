package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract

internal class BriarManualInvitationCoordinator(
  private val acceptInvitation: suspend (String) -> BriarInvitationAcceptanceResult,
) {

  constructor(useCase: BriarInvitationAcceptanceUseCase) : this(useCase::accept)

  suspend fun accept(rawBriarLink: String): Result = when (val result = acceptInvitation(rawBriarLink)) {
    is BriarInvitationAcceptanceResult.Accepted -> {
      val invitation = result.invitation
      Result.LaunchChat(
        ChatLaunchContract(
          userId = invitation.duplicateKey,
          userName = invitation.alias ?: invitation.duplicateKey,
          conversationId = result.conversationId,
        ),
      )
    }
    is BriarInvitationAcceptanceResult.InvalidLink -> Result.InvalidInvitation(result.reason)
    is BriarInvitationAcceptanceResult.Failed -> Result.InvitationFailed(result.error)
  }

  sealed interface Result {
    data class LaunchChat(val launchContract: ChatLaunchContract) : Result
    data class InvalidInvitation(
      val reason: BriarInvitationLinkParseResult.InvalidReason,
    ) : Result
    data class InvitationFailed(val error: Throwable) : Result
  }
}
