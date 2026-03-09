package com.example.rise.ui.mainActivity

import android.content.Context
import android.content.Intent
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatActivity
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import kotlinx.coroutines.delay

internal class BriarInvitationOnboardingCoordinator(
  private val acceptInvitation: suspend (String) -> BriarInvitationAcceptanceResult,
  private val createChatIntent: (Context, ChatLaunchContract) -> Intent = { context, launchContract ->
    ChatActivity.createLaunchIntent(context, launchContract)
  },
  private val retryDelayMillis: Long = RUNTIME_NOT_READY_RETRY_DELAY_MS,
  private val maxRuntimeNotReadyRetries: Int = MAX_RUNTIME_NOT_READY_RETRIES,
) {

  constructor(useCase: BriarInvitationAcceptanceUseCase) : this(useCase::accept)

  suspend fun accept(
    context: Context,
    action: BriarInvitationOnboardingAction,
  ): Result = when (val result = acceptInvitationWhenReady(action.briarLink)) {
    is BriarInvitationAcceptanceResult.Accepted -> {
      val conversationId = result.conversationId
      if (conversationId != null) {
        val invitation = result.invitation
        Result.LaunchChat(
          intent = createChatIntent(
            context,
            ChatLaunchContract(
              userId = invitation.duplicateKey,
              userName = invitation.alias ?: invitation.duplicateKey,
              conversationId = conversationId,
            ),
          ),
        )
      } else {
        Result.ContactAddedPendingSync(result.invitation.alias ?: result.invitation.duplicateKey)
      }
    }
    is BriarInvitationAcceptanceResult.InvalidLink -> Result.InvalidInvitation(result.reason)
    is BriarInvitationAcceptanceResult.Failed -> Result.InvitationFailed(result.error)
  }

  private suspend fun acceptInvitationWhenReady(link: String): BriarInvitationAcceptanceResult {
    repeat(maxRuntimeNotReadyRetries) {
      val result = acceptInvitation(link)
      if (result is BriarInvitationAcceptanceResult.Failed && result.error.isRuntimeNotReadyFailure()) {
        delay(retryDelayMillis)
      } else {
        return result
      }
    }
    return acceptInvitation(link)
  }

  private fun Throwable.isRuntimeNotReadyFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
      val isRuntimeNotReady = current is IllegalStateException &&
        current.message?.contains(RUNTIME_NOT_READY_MESSAGE, ignoreCase = true) == true
      if (isRuntimeNotReady) return true
      current = current.cause
    }
    return false
  }

  sealed interface Result {
    data class LaunchChat(val intent: Intent) : Result
    data class ContactAddedPendingSync(val displayName: String) : Result
    data class InvalidInvitation(
      val reason: BriarInvitationLinkParseResult.InvalidReason,
    ) : Result
    data class InvitationFailed(val error: Throwable) : Result
  }

  companion object {
    private const val MAX_RUNTIME_NOT_READY_RETRIES = 40
    private const val RUNTIME_NOT_READY_RETRY_DELAY_MS = 250L
    private const val RUNTIME_NOT_READY_MESSAGE = "runtime is not ready"

    fun consumePendingAction(intent: Intent?): BriarInvitationOnboardingAction? {
      val rawExternalBriarLink = intent?.data
        ?.toString()
        ?.trim()
        ?.takeIf { rawLink ->
          intent.action == Intent.ACTION_VIEW && rawLink.startsWith("briar://", ignoreCase = true)
        }
      if (intent?.action != BriarInvitationOnboardingAction.ACTION && rawExternalBriarLink == null) {
        return null
      }

      val briarLink = intent.getStringExtra(BriarInvitationOnboardingAction.EXTRA_BRIAR_LINK)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: rawExternalBriarLink.orEmpty()
      val duplicateKey = intent.getStringExtra(BriarInvitationOnboardingAction.EXTRA_DUPLICATE_KEY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: rawExternalBriarLink?.let { "link:$it" }.orEmpty()
      val alias = intent.getStringExtra(BriarInvitationOnboardingAction.EXTRA_ALIAS)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

      intent.action = null
      intent.data = null
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
