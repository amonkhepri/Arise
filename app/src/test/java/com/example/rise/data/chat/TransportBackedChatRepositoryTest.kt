package com.example.rise.data.chat

import app.cash.turbine.test
import com.example.rise.models.TextMessage
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CanonicalMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportRouter
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransportBackedChatRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `getCurrentUser maps canonical identity to chat user`() = scope.runTest {
        val transportRouter = StubTransportRouter(standardIdentity = canonicalIdentity("self"))
        val repository = TransportBackedChatRepository(transportRouter)

        val user = repository.getCurrentUser()

        assertEquals("self", user.id)
        assertEquals("Self", user.displayName)
    }

    @Test
    fun `getOrCreateConversation maps identity inputs`() = scope.runTest {
        val expectedConversation = CanonicalConversation(id = "conversation-42", participants = setOf("self", "other"), title = "Other")
        val router = StubTransportRouter(conversation = expectedConversation)
        val repository = TransportBackedChatRepository(router)

        val id = repository.getOrCreateConversation("other", "Other")

        assertEquals("conversation-42", id)
        assertEquals("other", router.capturedIdentity?.id)
        assertEquals("Other", router.capturedIdentity?.displayName)
    }

    @Test
    fun `observeMessages converts canonical messages to text messages`() = scope.runTest {
        val messagesFlow = MutableSharedFlow<List<CanonicalMessage>>(replay = 1)
        val router = StubTransportRouter(observedMessages = messagesFlow)
        val repository = TransportBackedChatRepository(router)

        repository.observeMessages("conversation-1").test {
            val canonicalMessage = canonicalMessage("conversation-1", "message-1")
            messagesFlow.emit(listOf(canonicalMessage))
            scope.advanceUntilIdle()

            val messages = awaitItem()
            assertEquals(1, messages.size)
            val textMessage = messages.first()
            assertEquals("body", textMessage.text)
            assertEquals("sender", textMessage.senderId)
            assertEquals("recipient", textMessage.recipientId)
            assertEquals("Sender", textMessage.senderName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage forwards outbound message to router`() = scope.runTest {
        val router = StubTransportRouter()
        val repository = TransportBackedChatRepository(router)

        val message = TextMessage(
            text = "hello",
            time = Date(1L),
            senderId = "me",
            recipientId = "you",
            senderName = "Me",
        )

        repository.sendMessage("conversation-1", message)

        val outbound = router.sentMessages.single()
        assertEquals("conversation-1", outbound.conversationId)
        assertEquals("me", outbound.senderId)
        assertEquals(setOf("you"), outbound.recipientIds)
        assertEquals("hello", outbound.body)
        assertEquals(message.time, outbound.timestamp)
    }

    private fun canonicalIdentity(id: String) = CanonicalIdentity(id = id, displayName = id.replaceFirstChar { it.uppercaseChar() })

    private fun canonicalMessage(conversationId: String, messageId: String) = CanonicalMessage(
        canonicalMessageId = messageId,
        conversationId = conversationId,
        senderId = "sender",
        recipientId = "recipient",
        senderName = "Sender",
        body = "body",
        transport = com.example.rise.transport.router.TransportId.FIRESTORE,
        timestamp = Date(0),
    )

    private class StubTransportRouter(
        private val standardIdentity: CanonicalIdentity = CanonicalIdentity("user", "User"),
        private val conversation: CanonicalConversation = CanonicalConversation("conversation", emptySet(), ""),
        private val observedMessages: MutableSharedFlow<List<CanonicalMessage>> = MutableSharedFlow(replay = 1),
    ) : TransportRouter {

        var capturedIdentity: CanonicalIdentity? = null
            private set
        val sentMessages = mutableListOf<ConnectorOutboundMessage>()

        override val currentIdentity: kotlinx.coroutines.flow.Flow<CanonicalIdentity>
            get() = error("not used")

        override suspend fun ensureCurrentIdentity(): CanonicalIdentity = standardIdentity

        override suspend fun ensureConversation(otherIdentity: CanonicalIdentity): CanonicalConversation {
            capturedIdentity = otherIdentity
            return conversation
        }

        override fun observeConversation(conversationId: String): kotlinx.coroutines.flow.Flow<List<CanonicalMessage>> = observedMessages

        override suspend fun send(message: ConnectorOutboundMessage) {
            sentMessages += message
        }
    }
}
