package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarConversationDescriptor
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportConversationId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBriarChatAdapter(
    private val transportRuntimeBridge: TransportRuntimeBridge,
) : BriarChatAdapter {

    override suspend fun currentIdentity(): CanonicalIdentity {
        val gateway = transportRuntimeBridge.requireAvailableChatGateway()
        val identity = gateway.currentIdentity()
            ?: throw IllegalStateException("Briar runtime has no authenticated identity yet")
        return CanonicalIdentity(
            id = identity.id,
            displayName = identity.displayName,
        )
    }

    override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId {
        val gateway = transportRuntimeBridge.requireAvailableChatGateway()
        val descriptor = BriarConversationDescriptor(
            canonicalConversationId = conversation.id,
            participantIds = conversation.participants,
            title = conversation.title,
        )
        val result = gateway.ensureConversation(descriptor)
        return TransportConversationId(
            canonicalId = result.canonicalId,
            transportConversationId = result.transportConversationId,
        )
    }

    override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> {
        return transportRuntimeBridge.briarChatGateway.flatMapLatest { gateway ->
            if (!gateway.isAvailable) {
                flowOf(emptyList())
            } else {
                gateway.observeMessages(conversationId).map { messages ->
                    messages.map { it.toConnectorMessage() }
                }
            }
        }
    }

    override suspend fun sendMessage(message: ConnectorOutboundMessage) {
        val gateway = transportRuntimeBridge.requireAvailableChatGateway()
        gateway.sendMessage(message.toBriarOutboundMessage())
    }
}

private fun TransportRuntimeBridge.requireAvailableChatGateway(): BriarChatGateway {
    val gateway = briarChatGateway.value
    require(gateway.isAvailable) { "Briar chat gateway is not available" }
    return gateway
}
