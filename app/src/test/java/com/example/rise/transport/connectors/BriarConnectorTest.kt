package com.example.rise.transport.connectors

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.briar.BriarChatAdapter
import com.example.rise.transport.briar.BriarContactAdapter
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.TransportConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BriarConnectorTest {

    @Test
    fun `connector stays stopped when mode is Firestore`() = runTest {
        val bridge = FakeTransportBridge()
        val connector = BriarConnector(
            transportBridge = bridge,
            telemetrySink = { },
            briarChatAdapter = FakeChatAdapter(),
            briarContactAdapter = FakeContactAdapter(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        advanceUntilIdle()
        assertEquals(ConnectorLifecycleState.STOPPED, connector.lifecycle.value)
    }

    @Test
    fun `hybrid mode mirrors runtime status`() = runTest {
        val bridge = FakeTransportBridge()
        val connector = BriarConnector(
            transportBridge = bridge,
            telemetrySink = { },
            briarChatAdapter = FakeChatAdapter(),
            briarContactAdapter = FakeContactAdapter(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        advanceUntilIdle()
        bridge.currentModeFlow.value = BriarTransportMode.HYBRID
        advanceUntilIdle()
        assertEquals(ConnectorLifecycleState.AUTHENTICATING, connector.lifecycle.value)

        bridge.runtimeStatusFlow.value = BriarRuntimeStatus(BriarRuntimePhase.RUNNING)
        advanceUntilIdle()
        assertEquals(ConnectorLifecycleState.DEGRADED, connector.lifecycle.value)

        bridge.runtimeStatusFlow.value = BriarRuntimeStatus(BriarRuntimePhase.FAILED)
        advanceUntilIdle()
        assertEquals(ConnectorLifecycleState.FAILED, connector.lifecycle.value)
    }

    private class FakeTransportBridge : TransportRuntimeBridge {
        val currentModeFlow = MutableStateFlow(BriarTransportMode.FIRESTORE)
        val runtimeStatusFlow = MutableStateFlow(BriarRuntimeStatus(BriarRuntimePhase.STOPPED))

        override val currentMode: StateFlow<BriarTransportMode> = currentModeFlow.asStateFlow()
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> = runtimeStatusFlow.asStateFlow()
        override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val briarChatGateway: StateFlow<BriarChatGateway> =
            MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway).asStateFlow()
        override val briarContactService: StateFlow<BriarContactService> =
            MutableStateFlow<BriarContactService>(NoOpBriarContactService).asStateFlow()
        override fun requireFirestore(caller: String) = Unit
    }

    private class FakeChatAdapter : BriarChatAdapter {
        override suspend fun currentIdentity(): CanonicalIdentity =
            CanonicalIdentity("id", "name")

        override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId =
            TransportConversationId(conversation.id, "briar-${conversation.id}")

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> =
            flowOf(emptyList())

        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
    }

    private class FakeContactAdapter : BriarContactAdapter {
        override fun observeContacts(): Flow<List<ConnectorContact>> = flowOf(emptyList())
    }
}
