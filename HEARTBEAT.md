# Arise Heartbeat

Current autonomous priority:
- enable Briar messaging
- first unlock adding users via invitation link
- keep working toward the first invitation-link add-user to chat slice

On every heartbeat:
1. `cd /Users/amunratis/AndroidStudioProjects/Arise-autowork`
2. Read `AGENTS.md`, `MEMORY.md`, relevant files under `memory/`, `docs/autowork_state.json`, `docs/briar_invitation_chat_roadmap.md`,`./.openclaw/heartbeat-report.md` and especially `docs/briar_contact_link_invitation.md` - that one is new and can throw light on the current priority.
3. Check `git status --short`.
4. If tracked changes already exist, continue that exact in-progress task first. Do not start a new one.
5. Otherwise choose exactly one small, safe next step toward enabling Briar add-user via invitation link.
6. Follow repo `AGENTS.md` strictly:
   - clarify the requirement
   - write or update a failing test first when product behavior changes
   - make the minimal fix
   - run the smallest relevant validation set
7. For invite-link validation, keep Arise under test on `emulator-5554` (`Medium_Phone_API_36.1`) unless a task explicitly requires another device. Use the separate `Pixel_7` emulator (`adb` serial `emulator-5556`) as the official Briar peer/baseline device. Do not swap those roles casually.
8. If you had to figure out a durable project fact, environment mapping, repeatable command, or non-obvious gotcha during this run, write it down before finishing by updating `MEMORY.md` or adding/updating a concise note under `memory/`. Do not leave rediscovered knowledge only in transient logs or your reasoning.
9. Update `docs/autowork_state.json` if progress or blockers changed.
10. Update `./.openclaw/heartbeat-report.md`.
11. Before replying, create exactly one commit for this heartbeat whenever this run changed any tracked file or durable project file. Do not leave tracked changes uncommitted at the end of a normal heartbeat.
   - Use one of these commit message shapes:
     - `heartbeat(arise): <short task summary>`
     - `heartbeat(arise): no-op <short reason>`
     - `heartbeat(arise): blocked <short reason>`
   - Prefer `./.openclaw/bin/codex54_heartbeat_commit.sh "<message>" [--tracked] <extra-path>...` when it exists. That helper automatically stages `docs/autowork_state.json` and `./.openclaw/heartbeat-report.md`.
   - If this run started with safe-to-absorb tracked changes, include `--tracked` so all tracked modifications/deletions are staged into the heartbeat commit before adding any extra untracked files.
   - Pass every non-tracked file from this heartbeat explicitly, such as newly created code/tests, `MEMORY.md`, or roadmap/docs notes.
   - If the helper is missing, stage only the files that belong to this heartbeat's task/report/state/memory update and commit them manually. Do not sweep unrelated temp files, screenshots, or logs into the commit.
12. After committing, keep the long-lived branch/PR current. `./.openclaw/bin/codex54_heartbeat_commit.sh` already does this. If you had to commit manually, run `./.openclaw/bin/codex54_pr_sync.py --lane executor --commit <sha>`.

Rules:
- Heartbeat should do direct Arise project work, not queue-control work.
- Do not use `./.openclaw/bin/codex54_bm_story_tick.sh` or `./.openclaw/bin/codex54_bm_supervisor_tick.sh` as the heartbeat mechanism.
- Do not use the old Briar queue files as the source of truth for what heartbeat should do.
- Do not start unrelated roadmap work while the invitation-link add-user slice is unfinished unless you discover that invitation-link add-user slice is blocked by a huge blocker that needs to be addressed. Note down the blocker in docs/briar_invitation_chat_roadmap.md.
- Prefer one small, reviewable step over broad exploration.
- Every normal heartbeat must end at a commit boundary. Never leave tracked worktree changes behind for the next heartbeat just because the step feels "almost done".
- If the run starts with tracked changes, treat them as the active in-progress task and carry them through to a commit in the same heartbeat. If you cannot validate or commit them safely in this run, emit a blocked alert instead of leaving the worktree dirty again.
- If tracked changes appear to be unrelated human work or otherwise unsafe to absorb, stop and alert rather than guessing and committing them.
- Keep changes factual. Never claim files, tests, commits, or successful behavior unless they actually happened in this run.
- If blocked, record the exact blocker and the next concrete unblock step in the report.
- Treat project memory as part of the working system. Before deep investigation, check existing memory notes. When you learn something stable enough to save someone from rediscovering it later, add a memory entry in the same run.
- Keep memory entries concise and durable. Store stable workflows, environment mappings, validated assumptions, and recurring pitfalls; do not dump raw logs or ephemeral observations unless they materially change understanding.
- Treat same-device Briar-vs-Arise experiments as diagnostics only unless explicitly validated. The working baseline for this slice is device-to-device validation: Arise on `emulator-5554`, official Briar peer on `emulator-5556`.
- Do not use empty commits. The minimum normal heartbeat artifact is the report/state update commit that records what happened in this run.

Heartbeat report requirements:
- `./.openclaw/heartbeat-report.md` must always contain:
  - `Latest heartbeat`: time, mode, task, files changed, tests run, commit, blocker, next step
  - `Recent heartbeats`: up to 20 one-line entries, newest first
- Update the report even for no-op or blocked runs.

Reply behavior:
- After updating the report, reply `HEARTBEAT_OK` unless human attention is required.
- Only return a non-OK reply when the run is blocked in a way that needs the user.

If you must alert, keep it to 4 plain-text lines:
issue: <short blocker>
task: <task or none>
next: <next unblock step>
report: ./.openclaw/heartbeat-report.md
