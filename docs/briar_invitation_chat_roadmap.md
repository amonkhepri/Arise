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
- [ ] Define and document invitation-link contract + parser behaviour (required fields, invalid states, duplicate handling) with unit tests.
- [ ] Add/verify deep-link entrypoint that routes invitation links into a dedicated onboarding action path.
- [ ] Implement invitation acceptance use case in the connector/router layer (no UI-layer Briar internals).
- [ ] Ensure identity mapping is persisted (`IdentityRegistry`) so accepted invites resolve to stable people/chat identity keys.
- [ ] Bootstrap or resolve conversation for accepted contact and navigate directly into chat.
- [ ] Add regression coverage for duplicate/expired/invalid invitation links and ensure clear user feedback.
- [ ] Add targeted integration tests for invitation-link onboarding to chat in `HYBRID` and `BRIAR_ONLY`.
- [ ] Add a reusable QA script under `agent-tools/` for invitation-link happy-path smoke testing.

## Guardrails
- Keep Firestore usage out of this onboarding flow unless explicitly required as a temporary compatibility fallback.
- Keep Briar onboarding logic behind repository/router abstractions; avoid direct runtime calls from UI components.
- Maintain strict TDD for each backlog item (failing test first, minimal fix, tests green).

## Progress Log
- 2026-03-04: Roadmap created and set as active execution plan for autowork.
