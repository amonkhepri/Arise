# People Sync Reliability Roadmap

## Context
- Current Firestore roster listener now propagates failures and retries with exponential backoff.
- All clients share identical retry timing; with no live users this is acceptable short term.

## Backlog Item: Add Retry Jitter
- **Risk Mitigated:** prevents a thundering herd when many devices reconnect after outages.
- **Approach:** add a small random +/-10% jitter to each scheduled retry delay before invoking `delayProvider`.
- **Validation:** unit test should verify jitter bounds and ensure listener still re-registers.
- **Status:** not yet implemented; revisit before external user rollout.

## Follow-up: Error Surfacing Without Flow Cancellation
- `PeopleRepository` now exposes an `errors` flow so UIs (e.g., `PeopleViewModel`) can surface sync failures while staying subscribed to roster updates.
- Router-backed repositories must keep emitting people data after errors; unit coverage ensures the list flow stays hot even when the sync retries.
- Sync implementations should emit errors only when user action is required (e.g., auth failure). Transient connectivity issues should continue to flow through the retry path without forcing UI re-subscription.

## Stage 3 Router Work

### Account façade scope
- Introduce an `AccountRepository` that wraps the router/connector stack for profile reads and writes (name, bio, `profilePicturePath`).
- `FirebaseUserRemoteDataSource` becomes an implementation detail of `FirestoreConnector`; UI/ViewModels only depend on the façade so Stage 5 can swap transports without touching UI code.
- Update `MyAccountViewModel` + tests to rely on the façade; document the change in release notes and delete any direct Firestore SDK calls from the account feature.
- ✅ 2025-11-08: `RouterMyAccountRepository` now calls through the connector registry, and `FirestoreConnector` implements `AccountConnector` so profile fetch/update logic stays transport-scoped.

### Presence + Identity Registry
- Extend connector contact payloads to carry presence (defaulting to `PresenceStatus.UNKNOWN`). Firestore schema updates should note the temporary fallback and add TODOs pointing to router tickets.
- `IdentityRegistryStore` and `RouterPeopleRepositoryImpl.toPersonSummary()` must persist and expose connector-provided presence; add tests for both Firestore and Briar connectors.
- Track these steps in `docs/people_presence_firestore_inventory.md` with a status + date so QA knows which transports honor presence.
- ✅ 2025-11-08: `FirestorePeopleSync` now propagates `ConnectorContact.presence` into `IdentityRegistry`, and regression tests cover both snapshot processing and repository observers. Firestore still emits `UNKNOWN` until the schema adds a presence field.
- ✅ 2025-11-08: People cards and chat headers now surface the connector-provided presence so QA can see the signal end to end.
- ✅ 2025-11-08: `docs/connector_lifecycle.md` defines the connector lifecycle, capability contract, and telemetry events that Stage 3 must honor before Briar goes live.

### HYBRID flag verification
- The transport mode flag lives at `BriarTransportMode.HYBRID`; feature code should gate router-backed people/account flows behind this flag until QA signs off.
- Verification checklist: (1) launch with HYBRID off → legacy Firestore flows stay active, (2) toggle HYBRID on via `FeatureFlagsActivity` → router-backed people sync + account façade execute, (3) logcat confirms connector selection switches when the flag flips.
- Document any deviations or temp hacks in this roadmap so later stages know how to flip the flag during rollout.
- ✅ 2025-11-08: `DefaultBridgeOrchestrator` now tracks connector lifecycle and publishes `routingState`, so HYBRID immediately prefers Briar when it reaches `READY`, logs a single fallback when it doesn’t, and gives QA a visible hook for telemetry.
- ✅ 2025-11-09: `FeatureFlagsActivity` renders the live routing snapshot (primary/preferred/fallback +
  lifecycle) and `agent-tools/run-and-log.sh` filters the `BridgeOrchestrator` tag, so QA no longer
  needs to sift through full logcat to verify HYBRID toggles.
