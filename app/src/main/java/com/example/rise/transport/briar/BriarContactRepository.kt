package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * App-facing entry point for Briar contact operations. Calls into the runtime contact service once
 * the embedded runtime is ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BriarContactRepository(
    private val transportRuntimeBridge: TransportRuntimeBridge,
    private val runtimeNotReadyError: () -> IllegalStateException = { IllegalStateException("Briar runtime is not ready") },
    private val serviceAvailabilityTimeoutMillis: Long = CONTACT_SERVICE_AVAILABILITY_TIMEOUT_MILLIS,
) {

    suspend fun addContactByLink(link: String, alias: String? = null) {
        val service = awaitAvailableContactService()
        Timber.tag(TAG).i(
            "addContactByLink service=%s alias=%s link=%s",
            service.debugName(),
            alias?.trim().orEmpty().ifBlank { "<default>" },
            link.abbreviated(),
        )
        service.addContactByLink(link, alias)
        val contacts = service.observeContacts().firstOrNull().orEmpty()
        Timber.tag(TAG).i("Immediate contact snapshot after addContactByLink -> %s", contacts.summary())
    }

    fun getHandshakeLink(): String {
        val service = transportRuntimeBridge.briarContactService.value
        if (!service.isAvailable) throw runtimeNotReadyError()
        return service.getHandshakeLink()
    }

    suspend fun snapshotAvailableContacts(): List<CanonicalIdentity> {
        val service = transportRuntimeBridge.briarContactService.value
        if (!service.isAvailable) {
            Timber.tag(TAG).i("snapshotAvailableContacts skipped because service=%s is unavailable", service.debugName())
            return emptyList()
        }
        val contacts = service.observeContacts().first()
        Timber.tag(TAG).i("snapshotAvailableContacts service=%s -> %s", service.debugName(), contacts.summary())
        return contacts.map { contact ->
                CanonicalIdentity(
                    id = contact.canonicalId,
                    displayName = contact.displayName,
                )
            }
    }

    suspend fun awaitAddedContactIdentity(
        existingCanonicalIds: Set<String>,
        timeoutMillis: Long = CONTACT_ADDITION_TIMEOUT_MILLIS,
    ): CanonicalIdentity? {
        val service = awaitAvailableContactService()
        Timber.tag(TAG).i(
            "awaitAddedContactIdentity existing=%s timeout=%sms service=%s",
            existingCanonicalIds.sorted().joinToString(prefix = "[", postfix = "]"),
            timeoutMillis,
            service.debugName(),
        )
        val currentContacts = service.observeContacts()
            .firstOrNull()
            .orEmpty()
        Timber.tag(TAG).i("awaitAddedContactIdentity initial snapshot -> %s", currentContacts.summary())
        currentContacts.firstAddedIdentity(existingCanonicalIds)?.let { added ->
            Timber.tag(TAG).i("awaitAddedContactIdentity resolved immediately -> %s", added.id)
            return added
        }
        if (timeoutMillis <= 0L) {
            Timber.tag(TAG).w("awaitAddedContactIdentity skipped because timeout=%sms", timeoutMillis)
            return null
        }

        return withTimeoutOrNull(timeoutMillis) {
            service.observeContacts()
                .onEach { contacts ->
                    Timber.tag(TAG).i("awaitAddedContactIdentity emission -> %s", contacts.summary())
                }
                .map { contacts -> contacts.firstAddedIdentity(existingCanonicalIds) }
                .firstOrNull { contact -> contact != null }
        }.also { resolved ->
            if (resolved == null) {
                Timber.tag(TAG).w("awaitAddedContactIdentity timed out after %sms", timeoutMillis)
            } else {
                Timber.tag(TAG).i("awaitAddedContactIdentity resolved -> %s", resolved.id)
            }
        }
    }

    private suspend fun awaitAvailableContactService(): BriarContactService {
        val currentService = transportRuntimeBridge.briarContactService.value
        if (currentService.isAvailable) {
            Timber.tag(TAG).i("Contact service already available -> %s", currentService.debugName())
            return currentService
        }
        Timber.tag(TAG).i("Waiting for contact service availability from %s", currentService.debugName())

        return withTimeoutOrNull(serviceAvailabilityTimeoutMillis) {
            transportRuntimeBridge.briarContactService
                .flatMapLatest { service ->
                    service.availability().map { available ->
                        if (available) service else null
                    }
                }
                .first { service -> service != null }
        }?.also { service ->
            Timber.tag(TAG).i("Contact service became available -> %s", service.debugName())
        } ?: throw runtimeNotReadyError().also {
            Timber.tag(TAG).e(it, "Timed out waiting for contact service after %sms", serviceAvailabilityTimeoutMillis)
        }
    }

    private companion object {
        private const val TAG = "BriarContactRepository"
        private const val CONTACT_SERVICE_AVAILABILITY_TIMEOUT_MILLIS = 20_000L
        private const val CONTACT_ADDITION_TIMEOUT_MILLIS = 5_000L
    }
}

private fun List<BriarContact>.firstAddedIdentity(
    existingCanonicalIds: Set<String>,
): CanonicalIdentity? = firstOrNull { contact ->
    !contact.isPending && contact.canonicalId !in existingCanonicalIds
}
    ?.let { contact ->
        CanonicalIdentity(
            id = contact.canonicalId,
            displayName = contact.displayName,
        )
    }

private fun BriarContactService.debugName(): String =
    this::class.simpleName ?: "UnknownBriarContactService"

private fun String.abbreviated(): String =
    if (length <= 24) this else "${take(24)}..."

private fun List<BriarContact>.summary(): String =
    if (isEmpty()) {
        "count=0"
    } else {
        "count=$size ids=${joinToString(prefix = "[", postfix = "]") { it.canonicalId }}"
    }
