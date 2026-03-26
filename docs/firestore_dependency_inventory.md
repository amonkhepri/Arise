# Firestore/Firebase Dependency Inventory (First Pass)

_Last updated: 2026-03-03_

This inventory is the migration-oriented snapshot of production Firebase/Firestore usage in Arise.
It exists to shift work selection toward removing or isolating legacy Firebase dependencies instead
of continuing incremental Firestore retry-string maintenance by default.

This file complements `docs/people_presence_firestore_inventory.md` (people/presence schema and
router data-shape details). This inventory focuses on dependency surfaces and migration priority.

## Tags

- `migrate-now`: top-priority removal/isolation work for core flows or UI/domain leakage
- `temporary-legacy`: allowed to remain for now behind a boundary while connector migration proceeds
- `delete-after-cutover`: legacy implementation/assets that should be removed after connector cutover

## Scope

- Included: production code paths and build/runtime wiring
- Included domains (first-pass focus): people sync, account/profile, auth, presence, chat
- Also included: notable secondary Firebase surfaces (alarm/dashboard, FCM, build plugins/libs)
- Excluded: test-only dependencies (tracked separately in test code and can be reduced after runtime migration)

## Summary (What Still Keeps Firebase/Firestore in Core Flows)

- App startup DI still creates and injects Firebase singletons (`FirebaseAuth`, `FirebaseFirestore`, `FirebaseMessaging`).
- Auth/sign-in flows still depend on Firebase auth APIs and Firebase-issued custom tokens.
- Firestore remains the legacy transport connector implementation for contacts/messages/account.
- People sync still contains a Firestore-specific sync implementation and Firestore exception classification logic.
- Some UI code still references Firebase types directly (Firestore query/adapters and `FirebaseAuth` usage in legacy alarm adapters).
- Dashboard/alarm flows remain Firestore-native and leak Firestore query types into repository contracts.

## Inventory (Core + Secondary Production Surfaces)

| Domain | Component / File | Current Firebase / Firestore Surface | Tag | Migration Direction / Exit Condition |
| --- | --- | --- | --- | --- |
| Build/runtime wiring | `build.gradle`, `app/build.gradle` | Google services plugin + Firebase/Auth/Firestore/Messaging/Storage/FirebaseUI dependencies are still declared in app build. | `temporary-legacy` | Keep while core flow cutover is in progress; remove Firestore/FirebaseUI deps first as features migrate. |
| App composition root | `app/src/main/java/com/example/rise/App.kt` | Creates `FirebaseAuth`, `FirebaseFirestore`, `FirebaseMessaging` singletons and binds Firebase-backed auth/user/chat/alarm implementations by default. | `migrate-now` | Introduce connector-first/default bindings and isolate Firebase bindings to legacy modules/profile flags only. |
| Transport gating policy | `app/src/main/java/com/example/rise/transport/TransportRuntimeBridge.kt`, `TransportRuntimeBridgeImpl.kt` | `requireFirestore(caller)` is a first-class control path guard for legacy flows. | `migrate-now` | Replace Firestore-required guards with capability checks / connector availability checks for migrated features. |
| Connector selection policy | `app/src/main/java/com/example/rise/transport/router/DefaultConnectorRegistry.kt` | Stage comment + registry behavior still model Firestore as a primary Stage 2 transport path. | `migrate-now` | Update migration-stage assumptions and make Firestore optional/degraded-only after cutover criteria are met. |
| People sync (legacy path in router repo file) | `app/src/main/java/com/example/rise/data/people/RouterPeopleRepositoryImpl.kt` (`FirestorePeopleSync`) | Uses `FirebaseAuth` listener and Firestore-specific exception typing (`FirebaseFirestoreException`) in sync classification/retry behavior. | `migrate-now` | Reduce/retire `FirestorePeopleSync` as a core dependency; keep only bounded legacy adapter behavior if FIRESTORE mode remains. |
| People sync (connector path) | `app/src/main/java/com/example/rise/transport/connectors/FirestoreConnector.kt` | Firestore-backed connector still serves contacts/messages/account and advertises presence from `users.presence`. | `temporary-legacy` | Keep as the legacy transport adapter behind `TransportConnector` until connector-only cutover; do not expand scope. |
| Presence inventory/docs | `docs/people_presence_firestore_inventory.md` | Tracks Firestore `users.presence` fallback semantics and router expectations. | `temporary-legacy` | Keep updated until Firestore presence is removed or made non-core. |
| Account/profile routing fallback | `app/src/main/java/com/example/rise/data/myaccount/RouterMyAccountRepository.kt` | `accountConnector()` now prefers non-Firestore account connectors in `HYBRID`/`BRIAR_ONLY` and uses Firestore only as a legacy fallback when no connector-native account capability is available. | `migrate-now` | Remove the remaining Firestore fallback once cutover criteria guarantee connector-native account/profile support in non-FIRESTORE modes. |
| User profile remote adapter | `app/src/main/java/com/example/rise/data/firestore/FirebaseUserRemoteDataSource.kt` | Direct Firestore `users` document CRUD + snapshot observation. | `temporary-legacy` | Keep only as legacy adapter behind `UserRemoteDataSource`; remove direct app-core reliance over time. |
| User profile abstraction (Firestore-shaped semantics in docs) | `app/src/main/java/com/example/rise/data/firestore/UserRemoteDataSource.kt` | Interface docs explicitly describe Firestore-backed production usage and Firestore-like update semantics. | `temporary-legacy` | Reword contract around backend-agnostic semantics after Firebase path is no longer primary. |
| Auth service implementation | `app/src/main/java/com/example/rise/auth/FirebaseAuthenticationService.kt` | Direct FirebaseAuth sign-in/register/custom-token/auth-state/profile APIs implement `AuthenticationService`. | `migrate-now` | Add connector-driven auth implementation(s) and move Firebase auth to legacy adapter path only. |
| Auth state provider | `app/src/main/java/com/example/rise/data/auth/FirebaseAuthStateProvider.kt` | App-level auth state abstraction is backed directly by `FirebaseAuth`. | `migrate-now` | Replace with connector/runtime-auth state provider or composite provider that does not require Firebase in core flows. |
| Sign-in bootstrap + token persistence | `app/src/main/java/com/example/rise/data/auth/FirebaseSignInRepository.kt` | Uses `FirebaseMessaging`, Firestore-gated bootstrap (`requireFirestore`), and writes `registrationTokens` to user docs via `UserRemoteDataSource`. | `migrate-now` | Split connector account bootstrap from FCM token storage and make Firestore token persistence non-blocking/legacy-only. |
| Sign-in UI logic | `app/src/main/java/com/example/rise/ui/signInActivity/SignInViewModel.kt` | Handles `FirebaseAuthException` subclasses and signs in with Firebase custom tokens in Telegram flow. | `migrate-now` | Map auth failures through domain errors; move Firebase-specific exception handling behind auth adapter. |
| FCM service entrypoint | `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/rise/services/MyFirebaseMessagingService.kt` | Manifest registers Firebase messaging service; service stores FCM tokens via sign-in repository. | `temporary-legacy` | Keep while push/token strategy remains FCM-backed; reassess after auth/account/chat cutover. |
| Chat remote adapter | `app/src/main/java/com/example/rise/data/firestore/FirebaseChatRemoteDataSource.kt` | Direct Firestore channel/message/user-name reads/writes/listeners. | `temporary-legacy` | Keep behind `ChatRemoteDataSource` only; remove once connector/chat stack no longer needs Firestore transport. |
| Chat remote abstraction (Firestore semantics in docs) | `app/src/main/java/com/example/rise/data/firestore/ChatRemoteDataSource.kt` | Interface comments and method semantics are Firestore-collection/channel specific. | `temporary-legacy` | Generalize contract wording once alternative connector chat backing is primary. |
| Chat UI auth leakage | `app/src/main/java/com/example/rise/ui/dashboardNavigation/people/chatActivity/ChatActivity.kt` | ✅ Removed direct `FirebaseAuth` injection on 2026-03-03; chat rendering + scheduled-message identity now resolve through `AuthenticationService`. | `temporary-legacy` | Keep chat identity sourcing behind auth abstractions and avoid reintroducing Firebase auth SDK references in activity/UI code. |
| Message rendering sender ownership | `app/src/main/java/com/example/rise/item/MessageItem.kt`, `app/src/main/java/com/example/rise/item/TextMessageItem.kt` | ✅ Removed direct `FirebaseAuth.getInstance()` lookup on 2026-03-03; sender-vs-self rendering now uses injected current-user identity from UI state. | `temporary-legacy` | Keep renderer identity sourcing behind UI/viewmodel/auth abstractions and avoid reintroducing static Firebase auth lookups in item classes. |
| Alarm repository (secondary but direct) | `app/src/main/java/com/example/rise/data/dashboard/FirestoreAlarmRepository.kt` | Firestore-native alarm storage/query implementation and Firestore gating via `requireFirestore`. | `temporary-legacy` | Keep out of first migration wave if needed, but plan connector/local-store-backed alarm repository to eliminate Firestore query coupling. |
| Alarm repository contract leakage | `app/src/main/java/com/example/rise/data/dashboard/AlarmRepository.kt` | Interface returns `AlarmQuery` exposing `asFirestoreQuery(): Query`. | `migrate-now` | Replace Firestore query leakage with backend-neutral paging/list contract. |
| Dashboard alarm screen | `app/src/main/java/com/example/rise/ui/dashboardNavigation/dashboard/DashboardFragment.kt` | Imports Firestore query/exception types and uses Firestore-specific Recycler adapter. | `delete-after-cutover` | Delete/replace with backend-neutral adapter once alarm repository contract is decoupled from Firestore. |
| Alarm Recycler adapter | `app/src/main/java/com/example/rise/ui/dashboardNavigation/dashboard/recyclerview/MyFireStoreAlarmRecyclerViewAdapter.kt` | Direct `FirebaseFirestore.getInstance()` and `FirebaseAuth.getInstance()` deletes in UI adapter. | `delete-after-cutover` | Replace with viewmodel/repository actions; remove direct Firestore UI writes. |
| Alarm model annotation | `app/src/main/java/com/example/rise/ui/alarm/models/Alarm.kt` | ✅ Removed `@IgnoreExtraProperties` on 2026-03-03; `Alarm` no longer imports Firestore annotations. | `migrate-now` | Completed: keep alarm/domain models backend-neutral and prevent reintroducing Firestore annotations outside adapter layers. |

## Recommended Migration Order (Autowork Default)

1. `migrate-now` core-flow control path
- `App.kt` Firebase singleton bindings and default wiring
- auth stack (`FirebaseAuthenticationService`, `FirebaseAuthStateProvider`, `FirebaseSignInRepository`, `SignInViewModel`)
- people/account Firestore fallback behavior (`FirestorePeopleSync`, `RouterMyAccountRepository`, transport gating assumptions)

2. `migrate-now` UI/domain leakage cleanup
- `MessageItem` and `ChatActivity` direct `FirebaseAuth` access (✅ removed 2026-03-03)
- `AlarmRepository` Firestore query leakage (`asFirestoreQuery`)
- Firestore annotations in app models (`Alarm`) (✅ completed 2026-03-03)

3. `temporary-legacy` adapter containment hardening
- Keep `FirestoreConnector`, `FirebaseUserRemoteDataSource`, `FirebaseChatRemoteDataSource`, FCM service scoped behind interfaces
- Do not expand legacy adapter behavior unless required for migration blockers/regressions

4. `delete-after-cutover` cleanup
- Firestore Recycler adapters/screens for alarms
- manifest/service entries and Gradle deps that become unused after cutover

## Inventory Maintenance Rules

- When a Firebase/Firestore surface is removed, update this file in the same PR/commit.
- If a `temporary-legacy` item leaks into UI/domain code, retag it to `migrate-now`.
- If a new Firebase dependency is introduced for a blocker, add an entry with a blocker note and planned removal condition.
