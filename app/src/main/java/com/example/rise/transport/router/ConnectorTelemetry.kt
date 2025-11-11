package com.example.rise.transport.router

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * Collection of structured telemetry events emitted whenever connector state changes. These events
 * back the documentation in `docs/connector_lifecycle.md` and give QA a single stream to inspect.
 */
sealed interface ConnectorTelemetryEvent {

    data class StateChanged(
        val transport: TransportId,
        val state: ConnectorLifecycleState,
    ) : ConnectorTelemetryEvent

    data class CapabilitiesPublished(
        val transport: TransportId,
        val capabilities: ConnectorCapabilities,
    ) : ConnectorTelemetryEvent

    data class Failure(
        val transport: TransportId,
        val state: ConnectorLifecycleState,
        val error: Throwable?,
    ) : ConnectorTelemetryEvent

    data class PrimaryFallback(
        val fromTransport: TransportId,
        val toTransport: TransportId,
        val reason: ConnectorLifecycleState,
    ) : ConnectorTelemetryEvent
}

fun interface ConnectorTelemetrySink {
    fun emit(event: ConnectorTelemetryEvent)
}

/**
 * Default sink that logs every event via Timber and exposes a hot [SharedFlow] so debug UI and QA
 * tooling can subscribe to the same stream.
 */
class ObservableConnectorTelemetrySink(
    private val tag: String = "ConnectorTelemetry",
) : ConnectorTelemetrySink {

    private val _events = MutableSharedFlow<ConnectorTelemetryEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ConnectorTelemetryEvent> = _events.asSharedFlow()

    override fun emit(event: ConnectorTelemetryEvent) {
        log(event)
        _events.tryEmit(event)
    }

    private fun log(event: ConnectorTelemetryEvent) {
        val message = when (event) {
            is ConnectorTelemetryEvent.StateChanged ->
                "state=${event.state} transport=${event.transport}"
            is ConnectorTelemetryEvent.CapabilitiesPublished ->
                "capabilities=${event.capabilities.entries.keys} transport=${event.transport}"
            is ConnectorTelemetryEvent.Failure ->
                "failure state=${event.state} transport=${event.transport} error=${event.error?.message}"
            is ConnectorTelemetryEvent.PrimaryFallback ->
                "fallback from=${event.fromTransport} to=${event.toTransport} reason=${event.reason}"
        }
        Timber.tag(tag).i(message)
    }
}
