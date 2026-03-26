#!/bin/bash

set -euo pipefail

export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WINDOW_GUARD="$SCRIPT_DIR/codex54_bm_window_guard.sh"
CODEX_BIN="/opt/homebrew/bin/codex"
HEALTH_SCRIPT="$SCRIPT_DIR/codex54_bm_health_check.py"
LOG_DIR="/Users/amunratis/.openclaw/logs"
LOG_FILE="$LOG_DIR/codex54-bm-supervisor-tick.log"
LOCK_DIR="/Users/amunratis/.openclaw/codex54-bm-supervisor-tick.lock"

mkdir -p "$LOG_DIR"

if [[ -x "$WINDOW_GUARD" ]] && ! "$WINDOW_GUARD"; then
  exit 0
fi

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$*" >>"$LOG_FILE"
}

cleanup() {
  rm -rf "$LOCK_DIR"
}

if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  exit 0
fi
trap cleanup EXIT

health_json="$(/usr/bin/python3 "$HEALTH_SCRIPT" --json)"
healthy="$(printf '%s\n' "$health_json" | /usr/bin/python3 -c 'import json,sys; print(str(json.load(sys.stdin)["healthy"]).lower())')"

if [[ "$healthy" == "true" ]]; then
  log "healthy: no action"
  exit 0
fi

if pgrep -f "codex exec .*codex-supervisor\\[openclaw\\]|codex54_bm_supervisor_tick.sh" >/dev/null 2>&1; then
  log "unhealthy but supervisor already running"
  exit 0
fi

executor_active="$(printf '%s\n' "$health_json" | /usr/bin/python3 -c 'import json,sys; print(str(json.load(sys.stdin)["active"]["executor"]).lower())')"
reviewer_active="$(printf '%s\n' "$health_json" | /usr/bin/python3 -c 'import json,sys; print(str(json.load(sys.stdin)["active"]["reviewer"]).lower())')"
if [[ "$executor_active" == "true" || "$reviewer_active" == "true" ]]; then
  log "unhealthy but worker active; deferring supervisor"
  exit 0
fi

issues="$(printf '%s\n' "$health_json" | /usr/bin/python3 -c 'import json,sys; print(",".join(json.load(sys.stdin)["issues"]))')"
suggestions="$(printf '%s\n' "$health_json" | /usr/bin/python3 -c 'import json,sys; print(",".join(json.load(sys.stdin)["suggestions"]))')"
log "unhealthy: issues=${issues:-none} suggestions=${suggestions:-none}"
prompt_file="$(mktemp)"
trap 'rm -f "$prompt_file"; cleanup' EXIT
cat >"$prompt_file" <<EOF
Autonomous \`gpt-5.4\` Codex supervisor run for /Users/amunratis/AndroidStudioProjects/Arise-autowork.

Goal: repair exactly one unhealthy sender-side Briar add-user queue/orchestration issue without touching product code.

Current health snapshot:
\`\`\`json
$health_json
\`\`\`

Workflow:
1. Re-run \`./.openclaw/bin/codex54_bm_health_check.py --json\` only if you need a fresh snapshot.
2. If the loop is healthy, stop and report noop.
3. If unhealthy, repair exactly one issue and stop.

Allowed actions:
- Run \`./.openclaw/bin/codex54_task_merge_successor.py <task-id>\` when the health check reports \`red_green_merge_candidate\`.
- Run \`./.openclaw/bin/codex54_task_queue.py list >/dev/null\` to normalize queue state.
- Run \`openclaw cron run e42a5225-4d7a-4b4b-a2f4-f0467be4934e --expect-final --timeout 3600000\` when review is stale.
- Run \`openclaw cron run d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1 --expect-final --timeout 3600000\` when executor is stale.
- Edit only repo-local orchestration files under \`./.openclaw/\`.
- Use \`./.openclaw/bin/codex54_supervisor_finalize.sh "<summary>" "<commit-message>" [paths...]\` if you change repo-local \`.openclaw\` files.

Hard rules:
- Do not edit \`app/src\`, \`agent-tools\`, or docs outside \`./.openclaw/\`.
- Do not change the product backlog except for queue metadata inside \`./.openclaw/codex54_briar_add_user_tasks.json\`.
- Do not start both executor and reviewer in the same run.
- Do not amend existing commits.
- Prefer the smallest repair that restores forward progress.

Final response format (exactly 6 plain-text lines):
result: <success|noop|error>
issue: <issue-code|none>
action: <short-action|none>
commit: <sha|none>
author: codex-supervisor[openclaw] <codex-supervisor@local>|none
files: <comma-separated repo-relative paths|none>
EOF
"$CODEX_BIN" exec --json --color never --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check --model gpt-5.4 "$(cat "$prompt_file")" >>"$LOG_FILE" 2>&1
log "supervisor completed"
