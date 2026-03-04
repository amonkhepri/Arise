package com.example.rise.transport.briar.invite

import com.example.rise.transport.briar.BriarContactRepository
import kotlinx.coroutines.CancellationException

class BriarInvitationAcceptanceUseCase(
    private val contactRepository: BriarContactRepository,
    private val parser: BriarInvitationLinkParser = BriarInvitationLinkParser(),
) {

    suspend fun accept(rawLink: String): BriarInvitationAcceptanceResult {
        val parsed = parser.parse(rawLink)
        if (parsed is BriarInvitationLinkParseResult.Invalid) {
            return BriarInvitationAcceptanceResult.InvalidLink(parsed.reason)
        }

        parsed as BriarInvitationLinkParseResult.Success
        val invitation = parsed.invitation
        return try {
            contactRepository.addContactByLink(invitation.briarLink, invitation.alias)
            BriarInvitationAcceptanceResult.Accepted(invitation)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            BriarInvitationAcceptanceResult.Failed(invitation, error)
        }
    }
}

sealed interface BriarInvitationAcceptanceResult {
    data class Accepted(val invitation: BriarInvitationLink) : BriarInvitationAcceptanceResult
    data class InvalidLink(
        val reason: BriarInvitationLinkParseResult.InvalidReason,
    ) : BriarInvitationAcceptanceResult
    data class Failed(
        val invitation: BriarInvitationLink,
        val error: Throwable,
    ) : BriarInvitationAcceptanceResult
}
