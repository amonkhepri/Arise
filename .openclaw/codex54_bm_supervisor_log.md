# Codex GPT-5.4 BM Loop Supervisor Log

- 2026-03-07 16:57:18 CET repaired stale_pending_review by moving BU-001 from pending_review to needs_fix after repeated reviewer toolchain failures on missing Java 17, restoring executor-side queue visibility
- 2026-03-07 17:02:13 CET repaired executor_task_error by pinning BU-001 validation to the local Homebrew Java 17 path so executor runs the briar-runtime test helper with the required toolchain
