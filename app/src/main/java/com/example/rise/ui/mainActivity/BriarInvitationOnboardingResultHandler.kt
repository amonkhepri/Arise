package com.example.rise.ui.mainActivity

import android.content.Intent

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
                logInvitationFailure(result.error)
                showMessage(INVITATION_FAILED_MESSAGE)
                markHandled()
            }
        }
    }

    companion object {
        internal const val INVALID_LINK_MESSAGE = "Invalid Briar invitation link"
        internal const val INVITATION_FAILED_MESSAGE = "Unable to add Briar contact"

        internal fun pendingSyncMessage(displayName: String): String =
            "Briar contact added. Wait for $displayName to finish connecting, then open the chat from People."
    }
}
