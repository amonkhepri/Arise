package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.briarproject.bramble.api.contact.HandshakeLinkConstants

/**
 * Mirrors [NoOpBriarChatGateway] for the contact surface so consumers can observe
 * readiness changes even before the true implementation arrives.
 */
object NoOpBriarContactService : BriarContactService {
    private val readiness = MutableStateFlow(false)
    val readinessFlow: StateFlow<Boolean> = readiness

    override val isAvailable: Boolean
        get() = readiness.value

    override fun availability(): StateFlow<Boolean> = readinessFlow

    override suspend fun addContactByLink(link: String, alias: String?) {
        throw IllegalStateException("Briar runtime is not ready")
    }

    override fun getHandshakeLink(): String {
        if (!readiness.value) {
            throw IllegalStateException("Briar runtime is not ready")
        }
        return PLACEHOLDER_HANDSHAKE_LINK
    }

    override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())

    fun reset() {
        readiness.value = false
    }

    fun markReady() {
        readiness.value = true
    }

    private val PLACEHOLDER_HANDSHAKE_LINK =
        "briar://${"a".repeat(HandshakeLinkConstants.BASE32_LINK_BYTES)}"
}
