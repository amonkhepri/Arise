package com.example.rise.testutil

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarConversation
import com.example.rise.briar.runtime.BriarConversationDescriptor
import com.example.rise.briar.runtime.BriarMessage
import com.example.rise.briar.runtime.BriarOutboundMessage
import com.example.rise.briar.runtime.BriarPresenceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun stubBriarChatGateway(
    isAvailable: Boolean = false,
    messagesFlowFactory: (String) -> Flow<List<BriarMessage>> = { flowOf(emptyList()) },
    onSend: (BriarOutboundMessage) -> Unit = {},
): BriarChatGateway {
    return object : BriarChatGateway {
        override val isAvailable: Boolean = isAvailable

        override suspend fun currentIdentity() = null

        override suspend fun ensureConversation(
            descriptor: BriarConversationDescriptor
        ): BriarConversation {
            return BriarConversation(descriptor.canonicalConversationId, descriptor.canonicalConversationId)
        }

        override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
            messagesFlowFactory(conversationId)

        override suspend fun sendMessage(message: BriarOutboundMessage) {
            onSend(message)
        }
    }
}

fun stubBriarContactService(
    isAvailable: Boolean = false,
    contactsFlow: Flow<List<BriarContact>> = flowOf(emptyList()),
): BriarContactService {
    return object : BriarContactService {
        override val isAvailable: Boolean = isAvailable

        override fun observeContacts(): Flow<List<BriarContact>> = contactsFlow
    }
}

fun briarContact(
    canonicalId: String = "briar-user",
    alias: String = "briar-alias",
    presence: BriarPresenceStatus = BriarPresenceStatus.UNKNOWN,
): BriarContact = BriarContact(
    canonicalId = canonicalId,
    transportAlias = alias,
    displayName = alias,
    presence = presence,
)
