package com.example.rise.ui.mainActivity

import android.content.Intent
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
import com.example.rise.transport.briar.invite.BriarInvitationLinkParser

class BriarInvitationDeepLinkEntrypoint(
    private val parser: BriarInvitationLinkParser = BriarInvitationLinkParser(),
) {

    fun resolve(rawDeepLink: String?): BriarInvitationOnboardingAction? {
        val candidate = rawDeepLink?.trim().orEmpty()
        if (candidate.isEmpty()) return null

        val parsed = parser.parse(candidate)
        return if (parsed is BriarInvitationLinkParseResult.Success) {
            val invitation = parsed.invitation
            BriarInvitationOnboardingAction(
                briarLink = invitation.briarLink,
                alias = invitation.alias,
                duplicateKey = invitation.duplicateKey,
            )
        } else {
            null
        }
    }
}

data class BriarInvitationOnboardingAction(
    val briarLink: String,
    val alias: String?,
    val duplicateKey: String,
) {
    fun isRawExternalLink(): Boolean = duplicateKey == "link:$briarLink"

    fun applyTo(intent: Intent) {
        intent.action = ACTION
        intent.putExtra(EXTRA_BRIAR_LINK, briarLink)
        intent.putExtra(EXTRA_ALIAS, alias)
        intent.putExtra(EXTRA_DUPLICATE_KEY, duplicateKey)
    }

    companion object {
        const val ACTION = "com.example.rise.action.BRIAR_INVITATION_ONBOARDING"
        const val EXTRA_BRIAR_LINK = "com.example.rise.extra.BRIAR_INVITATION_LINK"
        const val EXTRA_ALIAS = "com.example.rise.extra.BRIAR_INVITATION_ALIAS"
        const val EXTRA_DUPLICATE_KEY = "com.example.rise.extra.BRIAR_INVITATION_DUPLICATE_KEY"

        fun consumeFrom(intent: Intent?): BriarInvitationOnboardingAction? {
            intent ?: return null
            val briarLink = intent.getStringExtra(EXTRA_BRIAR_LINK)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val duplicateKey = intent.getStringExtra(EXTRA_DUPLICATE_KEY)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val alias = intent.getStringExtra(EXTRA_ALIAS)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            intent.removeExtra(EXTRA_BRIAR_LINK)
            intent.removeExtra(EXTRA_ALIAS)
            intent.removeExtra(EXTRA_DUPLICATE_KEY)

            return BriarInvitationOnboardingAction(
                briarLink = briarLink,
                alias = alias,
                duplicateKey = duplicateKey,
            )
        }
    }
}
