package com.example.rise.transport.briar.invite

import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportRouter
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.CancellationException

class BriarInvitationAcceptanceUseCase(
    private val contactRepository: BriarContactRepository,
    private val parser: BriarInvitationLinkParser = BriarInvitationLinkParser(),
    private val identityRegistry: IdentityRegistry? = null,
    private val transportRouter: TransportRouter? = null,
) {

    suspend fun accept(rawLink: String): BriarInvitationAcceptanceResult {
        val parsed = parser.parse(rawLink)
        if (parsed is BriarInvitationLinkParseResult.Invalid) {
            return BriarInvitationAcceptanceResult.InvalidLink(parsed.reason)
        }

        parsed as BriarInvitationLinkParseResult.Success
        val invitation = parsed.invitation
        val peerIdentity = CanonicalIdentity(
            id = invitation.duplicateKey,
            displayName = invitation.alias ?: invitation.duplicateKey,
        )
        return try {
            contactRepository.addContactByLink(invitation.briarLink, invitation.alias)
            persistInvitationIdentity(peerIdentity, invitation.briarLink)
            val conversation = transportRouter?.ensureConversation(peerIdentity)
            if (conversation != null) {
                persistInvitationIdentity(peerIdentity, invitation.briarLink)
            }
            BriarInvitationAcceptanceResult.Accepted(
                invitation = invitation,
                conversationId = conversation?.id,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            BriarInvitationAcceptanceResult.Failed(invitation, error)
        }
    }

    private suspend fun persistInvitationIdentity(
        identity: CanonicalIdentity,
        briarLink: String,
    ) {
        identityRegistry?.upsertIdentity(
            identity = identity,
            aliases = mapOf(TransportId.BRIAR to briarLink),
            setAsCurrent = false,
        )
    }
}

sealed interface BriarInvitationAcceptanceResult {
    data class Accepted(
        val invitation: BriarInvitationLink,
        val conversationId: String? = null,
    ) : BriarInvitationAcceptanceResult
    data class InvalidLink(
        val reason: BriarInvitationLinkParseResult.InvalidReason,
    ) : BriarInvitationAcceptanceResult
    data class Failed(
        val invitation: BriarInvitationLink,
        val error: Throwable,
    ) : BriarInvitationAcceptanceResult
}
