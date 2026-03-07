package com.example.rise.testutil

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarConversation
import com.example.rise.briar.runtime.BriarConversationDescriptor
import com.example.rise.briar.runtime.BriarMessage
import com.example.rise.briar.runtime.BriarOutboundMessage
import com.example.rise.briar.runtime.BriarPresenceStatus
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    onAddContactByLink: (String, String?) -> Unit = { _, _ -> },
): BriarContactService {
    return object : BriarContactService {
        override val isAvailable: Boolean = isAvailable
        override suspend fun addContactByLink(link: String, alias: String?) {
            onAddContactByLink(link, alias)
        }

        override fun observeContacts(): Flow<List<BriarContact>> = contactsFlow
    }
}

fun stubTransportRuntimeBridge(
    mode: BriarTransportMode = BriarTransportMode.BRIAR_ONLY,
    runtimeStatus: BriarRuntimeStatus = BriarRuntimeStatus(BriarRuntimePhase.RUNNING),
    chatGateway: BriarChatGateway = stubBriarChatGateway(),
    contactService: BriarContactService = stubBriarContactService(),
): TransportRuntimeBridge {
    val modeFlow = MutableStateFlow(mode)
    val runtimeStatusFlow = MutableStateFlow(runtimeStatus)
    val diagnosticsFlow = MutableSharedFlow<BriarRuntimeEvent>()
    val chatGatewayFlow = MutableStateFlow(chatGateway)
    val contactServiceFlow = MutableStateFlow(contactService)
    return object : TransportRuntimeBridge {
        override val currentMode = modeFlow
        override val runtimeStatus = runtimeStatusFlow
        override val diagnostics = diagnosticsFlow
        override val briarChatGateway = chatGatewayFlow
        override val briarContactService = contactServiceFlow

        override fun requireFirestore(caller: String) = Unit
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
