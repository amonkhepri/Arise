package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

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
        service.addContactByLink(link, alias)
    }

    fun getHandshakeLink(): String {
        val service = transportRuntimeBridge.briarContactService.value
        if (!service.isAvailable) throw runtimeNotReadyError()
        return service.getHandshakeLink()
    }

    private suspend fun awaitAvailableContactService(): BriarContactService {
        val currentService = transportRuntimeBridge.briarContactService.value
        if (currentService.isAvailable) return currentService

        return withTimeoutOrNull(serviceAvailabilityTimeoutMillis) {
            transportRuntimeBridge.briarContactService
                .flatMapLatest { service ->
                    service.availability().map { available ->
                        if (available) service else null
                    }
                }
                .first { service -> service != null }
        } ?: throw runtimeNotReadyError()
    }

    private companion object {
        private const val CONTACT_SERVICE_AVAILABILITY_TIMEOUT_MILLIS = 20_000L
    }
}
