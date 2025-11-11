# Transport Router Design Spike

**Author:** Codex (pairing with project owner)  
**Date:** 2025-02-14  
**Status:** Draft – ready for review

## Background

- Stage 0–1 delivered feature-flag plumbing (`BriarTransportMode`) and a `TransportRuntimeBridge` that can start/stop the embedded Briar runtime while legacy Firestore chat stays in place.
- The long-term product goal is a Beeper-style client: one shared UI spanning multiple networks (Firestore, Briar, Telegram, future connectors). Briar is the canonical transport that guarantees reachability between any pair of users, even when only one participant has a secondary connector.
- The current `ChatRepository` is Firestore-specific and guarded by `requireFirestore()`. There is no abstraction for multi-transport message routing, identity canonicalisation, or connector lifecycle.

## Goals

1. Define the architecture for a Briar-first transport router that can host multiple connectors without fragmenting the UI.
2. Describe how message routing, identity management, and storage change when Telegram (and future networks) join the stack.
3. Outline implementation phases aligned with the updated staged rollout plan (Stages 2–6).

## Non-goals

- Detailed Briar protocol internals or Telegram API semantics.
- UI pixel specs beyond the requirement for a unified conversation timeline.
- Final data schema/DAO definitions (will be produced during implementation).

## Constraints & Assumptions

- Briar remains the default transport; Firestore is legacy and will sunset once parity is reached.
- Connectors must not block Briar delivery. If a secondary connector fails, Briar delivery continues (best-effort fan-out only).
- Feature-flag infrastructure stays in place; new capabilities must be guarded so QA can exercise them incrementally.
- `agent-tools/` scripts remain the vehicle for automated smoke tests and scenario capture.

## Key Requirements

- **R1 – Unified conversations:** UI observes a single timeline per conversation, regardless of which transport originated a message. Metadata indicates the transport path.
- **R2 – Canonical identities:** Every participant has a stable identity anchored to Briar; connector-specific identifiers map onto that canonical profile.
- **R3 – Briar-first routing:** When both peers support a connector, we may still use the secondary transport, but the canonical message copy always persists through Briar to guarantee availability.
- **R4 – Connector isolation:** Failures in one connector are contained; observability surfaces health so the app can degrade gracefully.
- **R5 – Extensibility:** Adding a new connector should not change client UI code; we extend the router and register a new plugin.

## Design Axes & Options

### Identity Canonicalisation

| Option | Description | Pros | Cons |
| --- | --- | --- | --- |
| **A. Briar-anchored identity registry** | Treat Briar contact IDs as the canonical user key; connectors attach aliases. | Matches Briar-as-default vision, simplifies cross-network fan-out. | Requires Briar bootstrap before any connector activity; needs mapping UI for first-time merges. |
| **B. Connector-neutral UUID** | Generate independent UUIDs and map every transport (including Briar) onto it. | Decouples from Briar lifecycle, easier offline provisioning. | Adds extra mapping layer for Briar; weakens “Briar is default” story. |
| **C. Per-connector identities** | Keep separate IDs, resolve on demand. | Lowest initial effort. | Hard to guarantee cross-network routing/UI unified list; duplicates contacts. |

**Decision:** Adopt option A. All connectors resolve through the Briar identity registry. When Briar is unavailable we block connector login to avoid dangling identities.

### Conversation Storage Model

| Option | Description | Pros | Cons |
| --- | --- | --- | --- |
| **A. Canonical conversation table** | One timeline table keyed by canonical participant set. Each message stores `transport`, `originalTransportId`, and `canonicalMessageId`. | Simplifies UI, dedupes history when toggling flags. | Requires migration from current Firestore schema and data reconciliation. |
| **B. Per-connector timelines** | Separate tables per transport; UI aggregates on the fly. | Less migration upfront. | Complex fan-out, risks duplicates/race conditions; poor offline story. |

**Decision:** Option A. Build a new persistence layer (likely Room or DataStore-backed) that the router owns. Legacy Firestore cache can migrate into this model.

### Routing Policy

| Option | Description | Pros | Cons |
| --- | --- | --- | --- |
| **A. Briar-first with optional mirror** | Always deliver via Briar; additional connectors mirror when both peers support them. | Guarantees delivery even if connector collapses; consistent history. | Doubles bandwidth for mirrored transports, but acceptable for reliability. |
| **B. Smart preference** | Choose connector based on quality/cost heuristics; Briar only on fallback. | Potentially better latency. | Violates requirement that Briar stays canonical; more complex heuristics. |

**Decision:** Option A. Mirror to Telegram when both parties have it, but treat Briar as the source of truth and the route for bridging.

### Connector Contract Shape

| Option | Description | Pros | Cons |
| --- | --- | --- | --- |
| **A. Push-based connectors** | Connectors emit events into router via Flow and accept send requests. | Plays nicely with coroutines, matches existing Flow usage. | Requires buffering/back-pressure handling. |
| **B. Polling adapters** | Router polls connectors for updates. | Simpler connectors. | Worse latency, more battery usage. |

**Decision:** Option A. Define a coroutine-based `TransportConnector` interface with Flow streams and suspend send APIs.

## Recommended Architecture

### Component Overview

- `TransportRouter`: High-level entry point injected into chat/people ViewModels. Owns routing rules, persistence, and fan-out.
- `TransportConnector` interface:
  ```kotlin
  interface TransportConnector {
      val transport: TransportId
      val status: Flow<ConnectorStatus>
      fun observeMessages(conversationId: CanonicalConversationId): Flow<ConnectorInboundMessage>
      suspend fun send(request: ConnectorOutboundMessage)
      suspend fun syncContacts(): List<ConnectorContact>
  }
  ```
  Each connector (Firestore, Briar, Telegram, future) implements this contract.
- `ConnectorRegistry`: Keeps active connectors, handles auth bootstrapping, and reports health metrics to `TransportRuntimeBridge`.
- `IdentityRegistry`: Anchors canonical Briar IDs and maintains mappings (e.g., Telegram handle → Briar contact). Exposes merge operations for the UI.
- `ConversationStore`: Room-backed persistence for canonical conversations and messages with transport metadata (source, timestamps, delivery status).
- `BridgeOrchestrator`: Watches connectors for inbound messages; relays them through Briar when the recipient lacks the originating transport, ensuring cross-network availability.

### Data Flow – Sending a Message

1. UI calls `TransportRouter.sendMessage(canonicalConversationId, messageBody, sendOptions)`.
2. Router looks up participants and determines active transports from `ConnectorRegistry`.
3. Router persists a canonical message record with `transport=Briar` and kicks off:
   - `BriarConnector.send()` (mandatory).
   - For each optional transport (e.g., Telegram) where both participants are active, router dispatches `send()` in parallel and updates per-transport delivery status.
4. `ConversationStore` emits the new message via Flows consumed by the UI. Delivery receipts from connectors update status fields.

### Data Flow – Receiving a Message (Telegram-only Sender)

1. Telegram connector emits `ConnectorInboundMessage` via its Flow.
2. Router resolves sender/recipient via `IdentityRegistry`. If the recipient lacks Telegram, router:
   - Persists the message with `transport=Telegram`.
   - Enqueues a bridge task: `BriarConnector.send()` to deliver the message payload to the recipient through Briar.
   - Marks the canonical message as “bridged via Briar”.
3. UI displays the message with a transport badge; metadata indicates whether it arrived via bridging.

### Integration with Existing Flags

- `BriarTransportMode` continues to gate runtime start/stop. Once Stage 5 flips HYBRID default, router becomes the primary chat entry point even in FIRESTORE mode (it will internally no-op Briar when flag disabled).
- Telegram enablement flag graduates in Stage 4; router checks both Briar mode and connector feature flag before activating the connector.

## Stage Alignment

- **Stage 2 – Transport Router Foundation**
  - Build `TransportRouter`, `ConversationStore`, `ConnectorRegistry`, and migrate Firestore path into a `FirestoreConnector`.
  - Retrofit `ChatViewModel` to use the router while continuing to mirror legacy behaviour in FIRESTORE mode.
  - Seed identity registry from existing user profiles (current account + chat partner IDs).
- **Stage 3 – People, Presence & Connector Framework**
  - Move presence/contact flows onto the router + identity registry.
  - Expose connector capability state to UI (badges, settings).
  - Harden connector lifecycle (start/stop, error surfaces).
- **Stage 4 – Telegram Connector Bridge**
  - Implement `TelegramConnector` and bridge logic that guarantees Briar delivery when peers lack Telegram.
  - Extend identity registry with Telegram alias linking workflows.
  - Add agent-tools scripts for mixed-network chat verification, capturing logs and screenshots.
- **Stage 5 – Supporting Services & Hardening**
  - Migrate alarms/notifications to use router metadata.
  - Add health monitors and fallback policies per connector.
  - Flip default mode to HYBRID; ensure router handles Firestore sunset path.
- **Stage 6 – Cleanup**
  - Remove Firestore repository remnants once router + Briar cover all flows.

## Testing & Observability

- Unit-test router routing rules, identity mapping, and bridging edge cases using fake connectors.
- Integration tests via `agent-tools/run-and-log.sh`:
  - Baseline Briar ↔ Briar conversation.
  - Briar ↔ Telegram (bridge required).
  - Mixed connector failure scenarios (Telegram down).
- Add structured logs/metrics in `TransportRuntimeBridge` for connector health and bridging actions (e.g., “Bridge success/failure” events).

## Risks & Open Questions

- **Identity merge UX:** Need flows for users to confirm Telegram ↔ Briar mappings to avoid impersonation.
- **Briar availability:** If Briar runtime cannot start (device restrictions), app must surface a blocking state because canonical routing depends on Briar identity.
- **Message ordering:** Bridging introduces duplicated timestamps; we must normalise ordering using canonical message IDs plus server timestamps where available.
- **Encryption boundaries:** Decide whether Briar-bridged messages retain encryption when mirrored to Telegram (may require plaintext transformation).
- **Resource usage:** Running multiple connectors simultaneously impacts battery/network; telemetry needed to confirm acceptable cost.

## Next Steps

1. Review this spike and capture feedback/changes directly in `BriarStagedPlan.md` if priorities shift.
2. Prototype the `TransportConnector` interface with no-op implementations to validate coroutine flows and back-pressure.
3. Draft identity registry data model (Room entities, mapping tables) and evaluate migration path from current SharedPreferences cache.
4. Define logging schema for connector health events so `agent-tools/run-and-log.sh` can filter them automatically.
