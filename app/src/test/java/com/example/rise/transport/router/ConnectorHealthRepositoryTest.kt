package com.example.rise.transport.router

import app.cash.turbine.test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorHealthRepositoryTest {

    @Test
    fun `emits messaging readiness and readiness telemetry`() = runTest {
        val telemetry = RecordingTelemetrySink()
        val connector = ReadyTogglingConnector()
        val registry = object : ConnectorRegistry {
            override val connectors: Set<TransportConnector> = setOf(connector)
            override fun connectorFor(transportId: TransportId): TransportConnector? =
                connectors.firstOrNull { it.transport == transportId }
            override fun primaryFor(mode: com.example.rise.featureflags.BriarTransportMode): TransportConnector = connector
            override fun mirrorsFor(mode: com.example.rise.featureflags.BriarTransportMode): List<TransportConnector> = emptyList()
        }

        val repository = ConnectorHealthRepository(
            connectorRegistry = registry,
            telemetrySink = telemetry,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.health.test {
            val initial = awaitItem()
            val snapshot = initial[TransportId.FIRESTORE]
            assertEquals("initial readiness false", false, snapshot?.messagingReady)

            // Enable messaging capability to signal readiness.
            connector.enableMessaging()
            val updated = awaitItem()[TransportId.FIRESTORE]
            assertEquals("updated readiness true", true, updated?.messagingReady)
            assertTrue(
                "Expected readiness telemetry when connector becomes ready",
                telemetry.events.any { it is ConnectorTelemetryEvent.ReadinessChanged && it.ready }
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    private class ReadyTogglingConnector : TransportConnector {
        private val statusFlow = MutableStateFlow(ConnectorStatus.ACTIVE)
        private val lifecycleFlow = MutableStateFlow(ConnectorLifecycleState.READY)
        private val capabilityFlow = MutableStateFlow(
            ConnectorCapabilities(
                mapOf("messages" to CapabilityDescriptor(1, mapOf("enabled" to "false")))
            )
        )

        override val transport: TransportId = TransportId.FIRESTORE
        override val status = statusFlow
        override val lifecycle = lifecycleFlow
        override val capabilities = capabilityFlow

        override suspend fun currentIdentity(): CanonicalIdentity =
            CanonicalIdentity("self", "Self")

        override suspend fun ensureConversation(conversation: CanonicalConversation): TransportConversationId =
            TransportConversationId(conversation.id, conversation.id)

        override fun observeMessages(conversationId: String) = kotlinx.coroutines.flow.emptyFlow<List<ConnectorInboundMessage>>()
        override suspend fun sendMessage(message: ConnectorOutboundMessage) = Unit

        fun enableMessaging() {
            capabilityFlow.value = ConnectorCapabilities(
                mapOf("messages" to CapabilityDescriptor(1, mapOf("enabled" to "true")))
            )
        }
    }

    private class RecordingTelemetrySink : ConnectorTelemetrySink {
        val events = mutableListOf<ConnectorTelemetryEvent>()
        override fun emit(event: ConnectorTelemetryEvent) {
            events += event
        }
    }
}
