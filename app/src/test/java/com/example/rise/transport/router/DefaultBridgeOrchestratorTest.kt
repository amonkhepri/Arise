package com.example.rise.transport.router

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class DefaultBridgeOrchestratorTest {

    @Test
    fun `hybrid mode selects briar when ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtimeBridge = FakeRuntimeBridge(BriarTransportMode.HYBRID)
        val firestore = TestConnector(TransportId.FIRESTORE)
        val briar = TestConnector(TransportId.BRIAR, ConnectorLifecycleState.READY)
        val registry = DefaultConnectorRegistry(setOf(firestore, briar))
        val orchestrator = DefaultBridgeOrchestrator(
            transportBridge = runtimeBridge,
            connectorRegistry = registry,
            telemetrySink = { },
            dispatcher = dispatcher,
            externalScope = backgroundScope,
        )

        advanceUntilIdle()

        val snapshot = orchestrator.routingState.value
        assertEquals(TransportId.BRIAR, snapshot.primary)
        assertEquals(PrimaryRoutingReason.PreferredReady, snapshot.reason)
    }

    @Test
    fun `hybrid mode falls back to firestore when briar not ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtimeBridge = FakeRuntimeBridge(BriarTransportMode.HYBRID)
        val firestore = TestConnector(TransportId.FIRESTORE, ConnectorLifecycleState.READY)
        val briar = TestConnector(TransportId.BRIAR, ConnectorLifecycleState.AUTHENTICATING)
        val registry = DefaultConnectorRegistry(setOf(firestore, briar))
        val orchestrator = DefaultBridgeOrchestrator(
            transportBridge = runtimeBridge,
            connectorRegistry = registry,
            telemetrySink = { },
            dispatcher = dispatcher,
            externalScope = backgroundScope,
        )

        advanceUntilIdle()

        val snapshot = orchestrator.routingState.value
        assertEquals(TransportId.FIRESTORE, snapshot.primary)
        assertEquals(PrimaryRoutingReason.PreferredNotReady, snapshot.reason)
        assertEquals(TransportId.BRIAR, snapshot.fallbackTarget)
    }

    @Test
    fun `firestore mode forces firestore even if briar ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtimeBridge = FakeRuntimeBridge(BriarTransportMode.FIRESTORE)
        val firestore = TestConnector(TransportId.FIRESTORE, ConnectorLifecycleState.READY)
        val briar = TestConnector(TransportId.BRIAR, ConnectorLifecycleState.READY)
        val registry = DefaultConnectorRegistry(setOf(firestore, briar))
        val orchestrator = DefaultBridgeOrchestrator(
            transportBridge = runtimeBridge,
            connectorRegistry = registry,
            telemetrySink = { },
            dispatcher = dispatcher,
            externalScope = backgroundScope,
        )

        advanceUntilIdle()

        val snapshot = orchestrator.routingState.value
        assertEquals(TransportId.FIRESTORE, snapshot.primary)
        assertEquals(PrimaryRoutingReason.FlagForcesFirestore, snapshot.reason)
    }

    @Test
    fun `explicit fallback updates routing snapshot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtimeBridge = FakeRuntimeBridge(BriarTransportMode.HYBRID)
        val firestore = TestConnector(TransportId.FIRESTORE, ConnectorLifecycleState.READY)
        val briar = TestConnector(TransportId.BRIAR, ConnectorLifecycleState.READY)
        val registry = DefaultConnectorRegistry(setOf(firestore, briar))
        val orchestrator = DefaultBridgeOrchestrator(
            transportBridge = runtimeBridge,
            connectorRegistry = registry,
            telemetrySink = { },
            dispatcher = dispatcher,
            externalScope = backgroundScope,
        )

        advanceUntilIdle()
        orchestrator.onPrimaryFallback(
            fromTransport = TransportId.BRIAR,
            toTransport = TransportId.FIRESTORE,
            reason = ConnectorLifecycleState.FAILED,
        )

        val snapshot = orchestrator.routingState.value
        assertEquals(PrimaryRoutingReason.ExplicitFallback, snapshot.reason)
        assertEquals(TransportId.FIRESTORE, snapshot.primary)
        assertEquals(TransportId.BRIAR, snapshot.fallbackTarget)
    }

    private class FakeRuntimeBridge(initialMode: BriarTransportMode) : TransportRuntimeBridge {
        private val modeFlow = MutableStateFlow(initialMode)
        override val currentMode: StateFlow<BriarTransportMode> = modeFlow.asStateFlow()
        override val runtimeStatus: StateFlow<BriarRuntimeStatus> =
            MutableStateFlow(BriarRuntimeStatus.stopped).asStateFlow()
        override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
        override val briarChatGateway: StateFlow<BriarChatGateway> =
            MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway).asStateFlow()
        override val briarContactService: StateFlow<BriarContactService> =
            MutableStateFlow<BriarContactService>(NoOpBriarContactService).asStateFlow()
        override fun requireFirestore(caller: String) = Unit
    }

    private class TestConnector(
        override val transport: TransportId,
        initialLifecycle: ConnectorLifecycleState = ConnectorLifecycleState.INITIAL,
    ) : TransportConnector {
        private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)
        private val lifecycleFlow = MutableStateFlow(initialLifecycle)
        private val capabilityFlow = MutableStateFlow(ConnectorCapabilities.EMPTY)

        override val status: StateFlow<ConnectorStatus> = statusFlow
        override val lifecycle: StateFlow<ConnectorLifecycleState> = lifecycleFlow
        override val capabilities: StateFlow<ConnectorCapabilities> = capabilityFlow

        override suspend fun currentIdentity(): CanonicalIdentity =
            error("Not used")

        override suspend fun ensureConversation(conversation: CanonicalConversation): String =
            error("Not used")

        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> =
            error("Not used")

        override suspend fun sendMessage(message: ConnectorOutboundMessage) =
            error("Not used")
    }
}
