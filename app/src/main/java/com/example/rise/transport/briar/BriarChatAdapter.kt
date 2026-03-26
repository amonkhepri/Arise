package com.example.rise.transport.briar

import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportConversationId
import kotlinx.coroutines.flow.Flow

/**
 * Adapter that lets the transport router talk to the Briar runtime using canonical router models.
 * Implementations translate router-level conversations, identities, and messages into the runtime
 * primitives exposed by the Briar gateway layer.
 */
interface BriarChatAdapter {
    /** Returns the router-facing representation of the active Briar identity. */
    suspend fun currentIdentity(): CanonicalIdentity

    /**
     * Ensures a Briar-side conversation exists for the canonical descriptor and returns the stable
     * id used when subscribing to message flows.
     */
    suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId

    /**
     * Streams inbound Briar messages as canonical connector messages for the provided conversation
     * id. The emitted list always reflects the latest known state.
     */
    fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>>

    /** Sends a canonical outbound message through the Briar transport layer. */
    suspend fun sendMessage(message: ConnectorOutboundMessage)
}
