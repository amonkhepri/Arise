# Repository Guidelines


You are a software engineer who strictly follows Test-Driven Development (TDD).

For every task, follow this loop:

1. Clarify the requirement
    - Briefly restate what needs to be changed or implemented.
    - Identify the observable behaviour that should change (inputs/outputs, side effects).

2. Write or update tests first
    - Inspect the existing test suite.
    - If the requested behaviour is not fully covered, ADD or MODIFY tests to express it.
    - Each new requirement or bugfix must be captured by at least one failing test.
    - Do NOT change production code before there is a failing test that demonstrates the problem or missing behaviour.

3. Run tests and confirm they fail for the right reason
    - Show which new/updated test fails and why.
    - If tests don’t fail, strengthen them until they do.

4. Implement the minimal code to pass the tests
    - Change only the production code necessary to make the failing tests pass.
    - Keep changes small and focused on the current test.

5. Run the full test suite again
    - Confirm all tests pass.
    - If anything fails, iterate by improving tests or implementation.

6. Refactor with tests green
    - Only refactor when all tests pass.
    - After refactoring, re-run tests to ensure behaviour is unchanged.

Additional rules:
- Prefer adding tests over weakening or deleting existing ones; only relax tests if the specification itself has changed.
- Make test names and descriptions explicit about the behaviour being validated.
- When presenting your work, structure it as:
    1) summary of requirement,
    2) test changes (new/updated/removed),
    3) production code changes,
    4) commands to run the tests and expected outcome.

## Agent-Specific Tips
- When implementing a feature or debugging in continuous mode, prefer the scripts in `agent-tools/` over ad-hoc terminal commands.
- These utilities focus on building, logging, and capturing device screenshots—use them to validate that your solution works end to end.
- Always check `agent-tools/` for an existing helper before running ad-hoc commands; if none exists, add a reusable script there so future runs stay consistent.
- `agent-tools/build.sh` – calls `gradlew assembleDebug` with the correct `JAVA_HOME` for local builds.
- `agent-tools/install.sh` – wraps `gradlew installDebug` for quick device deployment.
- `agent-tools/test.sh` – triggers `gradlew testDebugUnitTest` for JVM tests.
- `agent-tools/run-and-log.sh` – builds, installs, launches, captures filtered logcat, and saves screenshots.
- `agent-tools/click.sh` – executes tap/swipe/text/key sequences via ADB; supports `--file` for scripted flows.
- `agent-tools/dump_ui.sh` – dumps the UI hierarchy (`uiautomator dump`) and optionally greps for text. Useful when creating scripts to navigate across UI(for example find where is the button located)
- `agent-tools/screenshot.sh` – saves timestamped device screenshots into `agent-tools/screenshots/`.
- `agent-tools/send_keyevent.sh` – sends a single Android key event to the active device.
- Reference scenario files (e.g., `test_delayed_message.txt`, `schedule_message.txt`) when automating UI flows, and update them if UI coordinates change.
- Store temporary artifacts in `agent-tools/screenshots/` or discard them before submitting a PR to keep diffs clean.

## Current Architecture Direction (High Priority)
- Read `README.md` before making product-level or architecture-level changes. Treat it as the current vision and migration context.
- Firestore/Firebase usage is legacy and is being phased out. Do not introduce new Firestore or Firebase dependencies, auth flows, repositories, listeners, or schema coupling unless the user explicitly asks for legacy maintenance.
- Authentication must be connector-driven (Briar first, or another connector if available). New authentication logic should go through transport/connector abstractions rather than Firebase-specific APIs.
- Prefer connector-capability-based design (for auth/account/profile features) so new connectors can participate without UI rewrites.
- If modifying existing Firestore code, keep changes minimal and migration-oriented; avoid expanding Firestore surface area.

## Project Structure & Module Organization
- `app/` contains the Android application code (Kotlin, XML resources, instrumentation tests).
- `commons/` holds shared utilities consumed by the app module.
- `agent-tools/` centralizes automation helpers for device scripting, screenshots, and manual QA guides.
- `build.gradle`, `settings.gradle`, and the `gradle/` wrapper support project-wide configuration.
- Test data, notes, and artifacts live alongside the relevant feature folders to keep changes contextual.

## Coding Style & Naming Conventions
- Kotlin: prefer expressive, camelCase identifiers; keep functions <200 lines and split features into ViewModel, UI, and data packages.
- XML layouts follow snake_case filenames (e.g., `fragment_dashboard.xml`) and use Material attributes for theming.
- Indentation is two spaces for Kotlin and XML; avoid trailing whitespace and keep imports sorted automatically via Android Studio.
- Strings, colors, and dimens belong in resource files; do not hardcode literals in layouts or code unless scoped to tests.

## Testing Guidelines
- Unit tests (JUnit + Mockito/Kotlin coroutines) live under `app/src/test/...` and mirror the production package structure.
- Instrumentation/UI tests (Espresso) live under `app/src/androidTest/...`; name classes with the `*Test` suffix (e.g., `SignInCredentialSavingTest`).
- When adding new features, include at least one unit test and, when applicable, an instrumentation scenario covering primary flows.
- Run `./gradlew testDebugUnitTest connectedAndroidTest` before opening a pull request if your changes affect runtime behavior.

## Commit & Pull Request Guidelines
- Craft commits with present-tense, concise subjects (e.g., `Add credential caching for chat`).
- Group related changes; avoid mixing refactors, formatting, and feature work in the same commit.
- Pull requests should explain the motivation, summarize changes, list validation steps (tests, manual checks), and link to tracker issues.
- Attach screenshots or screen recordings for UI changes, and mention any follow-up tasks or known limitations.
