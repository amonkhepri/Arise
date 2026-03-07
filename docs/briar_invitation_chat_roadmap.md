# Briar Invitation-to-Chat Roadmap

## Goal
- Make it possible for a user to open a Briar invitation link, add that person, and start chatting with them in one flow.

## Definition of Done (First Slice)
- A valid invitation link can be opened from inside or outside the app.
- The app resolves the invitation through Briar contact onboarding.
- The invited user appears in people/contact surfaces with a stable canonical identity.
- A conversation is created/resolved for that contact and chat opens successfully.
- Sending the first message from that opened chat succeeds in `HYBRID` and `BRIAR_ONLY`.

## Priority Backlog (Ordered)
- [x] Hotfix blocker: resolve startup crash caused by missing Koin binding for invitation deep-link onboarding (`BriarInvitationDeepLinkEntrypoint`) in `SplashActivityViewModel`.
- [x] Define and document invitation-link contract + parser behaviour (required fields, invalid states, duplicate handling) with unit tests.
- [x] Add/verify deep-link entrypoint that routes invitation links into a dedicated onboarding action path.
- [x] Implement invitation acceptance use case in the connector/router layer (no UI-layer Briar internals).
- [x] Ensure identity mapping is persisted (`IdentityRegistry`) so accepted invites resolve to stable people/chat identity keys.
- [ ] Bootstrap or resolve conversation for accepted contact and navigate directly into chat.
- [ ] Add regression coverage for duplicate/expired/invalid invitation links and ensure clear user feedback.
- [ ] Add targeted integration tests for invitation-link onboarding to chat in `HYBRID` and `BRIAR_ONLY`.
- [x] Add a reusable QA script under `agent-tools/` for invitation-link happy-path smoke testing.

## Guardrails
- Keep Firestore usage out of this onboarding flow unless explicitly required as a temporary compatibility fallback.
- Keep Briar onboarding logic behind repository/router abstractions; avoid direct runtime calls from UI components.
- Maintain strict TDD for each backlog item (failing test first, minimal fix, tests green).

## Progress Log
- 2026-03-06: Added `agent-tools/invitation-link-logcat.sh` and `agent-tools/invitation-link-capture.sh` so invitation smoke runs can collect filtered logs and screenshots without ad-hoc adb commands.
- 2026-03-06: Added `agent-tools/invitation-link-smoke.sh` and a matching quick-guide section so invitation-link smoke checks can be launched consistently from local agent tools.
- 2026-03-06: Preserved the invite-link Briar alias after canonical conversation bootstrap so accepted invites keep stable lookup keys even when `TransportRouterImpl.ensureConversation()` runs, with regression coverage wired through the real router path.
- 2026-03-04: Extended `BriarInvitationAcceptanceUseCase` to optionally bootstrap a canonical conversation via `TransportRouter.ensureConversation` and return the resolved `conversationId` in `Accepted`, with regression coverage.
- 2026-03-04: Fixed invite-acceptance identity regression by ensuring `IdentityRegistryImpl.upsertIdentity(setAsCurrent = false)` never mutates `currentIdentity`; added regression coverage in `BriarInvitationAcceptanceUseCaseTest` to keep current identity unset after accepting an invite.
- 2026-03-04: Persisted accepted invitation identity mapping in `BriarInvitationAcceptanceUseCase` by upserting a deterministic canonical record in `IdentityRegistry` (duplicate key + Briar link alias), with regression unit coverage.
- 2026-03-04: Fixed startup crash (`NoDefinitionFoundException` for `BriarInvitationDeepLinkEntrypoint`) by adding Koin bindings in `App.appModule`, plus a regression test (`AppModuleInvitationBindingsTest`) to ensure the entrypoint remains resolvable.
- 2026-03-04: Added `BriarInvitationAcceptanceUseCase` in the transport invitation layer to parse deep links, delegate contact acceptance via `BriarContactRepository`, and surface accepted/invalid/failed outcomes with dedicated unit coverage.
- 2026-03-04: Added `SplashActivity` invitation `VIEW` intent filters for `arise://briar/invite` and `https://(www.)arise.app/briar/invite`, plus unit coverage for manifest contract and HTTPS onboarding action routing.
- 2026-03-04: Added `BriarInvitationLinkParser` contract + unit coverage and documented the supported invitation link shapes in `docs/briar_invitation_link_contract.md`.
- 2026-03-04: Roadmap created and set as active execution plan for autowork.
- 2026-03-04: Added Splash deep-link routing for invitation links into a dedicated `MainActivity` onboarding action payload (`BriarInvitationOnboardingAction`) with unit coverage in `SplashActivityViewModelTest`.
- 2026-03-06: Added `agent-tools/invitation-link-artifacts.sh` as invitation artifact locator helper.
- 2026-03-06: Added `agent-tools/invitation-link-prefix.sh` and documented it in `docs/invitation_artifact_toolkit.md`.

2026-03-06: Added \`agent-tools/invitation-link-summary.sh\` as invitation artifact summary helper
