package com.example.rise.transport.router

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observes every registered connector and emits their latest lifecycle/status/capability snapshot.
 * The repository also feeds telemetry whenever a connector changes state or capabilities.
 */
interface ConnectorHealthProvider {
    val health: StateFlow<Map<TransportId, ConnectorHealth>>
}

class ConnectorHealthRepository(
    connectorRegistry: ConnectorRegistry,
    private val telemetrySink: ConnectorTelemetrySink,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ConnectorHealthProvider {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _health = MutableStateFlow<Map<TransportId, ConnectorHealth>>(emptyMap())
    override val health: StateFlow<Map<TransportId, ConnectorHealth>> = _health.asStateFlow()

    init {
        connectorRegistry.connectors.forEach { connector ->
            observeConnector(connector)
        }
    }

    private fun observeConnector(connector: TransportConnector) {
        val transport = connector.transport
        fun update(
            lifecycle: ConnectorLifecycleState = connector.lifecycle.value,
            status: ConnectorStatus = connector.status.value,
            capabilities: ConnectorCapabilities = connector.capabilities.value,
            messagingReady: Boolean = connector.messagingReady(),
        ) {
            _health.update { existing ->
                val copy = existing.toMutableMap()
                copy[transport] = ConnectorHealth(
                    transport = transport,
                    lifecycle = lifecycle,
                    status = status,
                    capabilities = capabilities,
                    messagingReady = messagingReady,
                )
                copy.toMap()
            }
        }

        update()
        scope.launch {
            connector.lifecycle.collect { state ->
                telemetrySink.emit(ConnectorTelemetryEvent.StateChanged(transport, state))
                update(lifecycle = state)
            }
        }
        scope.launch {
            connector.status.collect { status ->
                update(status = status)
            }
        }
        scope.launch {
            connector.capabilities.collect { capabilities ->
                telemetrySink.emit(
                    ConnectorTelemetryEvent.CapabilitiesPublished(transport, capabilities)
                )
                val ready = connector.messagingReady()
                update(capabilities = capabilities, messagingReady = ready)
                telemetrySink.emit(ConnectorTelemetryEvent.ReadinessChanged(transport, ready))
            }
        }
    }

    private fun TransportConnector.messagingReady(): Boolean {
        val messages = capabilities.value.entries["messages"] ?: return false
        return lifecycle.value == ConnectorLifecycleState.READY &&
            messages.properties["enabled"]?.toBooleanStrictOrNull() == true
    }
}
