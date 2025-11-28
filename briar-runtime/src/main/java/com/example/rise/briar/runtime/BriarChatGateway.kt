package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Contract the embedded Briar runtime exposes for chat-specific operations. The transport router
 * relies on this gateway to negotiate conversations, stream inbound events, and post outbound
 * payloads without depending on platform-specific APIs.
 */
interface BriarChatGateway {
    /** True when the underlying Briar runtime is initialized and ready for chat traffic. */
    val isAvailable: Boolean

    /**
     * Emits availability changes so connectors can react when readiness toggles without the
     * gateway instance being replaced.
     */
    fun availability(): Flow<Boolean> = flowOf(isAvailable)

    /** Returns the active Briar identity, or null if the user has not finished provisioning. */
    suspend fun currentIdentity(): BriarIdentity?

    /**
     * Ensures a concrete Briar conversation exists for the provided descriptor and returns the
     * materialized conversation instance used by downstream transport layers.
     */
    suspend fun ensureConversation(descriptor: BriarConversationDescriptor): BriarConversation

    /**
     * Streams the current and future inbound messages for the target conversation id. The flow is
     * hot and emits fresh lists whenever the Briar runtime reports new messages.
     */
    fun observeMessages(conversationId: String): Flow<List<BriarMessage>>

    /** Sends the provided outbound message through the Briar runtime. */
    suspend fun sendMessage(message: BriarOutboundMessage)
}
