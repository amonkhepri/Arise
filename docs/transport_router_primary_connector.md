# Primary Connector Concept

## Summary

The transport router hosts multiple **connectors** (Firestore, Briar, Telegram, …). At any given
moment we designate one of them as the **primary connector**. The primary connector is the transport
the router trusts for canonical operations:

- Resolving the current signed-in identity ("who am I?")
- Creating canonical conversations
- Persisting/send the canonical copy of each message
- Deciding how other connectors (mirrors) should fan out

Other connectors can still operate concurrently (mirroring or bridging), but they never replace the
primary role without an explicit mode change.

## Why we need a primary connector

1. **Canonical identity** – We need a single source of truth for the current user. The primary
   connector’s `currentIdentity()` result feeds the `IdentityRegistry` so the app knows which
   canonical ID belongs to "me".
2. **Conversation consistency** – We use the primary to create/ensure conversation IDs. Every other
   connector stores aliases that map back to the canonical conversation.
3. **Deterministic fallbacks** – If a secondary connector fails, Briar (as the primary in HYBRID
   mode) still guarantees delivery. The router never risks sending only through a secondary path.
4. **Observability & policy** – Health checks, retry policies, and telemetry all anchor on the
   primary connector’s status.

## How the primary is chosen

The `ConnectorRegistry` inspects the current feature flag (`BriarTransportMode`):

- `FIRESTORE` mode → the Firestore connector is primary.
- `HYBRID` mode → the Briar connector is primary (Firestore and other connectors act as mirrors).

Future modes can extend this logic if we introduce additional transports.

## Primary vs. mirrors

- **Primary connector** – Handles canonical duties (identity, conversation creation, canonical
  send). The router *must* succeed through this connector to consider the operation successful.
- **Mirror connectors** – Optional fan-out paths. The router sends to them after the primary send
  succeeds. Failures here log telemetry but do not block the canonical flow.

## Where the primary is used in code

- `TransportRouterImpl.resolveCurrentIdentity()` asks the primary connector for the identity, then
  updates the cache.
- `TransportRouterImpl.ensureConversation()` calls the primary to ensure the conversation exists and
  records aliases for mirrors.
- `TransportRouterImpl.send()` dispatches through the primary first, then mirrors.

Keeping this separation explicit lets us add new connectors without rewriting the UI or risking
transport-specific divergence in message history.

## Observability

- `DefaultBridgeOrchestrator` exposes the current routing decision via
  `BridgeOrchestrator.routingState` (`PrimaryRoutingSnapshot`). The snapshot includes the selected
  primary transport, the preferred transport for the active mode, optional fallback target, lifecycle
  reason, trigger, and timestamp.
- `FeatureFlagsActivity` renders the snapshot so QA can flip HYBRID mode and immediately confirm which
  connector is primary. This replaces manual log inspection for the staged rollout.
- `agent-tools/run-and-log.sh` now keeps the `BridgeOrchestrator` log tag in the default filter so
  scripted smoke tests capture routing changes automatically.
