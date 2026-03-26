package com.example.rise.transport.briar.invite

import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.CancellationException

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
            val existingContactIds = contactRepository
                .snapshotAvailableContacts()
                .mapTo(mutableSetOf()) { contact -> contact.id }
            transportRouter?.ensureCurrentIdentity()
            var fallbackPeerIdentity = peerIdentity
            try {
                contactRepository.addContactByLink(invitation.briarLink, invitation.alias)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!error.isPendingContactExistsFailure()) throw error
                fallbackPeerIdentity = contactRepository
                    .snapshotAvailableContacts()
                    .firstNewPendingIdentity(existingContactIds)
                    ?: error.pendingContactIdentity()
                    ?: peerIdentity
            }
            val bootstrap = bootstrapConversationIfResolvable(
                peerIdentity = fallbackPeerIdentity,
                existingContactIds = existingContactIds,
            )
            BriarInvitationAcceptanceResult.Accepted(
                invitation = invitation,
                conversationId = bootstrap.conversation?.id,
                contactIdentity = bootstrap.contactIdentity,
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
        existingContactIds: Set<String>,
    ): BootstrapResult {
        val router = transportRouter ?: return BootstrapResult(
            conversation = null,
            contactIdentity = peerIdentity,
        )
        var activePeerIdentity = peerIdentity
        repeat(maxDeferredConversationRetries) {
            if (activePeerIdentity.id.startsWith(PENDING_CONTACT_ID_PREFIX)) {
                val resolvedIdentity = contactRepository.awaitAddedContactIdentity(
                    existingCanonicalIds = existingContactIds,
                    timeoutMillis = deferredConversationRetryDelayMillis,
                )
                if (resolvedIdentity == null) {
                    return@repeat
                }
                activePeerIdentity = resolvedIdentity
            }
            try {
                return BootstrapResult(
                    conversation = router.ensureConversation(activePeerIdentity),
                    contactIdentity = activePeerIdentity,
                )
            } catch (error: Throwable) {
                if (!shouldDeferConversationBootstrap(error)) {
                    throw error
                }
                val resolvedIdentity = contactRepository.awaitAddedContactIdentity(
                    existingCanonicalIds = existingContactIds,
                    timeoutMillis = deferredConversationRetryDelayMillis,
                )
                val surfacedPendingIdentity = if (resolvedIdentity == null) {
                    contactRepository
                        .snapshotAvailableContacts()
                        .firstNewPendingIdentity(existingContactIds)
                } else {
                    null
                }
                if (resolvedIdentity == null && activePeerIdentity.id.startsWith(PENDING_CONTACT_ID_PREFIX)) {
                    return BootstrapResult(
                        conversation = null,
                        contactIdentity = activePeerIdentity,
                    )
                }
                if (surfacedPendingIdentity != null) {
                    return BootstrapResult(
                        conversation = null,
                        contactIdentity = surfacedPendingIdentity,
                    )
                }
                activePeerIdentity = resolvedIdentity ?: activePeerIdentity
            }
        }

        if (activePeerIdentity.id.startsWith(PENDING_CONTACT_ID_PREFIX)) {
            val resolvedIdentity = contactRepository.awaitAddedContactIdentity(
                existingCanonicalIds = existingContactIds,
                timeoutMillis = deferredConversationRetryDelayMillis,
            ) ?: return BootstrapResult(
                conversation = null,
                contactIdentity = activePeerIdentity,
            )
            activePeerIdentity = resolvedIdentity
        }

        return runCatching {
            BootstrapResult(
                conversation = router.ensureConversation(activePeerIdentity),
                contactIdentity = activePeerIdentity,
            )
        }.getOrElse { error ->
            if (shouldDeferConversationBootstrap(error)) {
                BootstrapResult(
                    conversation = null,
                    contactIdentity = activePeerIdentity,
                )
            } else {
                throw error
            }
        }
    }

    private fun shouldDeferConversationBootstrap(error: Throwable): Boolean =
        error is IllegalArgumentException &&
            error.message.orEmpty().contains("numeric Briar contact id", ignoreCase = true)

    private fun Throwable.isPendingContactExistsFailure(): Boolean =
        generateSequence(this) { it.cause }
            .any { current -> current::class.simpleName == PENDING_CONTACT_EXISTS_EXCEPTION_NAME }

    private fun List<CanonicalIdentity>.firstNewPendingIdentity(
        existingContactIds: Set<String>,
    ): CanonicalIdentity? = firstOrNull { identity ->
        identity.id.startsWith(PENDING_CONTACT_ID_PREFIX) && identity.id !in existingContactIds
    }

    private fun Throwable.pendingContactIdentity(): CanonicalIdentity? =
        generateSequence(this) { it.cause }
            .mapNotNull { current -> current.extractPendingContactIdentity() }
            .firstOrNull()

    private fun Throwable.extractPendingContactIdentity(): CanonicalIdentity? {
        val pendingContact = readGetter("getPendingContact") ?: return null
        val pendingId = pendingContact
            .readGetter("getId")
            ?.readGetter("getBytes") as? ByteArray
            ?: return null
        val canonicalId = "$PENDING_CONTACT_ID_PREFIX${pendingId.toHex()}"
        val displayName = (pendingContact.readGetter("getAlias") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: canonicalId
        return CanonicalIdentity(
            id = canonicalId,
            displayName = displayName,
        )
    }

    private fun Any.readGetter(name: String): Any? =
        runCatching { javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(this) }
            .getOrNull()

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
        private const val PENDING_CONTACT_EXISTS_EXCEPTION_NAME = "PendingContactExistsException"
        private const val PENDING_CONTACT_ID_PREFIX = "pending:"
    }

    private data class BootstrapResult(
        val conversation: com.example.rise.transport.router.CanonicalConversation?,
        val contactIdentity: CanonicalIdentity,
    )
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }

enum class BriarInvitationRejectionReason {
    DUPLICATE,
    EXPIRED,
    INVALID,
}

sealed interface BriarInvitationAcceptanceResult {
    data class Accepted(
        val invitation: BriarInvitationLink,
        val conversationId: String? = null,
        val contactIdentity: CanonicalIdentity? = null,
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
