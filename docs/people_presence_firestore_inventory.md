# People & Presence Firestore Inventory

 _Last updated: 2026-02-16_

Stage 3 of `BriarStagedPlan.md` requires an inventory of every people/presence entry point that
still relies on Firestore and documentation of the data shape that the transport router and
`IdentityRegistry` expect. This file captures the current state so later stages can retire or adapt
these dependencies.

## Firestore Collections

### `users` (top-level collection)
- **Document ID**: treated as the canonical person identifier while Briar identities are introduced.
- **Fields**
  - `name : String` – display name surfaced in `PersonSummary` and chat headers.
  - `bio : String` – copied into `IdentityProfile.bio`.
  - `profilePicturePath : String?` – optional path for avatar assets.
  - `presence : String?` – optional transport status (`ONLINE`/`OFFLINE`/`UNKNOWN`); missing or invalid values fall back to `UNKNOWN`.
  - `registrationTokens : Array<String>` – FCM tokens; not yet consumed by router flows but must be preserved.
- **Default skeleton**: see `app/src/main/java/com/example/rise/models/User.kt`.
- **Presence**: `FirestoreConnector.observeContacts()` reads `users.presence` when present and applies `PresenceStatus.UNKNOWN` as the safety fallback for missing/invalid values.

### `users/{uid}/engagedChatChannels` (subcollection)
- **Fields**
  - `channelId : String` – Firestore conversation/channel alias.
- **Usage**: connector implementations (`FirebaseChatRemoteDataSource` and `FirestoreConnector`) resolve channel aliases for roster contacts.

## Code Entry Points Still Using Firestore

The default is “call through the transport stack.” Anything that must stay Firestore-only needs an explicit exception note with status + date so later stages can revisit it.

| Area | Class & File | Purpose | Routing Decision (status/date) | Exception Notes |
| --- | --- | --- | --- | --- |
| People sync | `FirestorePeopleSync` (`app/src/main/java/com/example/rise/data/people/RouterPeopleRepository.kt`) | Registers a snapshot listener on `users`, emits canonical IDs and hydrates the `IdentityRegistry`. | Router-backed ✅ (2025-11-08) | Now consumes `TransportConnector.observeContacts()`; no direct Firestore callers remain. |
| Legacy UI helper | `FirestoreUtil` (`app/src/main/java/com/example/rise/util/FirestoreUtil.kt`) | Misc helpers used by auth/FCM flows. | Router-backed ✅ (2025-11-08) | File deleted. User bootstrap + token storage now happen via `FirebaseSignInRepository`/`UserRemoteDataSource`, so no UI layers call Firestore SDK directly. |
| My account profile | `FirebaseUserRemoteDataSource` (`app/src/main/java/com/example/rise/data/firestore/FirebaseUserRemoteDataSource.kt`) | Fetches/updates the signed-in user profile for account settings. | Router-backed ✅ (2026-02-16) | `MyAccountViewModel` now depends on `MyAccountRepository`, and `RouterMyAccountRepository` routes name/bio/`profilePicturePath` writes through `AccountConnector` or Briar identity storage. |
| Chat connector bridge | `FirebaseChatRemoteDataSource` (`app/src/main/java/com/example/rise/data/firestore/FirebaseChatRemoteDataSource.kt`) | Resolves display names and channel aliases for Firestore chats; used by `FirestoreConnector`. | Router-scoped ✅ (2025-11-08) | Only `FirestoreConnector` may call this; other modules must access roster data via connectors, not Firestore SDK. |

## Presence Support Status (QA)

| Transport | Presence feed status/date | Current behavior | Follow-up TODO |
| --- | --- | --- | --- |
| Firestore | Partial ✅ (2026-02-16) | `FirestoreConnector.observeContacts()` maps `users.presence` values (`ONLINE`/`OFFLINE`/`UNKNOWN`) and falls back to `UNKNOWN`. Capability metadata advertises `presenceField=users.presence` + `presenceFallback=UNKNOWN`. | TODO(`ROUTER-PRESENCE-SCHEMA`): make Firestore presence schema mandatory (or remove Firestore presence entirely once Briar is authoritative). |
| Briar | Baseline ✅ (2026-02-16) | `ConnectorContact.presence` is carried through Briar adapters; default remains `UNKNOWN` when runtime status is unavailable. | TODO(`ROUTER-BRIAR-PRESENCE-LIVE`): wire continuous Briar runtime presence updates for contact cards/chat headers. |

## Router & Identity Registry Requirements
- Canonical identifiers pulled from Firestore must map to `IdentityRegistry` entries with `aliases[TransportId.FIRESTORE] = documentId`.
- `IdentityProfile` currently derives `bio` and `profilePicturePath` from Firestore. Presence comes from connector payloads; Firestore still uses `UNKNOWN` as the fallback when `users.presence` is absent/invalid.
- Any future presence integration should either:
  1. Add a Firestore-compatible field and update both `FirestorePeopleSync` and `IdentityRegistryStore`, or
  2. Route presence exclusively through Briar connectors and strip Firestore presence writes to avoid conflicts.

## Open Migration Tasks
1. **Roster flow alignment**
- ✅ `FirestorePeopleSync` now consumes the `TransportConnector.observeContacts()` feed exposed by `FirestoreConnector`, eliminating its direct Firestore dependency.
- ✅ User bootstrap/token flows now route through `FirebaseSignInRepository` + `UserRemoteDataSource`, and `AlarmReceiver` dispatches via `ChatRepository` instead of writing directly to Firestore.
- ✅ `agent-tools/backfill-identities.sh` can be run after flipping HYBRID on to seed the router’s `IdentityRegistry` with all Firestore contacts (the script installs the latest debug build and broadcasts `com.example.rise.debug.RUN_IDENTITY_BACKFILL`, which enqueues the `IdentityBackfillWorker`). The worker is now enqueued whenever HYBRID/BRIAR_ONLY becomes active—even if no user is signed in yet—so QA can flip the flag before login and the backfill will run automatically once authentication completes.
2. Define how `registrationTokens` migrate into router-friendly telemetry or remain Firestore-only.
3. Decide whether `engagedChatChannels` remains a Firestore concern post Stage 5 or migrates into the Room-backed `ConversationStore`.

## Presence Migration Notes

- `ConnectorContact` now carries a `presence` field; all transports default to `PresenceStatus.UNKNOWN`. Once a connector can surface richer contact status, it should populate this field so the router propagates presence through `IdentityRegistry`.
- The next implementation steps are:
  1. Extend the relevant connector (Firestore initially, Briar later) to emit presence updates alongside roster snapshots. For Firestore, this implies adding a presence field to the `User` document or a dedicated subcollection.
  2. ✅ `processSnapshot()` in `RouterPeopleRepository` now persists connector-provided presence (2025-11-08).
  3. ✅ Identity registry persistence + RouterPeopleRepository tests cover presence serialization and summaries (2025-11-08).
  4. ✅ People list + chat screens consume presence from `PersonSummary` (2025-11-08).
- Until connectors emit real presence, callers must continue to treat presence as unknown; no UI changes should rely on Firestore-only presence fields to avoid diverging from the router model.
