package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.briar.invite.BriarInvitationClassifiedFailure
import com.example.rise.transport.briar.invite.BriarInvitationRejectionReason
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract

internal class BriarManualInvitationCoordinator(
  private val acceptInvitation: suspend (String) -> BriarInvitationAcceptanceResult,
) {

  constructor(useCase: BriarInvitationAcceptanceUseCase) : this(useCase::accept)

  suspend fun accept(rawBriarLink: String): Result = when (val result = acceptInvitation(rawBriarLink)) {
    is BriarInvitationAcceptanceResult.Accepted -> {
      val invitation = result.invitation
      val conversationId = result.conversationId
      if (conversationId != null) {
        Result.LaunchChat(
          ChatLaunchContract(
            userId = invitation.duplicateKey,
            userName = invitation.alias ?: invitation.duplicateKey,
            conversationId = conversationId,
          ),
        )
      } else {
        Result.ContactAddedPendingSync(invitation.alias ?: invitation.duplicateKey)
      }
    }
    is BriarInvitationAcceptanceResult.InvalidLink ->
      Result.RejectedInvitation(BriarInvitationRejectionReason.INVALID)
    is BriarInvitationAcceptanceResult.Failed -> {
      val rejectionReason = (result.error as? BriarInvitationClassifiedFailure)?.reason
      if (rejectionReason != null) {
        Result.RejectedInvitation(rejectionReason)
      } else {
        Result.InvitationFailed(result.error)
      }
    }
  }

  sealed interface Result {
    data class LaunchChat(val launchContract: ChatLaunchContract) : Result
    data class ContactAddedPendingSync(val displayName: String) : Result
    data class RejectedInvitation(
      val reason: BriarInvitationRejectionReason,
    ) : Result
    data class InvitationFailed(val error: Throwable) : Result
  }
}
