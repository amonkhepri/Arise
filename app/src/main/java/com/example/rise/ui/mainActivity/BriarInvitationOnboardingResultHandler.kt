package com.example.rise.ui.mainActivity

import android.content.Intent
import com.example.rise.transport.briar.invite.BriarInvitationClassifiedFailure
import com.example.rise.transport.briar.invite.BriarInvitationRejectionReason

internal class BriarInvitationOnboardingResultHandler(
    private val launchChat: (Intent) -> Unit,
    private val showMessage: (String) -> Unit,
    private val markHandled: () -> Unit,
    private val logInvalidInvitation: (BriarInvitationOnboardingCoordinator.Result.InvalidInvitation) -> Unit,
    private val logInvitationFailure: (Throwable) -> Unit,
) {

    fun handle(result: BriarInvitationOnboardingCoordinator.Result) {
        when (result) {
            is BriarInvitationOnboardingCoordinator.Result.LaunchChat -> {
                launchChat(result.intent)
                markHandled()
            }
            is BriarInvitationOnboardingCoordinator.Result.ContactAddedPendingSync -> {
                showMessage(pendingSyncMessage(result.displayName))
                markHandled()
            }
            is BriarInvitationOnboardingCoordinator.Result.InvalidInvitation -> {
                logInvalidInvitation(result)
                showMessage(INVALID_LINK_MESSAGE)
                markHandled()
            }
            is BriarInvitationOnboardingCoordinator.Result.InvitationFailed -> {
                val rejectionReason = rejectionReasonFor(result.error)
                if (rejectionReason == null) {
                    logInvitationFailure(result.error)
                }
                showMessage(messageFor(result.error, rejectionReason))
                markHandled()
            }
        }
    }

    private fun rejectionReasonFor(error: Throwable): BriarInvitationRejectionReason? =
        (error as? BriarInvitationClassifiedFailure)?.reason

    private fun messageFor(
        error: Throwable,
        rejectionReason: BriarInvitationRejectionReason?,
    ): String {
        if (error is BriarInvitationChatLaunchFailure) {
            return CHAT_OPEN_FAILED_MESSAGE
        }
        return when (rejectionReason) {
            BriarInvitationRejectionReason.DUPLICATE -> DUPLICATE_INVITATION_MESSAGE
            BriarInvitationRejectionReason.EXPIRED -> EXPIRED_INVITATION_MESSAGE
            BriarInvitationRejectionReason.INVALID -> INVALID_LINK_MESSAGE
            null -> INVITATION_FAILED_MESSAGE
        }
    }

    companion object {
        internal const val DUPLICATE_INVITATION_MESSAGE = "This Briar invitation link was already used."
        internal const val EXPIRED_INVITATION_MESSAGE =
            "This Briar invitation link has expired. Ask for a new one."
        internal const val INVALID_LINK_MESSAGE = "Invalid Briar invitation link."
        internal const val INVITATION_FAILED_MESSAGE = "Unable to add Briar contact."
        internal const val CHAT_OPEN_FAILED_MESSAGE =
            "Briar contact was added, but chat could not be opened. Try again from People."

        internal fun pendingSyncMessage(displayName: String): String =
            "Briar contact added. Wait for $displayName to finish connecting, then open the chat from People."
    }
}
