Staged Plan

  - Stage 0 – Flag Infrastructure (status: Firestore only)
      - ~~Introduce a central feature toggle (e.g., BriarTransportMode) with enum values that model 
 the rollout path: FIRESTORE (legacy) and HYBRID (multi-connector). Back it with a Koin-provided 
FeatureFlags service that reads from persistent prefs plus a debug override (and later remote 
config if needed). Default to FIRESTORE, expose a developer option to flip modes at runtime.~~
  - Stage 1 – Briar Runtime Shell (flag: HYBRID path unused)
      - ~~Definition: a self-contained Gradle composite module (`briar-runtime`) that hosts the 
upstream Briar DI graph and exposes only Koin-registered bridge interfaces, letting us start/stop 
the Briar stack behind a feature flag without touching production app modules.~~
      - ~~Wire Briar modules into a new Gradle composite (settings.gradle) but keep them out of the 
main variant by default; this introduces a self-contained `briar-runtime` module without forcing a 
broader app modularisation yet.~~
      - ~~Implement a BriarComponentProvider that builds the Dagger component (mirroring 
BriarHeadlessApp) and exposes wrapped managers, yet never started when the flag is FIRESTORE.~~
      - ~~Add a BriarRuntimeManager façade registered in Koin that lazily instantiates the Dagger 
graph, exposes only bridge interfaces (e.g., BriarChatGateway, BriarContactService), and handles 
lifecycle start/stop when the flag changes.~~
      - ~~Provide a BriarComponentFactory binding so instrumentation and unit tests can supply fakes 
while production builds construct the real component.~~
      - ~~Add diagnostics (logging + background service stub) to confirm the component initialises 
and shuts down safely under instrumentation without touching existing repositories.~~
  - Stage 2 – Transport Router Foundation (flag: HYBRID opt-in)
      - ~~Stand up the `TransportRouter`, shared `TransportConnector` contract, and 
`ConnectorRegistry`; wrap the existing Firestore path behind a `FirestoreConnector` and register 
the Briar runtime through `TransportRuntimeBridge`.~~
      - ~~Introduce a Room-backed `ConversationStore` that persists canonical conversations/messages 
with transport metadata, including migration helpers so legacy Firestore cache can populate the new 
timeline.~~
      - ~~Seed a Briar-anchored `IdentityRegistry`, map existing participant IDs, and expose 
canonical conversation IDs to the router.~~
      - ~~Retrofit `ChatRepository`/`ChatViewModel` to call the router; FIRESTORE mode routes solely 
through the Firestore connector while HYBRID fans out through Briar-first delivery with optional 
mirrors.~~
      - ~~Stub `BridgeOrchestrator` hooks and cover Briar-first routing rules plus fallback behaviour 
with focused unit/integration tests.~~
  - Stage 3 – People, Presence & Connector Framework (flag: HYBRID default)
  - ~~Inventory every people/presence entry point still backed by Firestore and document 
required data shapes for the router + `IdentityRegistry`.~~
  - ~~Introduce a router-backed `PeopleRepository` that maps existing Firestore models to 
canonical identities, while delegating Firestore I/O to `FirestoreConnector`.~~
  - ~~Ensure connector sync layers (e.g., Firestore) publish the canonical current-user ID via the
 router-facing API so roster filtering never races stale registry state when the primary connector
 changes.~~
  - ~~Rewire presence observers and caches to subscribe through the router, add regression 
smoke tests, and keep a feature toggle to fall back to Firestore flows.~~
      - ~~Script data backfill routines so Firestore-derived presence and contacts data hydrate
`IdentityRegistry` on first launch.~~ (IdentityBackfillCoordinator/Worker + QA script landed 2025‑11‑08; worker now enqueues automatically when HYBRID/BRIAR_ONLY activates, even if the user signs in later.)
      - Formalise the connector onboarding lifecycle as a state machine (auth, handshake, 
capabilities, failure isolation) and record it in shared docs.
      - Update each connector implementation to emit lifecycle state updates, persist capability 
metadata, and expose failures for UI consumption.
      - ~~Surface connector status, capabilities, and failure states via ViewModels to the chat and 
settings UI layers with loading/error affordances.~~
      - Integrate the embedded Briar runtime’s chat/contact APIs so `BriarConnector` can resolve identities, create conversations, observe/send messages, and publish real capabilities (messages enabled, contacts with presence).
      - Implement an `AccountConnector` façade for Briar (or keep routing account requests to Firestore until Briar exposes that surface) so `RouterMyAccountRepository` works in HYBRID without crashes.
      - Update BridgeOrchestrator/TransportRouter tests to prove HYBRID mode routes through Briar when it’s ready and falls back gracefully otherwise.
      - Expand the existing `BriarConnector` façade (already backed by `BriarRuntimeBridge`) so it can resolve identities, manage conversations, stream contacts/messages, and publish real capability/presence data; once complete, register it as the preferred connector whenever HYBRID is active.
      - Land BridgeOrchestrator hooks that prioritise the Briar connector yet gracefully fall back 
to Firestore when peers lack Briar transport.
      - Refresh chat and settings surfaces to show unified conversations, transport badges, 
connector health indicators, identity merge affordances, and live Briar availability.
      - ~~Define the router telemetry event schema, craft a Flow exporter, and add logging hooks 
across connector lifecycle and UI entry points.~~ (`ConnectorTelemetry` + `ObservableConnectorTelemetrySink` shipped 2025‑11‑05.)
      - Use `agent-tools/run-and-log.sh` plus targeted click scripts to exercise connector 
enable/disable and transient failure scenarios, capturing QA logs and screenshots.
  - Stage 4 – Telegram Connector Bridge (flag: targeted rollout)
      - Graduate the Telegram auth flag, implement the `TelegramConnector`, and activate 
`BridgeOrchestrator` logic so Briar automatically relays messages when recipients lack Telegram.
      - Extend the `IdentityRegistry` with Telegram alias linking/merge workflows and supporting UI 
copy to resolve conflicts.
      - Validate bidirectional send/receive, bridging receipts, and error handling across 
Briar↔Telegram conversations with automated smoke scenarios and targeted unit tests.
      - Produce QA runbooks plus `agent-tools/run-and-log.sh` scripts for mixed-network chat 
verification and health capture.
  - Stage 5 – Supporting Services & Multi-Connector Hardening (flag: HYBRID default)
      - Migrate alarms, notifications, and other non-chat flows to consume router metadata, 
retiring remaining direct Firestore dependencies while keeping connectors operational.
      - Build health monitoring/logging and fallback policies per connector so outages degrade 
gracefully while Briar-first delivery remains unaffected.
      - Once feature parity is achieved, flip HYBRID (multi-connector) to the default mode in 
internal builds and keep an emergency fallback to Firestore.
      - Decision point: before executing this stage, agree on the long-term plan for the local Room 
conversation cache (e.g., keep + encrypt via SQLCipher, rely on Android FBE, or drop once Briar is 
canonical) since Stage 5 promotes Briar-first delivery; do not proceed without explicit direction.
  - Stage 6 – Multi-Connector Cleanup (flag: HYBRID shipped)
      - Remove Firebase/Firestore dependencies, google-services.json, and dead code paths once the 
router + connectors cover all flows.
      - Migrate data/credential storage to the canonical identity system, finalise documentation, 
and update licensing/attribution for GPL compliance.
