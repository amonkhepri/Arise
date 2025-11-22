package com.example.rise.transport.connectors

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarIdentity
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.briar.BriarChatAdapter
import com.example.rise.transport.briar.BriarContactAdapter
import com.example.rise.transport.router.CanonicalConversation
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.ConnectorTelemetryEvent
import com.example.rise.transport.router.TransportConversationId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BriarConnectorTest {

    @Test
    fun `running runtime marks connector ready when gateways available`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val telemetry = mutableListOf<ConnectorTelemetryEvent>()
        val bridge = FakeTransportRuntimeBridge(BriarTransportMode.HYBRID)
        val connector = BriarConnector(
            transportBridge = bridge,
            telemetrySink = telemetry::add,
            briarChatAdapter = StubBriarChatAdapter(),
            briarContactAdapter = StubBriarContactAdapter(),
            dispatcher = dispatcher,
        )

        bridge.setChatGateway(AvailableChatGateway)
        bridge.setContactService(AvailableContactService)
        bridge.setRuntimeStatus(
            BriarRuntimeStatus(
                phase = BriarRuntimePhase.RUNNING,
            )
        )
        advanceUntilIdle()
        connector.currentIdentity() // mark chat ready
        advanceUntilIdle()

        assertEquals(ConnectorLifecycleState.READY, connector.lifecycle.value)
        assertTrue(connector.capabilities.value.entries["messages"]?.version == 1)
        assertEquals("true", connector.capabilities.value.entries["messages"]?.properties?.get("enabled"))
    }

    @Test
    fun `failed runtime emits telemetry failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val telemetry = mutableListOf<ConnectorTelemetryEvent>()
        val bridge = FakeTransportRuntimeBridge(BriarTransportMode.HYBRID)
        val connector = BriarConnector(
            transportBridge = bridge,
            telemetrySink = telemetry::add,
            briarChatAdapter = StubBriarChatAdapter(),
            briarContactAdapter = StubBriarContactAdapter(),
            dispatcher = dispatcher,
        )

        val error = IllegalStateException("boom")
        bridge.setRuntimeStatus(
            BriarRuntimeStatus(
                phase = BriarRuntimePhase.FAILED,
                lastError = error,
            )
        )
        advanceUntilIdle()

        assertEquals(ConnectorLifecycleState.FAILED, connector.lifecycle.value)
        assertTrue(telemetry.filterIsInstance<ConnectorTelemetryEvent.Failure>().any { it.error == error })
    }

    private class FakeTransportRuntimeBridge(initialMode: BriarTransportMode) : TransportRuntimeBridge {
        private val modeState = MutableStateFlow(initialMode)
        private val statusState = MutableStateFlow(BriarRuntimeStatus.stopped)
        private val chatGatewayState = MutableStateFlow<BriarChatGateway>(UnavailableChatGateway)
        private val contactServiceState = MutableStateFlow<BriarContactService>(UnavailableContactService)

        override val currentMode: StateFlow<BriarTransportMode> = modeState.asStateFlow()
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> = statusState.asStateFlow()
        override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val briarChatGateway: StateFlow<BriarChatGateway> = chatGatewayState.asStateFlow()
        override val briarContactService: StateFlow<BriarContactService> = contactServiceState.asStateFlow()

        override fun requireFirestore(caller: String) = Unit

        fun setRuntimeStatus(status: BriarRuntimeStatus) {
            statusState.value = status
        }

        fun setChatGateway(gateway: BriarChatGateway) {
            chatGatewayState.value = gateway
        }

        fun setContactService(service: BriarContactService) {
            contactServiceState.value = service
        }
    }

    private class StubBriarChatAdapter : BriarChatAdapter {
        override suspend fun currentIdentity(): CanonicalIdentity {
            return CanonicalIdentity("self", "Self")
        }

        override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId {
            return TransportConversationId(conversation.id, conversation.id)
        }

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> = emptyFlow()

        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit
    }

    private class StubBriarContactAdapter : BriarContactAdapter {
        override fun observeContacts(): Flow<List<com.example.rise.transport.router.ConnectorContact>> = emptyFlow()
    }

    private object AvailableChatGateway : BriarChatGateway {
        override val isAvailable: Boolean = true
        override suspend fun currentIdentity(): BriarIdentity? = BriarIdentity("self", "Self")
        override suspend fun ensureConversation(descriptor: com.example.rise.briar.runtime.BriarConversationDescriptor): com.example.rise.briar.runtime.BriarConversation {
            return com.example.rise.briar.runtime.BriarConversation(descriptor.canonicalConversationId, descriptor.canonicalConversationId)
        }
        override fun observeMessages(conversationId: String): Flow<List<com.example.rise.briar.runtime.BriarMessage>> = emptyFlow()
        override suspend fun sendMessage(message: com.example.rise.briar.runtime.BriarOutboundMessage) = Unit
    }

    private object AvailableContactService : BriarContactService {
        override val isAvailable: Boolean = true
        override fun observeContacts(): Flow<List<com.example.rise.briar.runtime.BriarContact>> = emptyFlow()
    }

    private object UnavailableChatGateway : BriarChatGateway {
        override val isAvailable: Boolean = false
        override suspend fun currentIdentity(): BriarIdentity? = null
        override suspend fun ensureConversation(descriptor: com.example.rise.briar.runtime.BriarConversationDescriptor): com.example.rise.briar.runtime.BriarConversation =
            throw UnsupportedOperationException()
        override fun observeMessages(conversationId: String): Flow<List<com.example.rise.briar.runtime.BriarMessage>> = emptyFlow()
        override suspend fun sendMessage(message: com.example.rise.briar.runtime.BriarOutboundMessage) = Unit
    }

    private object UnavailableContactService : BriarContactService {
        override val isAvailable: Boolean = false
        override fun observeContacts(): Flow<List<com.example.rise.briar.runtime.BriarContact>> = emptyFlow()
    }
}
