package com.example.rise.transport.briar.invite

import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.CancellationException

class BriarInvitationAcceptanceUseCase(
    private val contactRepository: BriarContactRepository,
    private val parser: BriarInvitationLinkParser = BriarInvitationLinkParser(),
    private val identityRegistry: IdentityRegistry? = null,
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
            identityRegistry?.upsertIdentity(
                identity = CanonicalIdentity(
                    id = invitation.duplicateKey,
                    displayName = invitation.alias ?: invitation.duplicateKey,
                ),
                aliases = mapOf(TransportId.BRIAR to invitation.briarLink),
                setAsCurrent = false,
            )
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
