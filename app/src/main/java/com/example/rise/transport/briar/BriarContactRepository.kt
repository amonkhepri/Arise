package com.example.rise.transport.briar

import com.example.rise.transport.TransportRuntimeBridge

/**
 * App-facing entry point for Briar contact operations. Calls into the runtime contact service once
 * the embedded runtime is ready.
 */
class BriarContactRepository(
    private val transportRuntimeBridge: TransportRuntimeBridge,
    private val runtimeNotReadyError: () -> IllegalStateException = { IllegalStateException("Briar runtime is not ready") },
) {

    suspend fun addContactByLink(link: String, alias: String? = null) {
        val service = transportRuntimeBridge.briarContactService.value
        if (!service.isAvailable) throw runtimeNotReadyError()
        service.addContactByLink(link, alias)
    }

    fun getHandshakeLink(): String {
        val service = transportRuntimeBridge.briarContactService.value
        if (!service.isAvailable) throw runtimeNotReadyError()
        return service.getHandshakeLink()
    }
}
