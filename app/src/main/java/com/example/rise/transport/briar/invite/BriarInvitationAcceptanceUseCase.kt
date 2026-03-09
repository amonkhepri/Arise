package com.example.rise.transport.briar.invite

import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class BriarInvitationAcceptanceUseCase(
    private val contactRepository: BriarContactRepository,
    private val parser: BriarInvitationLinkParser = BriarInvitationLinkParser(),
    private val identityRegistry: IdentityRegistry? = null,
    private val transportRouter: TransportRouter? = null,
    private val deferredConversationRetryDelayMillis: Long = DEFERRED_CONVERSATION_RETRY_DELAY_MILLIS,
    private val maxDeferredConversationRetries: Int = MAX_DEFERRED_CONVERSATION_RETRIES,
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
            val conversation = bootstrapConversationIfResolvable(peerIdentity)
            BriarInvitationAcceptanceResult.Accepted(
                invitation = invitation,
                conversationId = conversation?.id,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            classifyRejection(error)?.let { reason ->
                BriarInvitationAcceptanceResult.Failed(
                    invitation = invitation,
                    error = wrapRejection(error, reason),
                )
            } ?: BriarInvitationAcceptanceResult.Failed(invitation, error)
        }
    }

    private suspend fun bootstrapConversationIfResolvable(
        peerIdentity: CanonicalIdentity,
    ): com.example.rise.transport.router.CanonicalConversation? {
        val router = transportRouter ?: return null
        repeat(maxDeferredConversationRetries) {
            try {
                return router.ensureConversation(peerIdentity)
            } catch (error: Throwable) {
                if (!shouldDeferConversationBootstrap(error)) {
                    throw error
                }
                delay(deferredConversationRetryDelayMillis)
            }
        }

        return runCatching {
            router.ensureConversation(peerIdentity)
        }.getOrElse { error ->
            if (shouldDeferConversationBootstrap(error)) {
                null
            } else {
                throw error
            }
        }
    }

    private fun shouldDeferConversationBootstrap(error: Throwable): Boolean =
        error is IllegalArgumentException &&
            error.message.orEmpty().contains("numeric Briar contact id", ignoreCase = true)

    private fun classifyRejection(error: Throwable): BriarInvitationRejectionReason? {
        generateSequence(error) { it.cause }.forEach { current ->
            val text = buildString {
                append(current::class.simpleName.orEmpty())
                append(' ')
                append(current.message.orEmpty())
            }
            when {
                text.containsAnyIgnoreCase("duplicate", "already added", "already exists", "already used") -> {
                    return BriarInvitationRejectionReason.DUPLICATE
                }
                text.containsAnyIgnoreCase("expired", "no longer valid") -> {
                    return BriarInvitationRejectionReason.EXPIRED
                }
                text.containsAnyIgnoreCase("invalid", "malformed", "bad invitation", "unsupported") -> {
                    return BriarInvitationRejectionReason.INVALID
                }
            }
        }
        return null
    }

    private fun wrapRejection(
        error: Throwable,
        reason: BriarInvitationRejectionReason,
    ): Throwable = when {
        error is BriarInvitationClassifiedFailure && error.reason == reason -> error
        reason == BriarInvitationRejectionReason.DUPLICATE -> BriarInvitationDuplicateFailure(error)
        reason == BriarInvitationRejectionReason.EXPIRED -> BriarInvitationExpiredFailure(error)
        else -> BriarInvitationInvalidFailure(error)
    }

    private fun String.containsAnyIgnoreCase(vararg needles: String): Boolean =
        needles.any { needle -> contains(needle, ignoreCase = true) }

    private companion object {
        private const val MAX_DEFERRED_CONVERSATION_RETRIES = 120
        private const val DEFERRED_CONVERSATION_RETRY_DELAY_MILLIS = 250L
    }
}

enum class BriarInvitationRejectionReason {
    DUPLICATE,
    EXPIRED,
    INVALID,
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

sealed class BriarInvitationClassifiedFailure(
    val reason: BriarInvitationRejectionReason,
    cause: Throwable,
) : IllegalStateException(cause.message ?: "Unable to accept Briar invitation.", cause)

class BriarInvitationDuplicateFailure(
    cause: Throwable,
) : BriarInvitationClassifiedFailure(BriarInvitationRejectionReason.DUPLICATE, cause)

class BriarInvitationExpiredFailure(
    cause: Throwable,
) : BriarInvitationClassifiedFailure(BriarInvitationRejectionReason.EXPIRED, cause)

class BriarInvitationInvalidFailure(
    cause: Throwable,
) : BriarInvitationClassifiedFailure(BriarInvitationRejectionReason.INVALID, cause)
