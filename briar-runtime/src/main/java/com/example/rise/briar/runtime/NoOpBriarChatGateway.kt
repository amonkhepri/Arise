package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Lightweight `BriarChatGateway` that never exposes real functionality. It keeps the
 * wiring stable during Stage 1 while allowing downstream code to observe readiness flips.
 */
object NoOpBriarChatGateway : BriarChatGateway {
    private val readiness = MutableStateFlow(false)
    val readinessFlow: StateFlow<Boolean> = readiness

    override val isAvailable: Boolean
        get() = readiness.value

    override fun availability(): StateFlow<Boolean> = readinessFlow

    override suspend fun currentIdentity(): BriarIdentity? = null

    override suspend fun ensureConversation(
        descriptor: BriarConversationDescriptor
    ): BriarConversation {
        throw IllegalStateException("Briar chat gateway is not available(NoOp)")
    }

    override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
        flowOf(emptyList())

    override suspend fun sendMessage(message: BriarOutboundMessage) {
        throw IllegalStateException("Briar chat gateway is not available(NoOp)")
    }

    fun reset() {
        readiness.value = false
    }

    fun markReady() {
        readiness.value = true
    }
}
