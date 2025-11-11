# Connector Lifecycle, Capabilities & Telemetry

_Last updated: 2025-11-08_

Stage 3 introduces a multi-connector runtime where Firestore and Briar (plus future transports) must
behave predictably. This document defines the lifecycle state machine, capability contract, and
telemetry events every connector must implement so the router, UI, and QA tooling can reason about
health, fallbacks, and feature flags.

## Goals
- Provide a consistent state model so BridgeOrchestrator can decide when a connector is usable or
  must fall back to another transport.
- Describe which capabilities a connector may expose (presence, roster, account, etc.) and how they
  are published to the router.
- Define telemetry events the app emits so QA scripts and analytics can verify HYBRID toggles,
  connector failures, and recovery paths.

## Lifecycle State Machine
```
   ┌────────┐        auth ok         ┌────────────┐        handshake ok        ┌─────────┐
   │ INITIAL├───────────────────────▶│AUTHENTICATING├──────────────────────────▶│HANDSHAKING│
   └───┬────┘                         └──────┬─────┘                             └────┬────┘
       │ auth fail / config error           │ token ready                           │ ready
       ▼                                    ▼                                      ▼
   ┌────────┐   recoverable error    ┌────────┐     capability mismatch      ┌─────────┐
   │ FAILED ◀────────────────────────┤ DEGRADED├────────────────────────────▶│  READY  │
   └───┬────┘                        └────────┘                               └────┬────┘
       │ unrecoverable                                                   heartbeat │
       ▼                                                                    timeout▼
   ┌────────┐                                                             ┌────────┐
   │ STOPPED│◀────────────────────────────────────────────────────────────┤ RETIRING│
   └────────┘   (app exit / feature flag off / manual stop)               └────────┘
```

| State            | Description                                                                                             | Allowed Transitions                    | Notes                                                                                                                                   |
|------------------|---------------------------------------------------------------------------------------------------------|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `INITIAL`        | Connector constructed, no network work yet.                                                             | `AUTHENTICATING`, `FAILED`, `STOPPED`  | Router should treat connector as unavailable.                                                                                           |
| `AUTHENTICATING` | Performing auth/login/token refresh.                                                                    | `HANDSHAKING`, `DEGRADED`, `FAILED`    | Emit a `ConnectorStateChanged` (from `AUTHENTICATING` to target state) and `ConnectorFailure` event whenever auth exceeds 30s or fails. |
| `HANDSHAKING`    | Negotiating capabilities with the remote backend **or** embedded runtime (e.g., Briar service binding). | `READY`, `DEGRADED`, `FAILED`          | Router will wait for handshake before routing traffic.                                                                                  |
| `READY`          | Connector can send/receive, capabilities current.                                                       | `DEGRADED`, `FAILED`, `RETIRING`       | BridgeOrchestrator may select as primary.                                                                                               |
| `DEGRADED`       | Partial functionality (e.g., roster ok, messages blocked).                                              | `HANDSHAKING`, `READY`, `FAILED`       | Capabilities map reflects what still works.                                                                                             |
| `FAILED`         | Unrecoverable error. Router must fall back.                                                             | `AUTHENTICATING` (if retry), `STOPPED` | Firestore connector should rarely land here; Briar may during runtime crashes.                                                          |
| `RETIRING`       | App is shutting down or feature flag disabled; connector finishing inflight work.                       | `STOPPED`                              | Router stops scheduling new work.                                                                                                       |
| `STOPPED`        | Connector fully stopped/removed.                                                                        | `AUTHENTICATING` (manual restart)      | Router removes it from primary/mirror selection.                                                                                        |

### Transition Semantics
- Each connector owns its retry policy. It must emit `ScheduledRetry` telemetry before attempting a
  retry so QA can correlate downtime.
- If a connector oscillates between `READY` and `DEGRADED` more than 3 times in 5 minutes, it must
  downgrade to `FAILED` and require manual intervention (prevents endless loops).
- BridgeOrchestrator should never route messages through a connector that is not `READY`. In
  HYBRID mode it should: prefer Briar `READY`; if Briar `DEGRADED`, keep Firestore as primary; if
  Briar `FAILED`, fall back to Firestore and log a `PrimaryFallback` telemetry event.

## Capability Contract
Each connector publishes a capability map when it enters `READY` (and whenever it changes). The map
is a JSON object keyed by capability name with version/metadata, e.g.:

```json
{
  "messages": { "version": 1, "supportsAttachments": false },
  "contacts": { "version": 1, "presence": "UNKNOWN" },
  "account": { "version": 1, "editableFields": ["name", "bio"] },
  "notifications": { "version": 1, "push": true }
}
```

### Required Capabilities
| Capability      | Description                                                                                  | Notes |
|-----------------|----------------------------------------------------------------------------------------------|-------|
| `messages`      | Ability to send/receive canonical messages. Must specify attachment support.                 |
| `contacts`      | Provides roster + presence data. Presence value should be `UNKNOWN`, `ONLINE`, or `OFFLINE`. |
| `account`       | Supports reading/updating profile fields. Include list of editable fields.                   |
| `notifications` | Indicates whether connector can deliver push notifications.                                  |

Connectors may add optional capabilities (`mediaSync`, `typingIndicators`, etc.), but they must
namescape them to avoid collisions (`briar.meshStatus`). Every capability change must trigger a new
telemetry event so the router/UI can react.

## Telemetry Events
Emit structured events via the shared `ConnectorTelemetrySink`. The default implementation
(`ObservableConnectorTelemetrySink`) logs to Timber under the `ConnectorTelemetry` tag and exposes a
`SharedFlow` that developer tooling, QA scripts, and debug UI can observe. Each entry should include
`transport`, `mode`, timestamps, and correlation IDs.

| Event                            | When                                                                      | Payload                                                |
|----------------------------------|---------------------------------------------------------------------------|--------------------------------------------------------|
| `ConnectorStateChanged`          | Any lifecycle transition.                                                 | `{ transport, fromState, toState, reason?, attempt? }` |
| `ConnectorCapabilitiesPublished` | Capabilities map changes (initial READY, updates).                        | `{ transport, capabilitiesJson, version }`             |
| `ConnectorFailure`               | Unrecoverable error before entering `FAILED`.                             | `{ transport, state, errorType, message, stack? }`     |
| `PrimaryFallback`                | BridgeOrchestrator switches primary connector due to failure/degradation. | `{ fromTransport, toTransport, reason }`               |
| `ScheduledRetry`                 | Connector schedules a retry after error.                                  | `{ transport, retryInMillis, attempt }`                |
| `ConnectorHeartbeat`             | Periodic READY heartbeat to confirm liveness (optional).                  | `{ transport, state, latencyMs }`                      |

Events should flow to:
1. Local logcat (filtered tag `ConnectorTelemetry`).
2. In-memory diagnostics stream so QA scripts can capture them via `run-and-log.sh`.
3. (Future) Remote telemetry exporter once backend is ready.

## Implementation Checklist
1. Add lifecycle enums + capability map fields to `TransportConnector`.
2. Update Firestore connector to publish static capabilities + READY/FAILED events (even if presence
   stays `UNKNOWN`).
3. When Briar connector lands, follow the same contract; BridgeOrchestrator consumes lifecycle +
   capability data to pick primaries. ✅ 2025-11-09: A stub `BriarConnector` now mirrors
   `BriarRuntimeStatus` into lifecycle updates while staying `DEGRADED` until messaging support
   lands, ensuring the router and orchestrator see Briar health even before it carries traffic.
4. Extend `docs/people_sync_roadmap.md` Stage 3 checklist to reference this document.
5. Add QA guidance referencing telemetry tags for HYBRID toggles. ✅ 2025-11-09: `FeatureFlagsActivity`
   now renders the live `PrimaryRoutingSnapshot` and the full connector health matrix (lifecycle +
   status + capabilities) so QA can confirm connector selection without digging through logcat.

## Open Questions
- Do we need per-capability readiness (e.g., `messages=READY`, `presence=DEGRADED`)? For now we rely
  on the capability map plus overall state; we can revisit if connectors need more granularity.
- Should telemetry events be persisted for offline upload? TBD once backend ingestion is available.

## Routing Diagnostics Surface

- `DefaultBridgeOrchestrator` now publishes a `PrimaryRoutingSnapshot` via `BridgeOrchestrator.routingState`.
- `FeatureFlagsActivity` subscribes to this flow and renders primary/preferred/fallback transports,
  lifecycle state, trigger, and timestamp so QA can validate HYBRID toggles on-device. The same
  screen now renders the `ConnectorHealthRepository` output so engineers can see each connector’s
  lifecycle/status/capabilities in real time.
- `agent-tools/run-and-log.sh` filters `BridgeOrchestrator` logcat entries, making it easy to collect
  the same signal during scripted runs.
