package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarConversation
import com.example.rise.briar.runtime.BriarConversationDescriptor
import com.example.rise.briar.runtime.BriarIdentity
import com.example.rise.briar.runtime.BriarMessage
import com.example.rise.briar.runtime.BriarOutboundMessage
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.ConnectorOutboundMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBriarChatAdapterTest {

    @Test
    fun `currentIdentity maps briar identity`() = runTest {
        val gateway = object : BriarChatGateway {
            override val isAvailable: Boolean = true
            override suspend fun currentIdentity(): BriarIdentity =
                BriarIdentity(id = "briar-123", displayName = "Briar User")

            override suspend fun ensureConversation(descriptor: BriarConversationDescriptor) =
                stubConversation(descriptor)

            override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: BriarOutboundMessage) = Unit
        }
        val adapter = DefaultBriarChatAdapter(fakeBridge(gateway))

        val identity = adapter.currentIdentity()

        assertEquals("briar-123", identity.id)
        assertEquals("Briar User", identity.displayName)
    }

    @Test
    fun `ensureConversation delegates to gateway`() = runTest {
        var capturedDescriptor: BriarConversationDescriptor? = null
        val gateway = object : BriarChatGateway {
            override val isAvailable: Boolean = true
            override suspend fun currentIdentity(): BriarIdentity? = null
            override suspend fun ensureConversation(
                descriptor: BriarConversationDescriptor
            ) = stubConversation(descriptor).also { capturedDescriptor = descriptor }

            override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: BriarOutboundMessage) = Unit
        }
        val adapter = DefaultBriarChatAdapter(fakeBridge(gateway))

        val conversationId = adapter.ensureConversation(
            CanonicalConversation(id = "canon-1", participants = setOf("a", "b"), title = "Test")
        )

        assertEquals("canon-1", conversationId.canonicalId)
        assertEquals("canon-1", conversationId.transportConversationId)
        assertEquals(setOf("a", "b"), capturedDescriptor?.participantIds)
    }

    @Test
    fun `observeMessages maps inbound messages`() = runTest {
        val emission = listOf(
            BriarMessage(
                messageId = "m1",
                conversationId = "c1",
                senderId = "sender",
                senderName = "Sender",
                recipientIds = setOf("recipient"),
                body = "hello",
                timestamp = Instant.ofEpochSecond(1),
            )
        )
        val gateway = object : BriarChatGateway {
            override val isAvailable: Boolean = true
            override suspend fun currentIdentity(): BriarIdentity? = null
            override suspend fun ensureConversation(descriptor: BriarConversationDescriptor) =
                stubConversation(descriptor)

            override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
                flowOf(emission)

            override suspend fun sendMessage(message: BriarOutboundMessage) = Unit
        }
        val adapter = DefaultBriarChatAdapter(fakeBridge(gateway))

        val mapped = adapter.observeMessages("c1").first()

        assertEquals(1, mapped.size)
        assertEquals("m1", mapped.first().messageId)
    }

    @Test
    fun `sendMessage converts outbound payload`() = runTest {
        var capturedMessage: BriarOutboundMessage? = null
        val gateway = object : BriarChatGateway {
            override val isAvailable: Boolean = true
            override suspend fun currentIdentity(): BriarIdentity? = null
            override suspend fun ensureConversation(descriptor: BriarConversationDescriptor) =
                stubConversation(descriptor)

            override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> =
                flowOf(emptyList())

            override suspend fun sendMessage(message: BriarOutboundMessage) {
                capturedMessage = message
            }
        }
        val adapter = DefaultBriarChatAdapter(fakeBridge(gateway))
        val outbound = ConnectorOutboundMessage(
            conversationId = "c1",
            senderId = "sender",
            senderName = "Sender",
            recipientIds = setOf("recipient"),
            body = "hey",
            timestamp = Date.from(Instant.ofEpochSecond(5)),
        )

        adapter.sendMessage(outbound)

        val payload = capturedMessage
        requireNotNull(payload)
        assertEquals("c1", payload.conversationId)
        assertEquals("sender", payload.senderId)
        assertEquals("hey", payload.body)
    }

    private fun stubConversation(descriptor: BriarConversationDescriptor) =
        BriarConversation(descriptor.canonicalConversationId, descriptor.canonicalConversationId)

    private fun fakeBridge(
        chatGateway: BriarChatGateway
    ): TransportRuntimeBridge {
        val chatFlow = MutableStateFlow(chatGateway)
        val contactFlow = MutableStateFlow<BriarContactService>(NoOpBriarContactService)
        return object : TransportRuntimeBridge {
            override val currentMode: StateFlow<BriarTransportMode> =
                MutableStateFlow(BriarTransportMode.HYBRID).asStateFlow()
            override val runtimeStatus: StateFlow<BriarRuntimeStatus> =
                MutableStateFlow(BriarRuntimeStatus(BriarRuntimePhase.RUNNING)).asStateFlow()
            override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
            override val briarChatGateway: StateFlow<BriarChatGateway> = chatFlow
            override val briarContactService = contactFlow.asStateFlow()
            override fun requireFirestore(caller: String) = Unit
        }
    }
}
