#!/bin/bash
set -euo pipefail

export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
QUEUE_FILE="$REPO_DIR/.openclaw/codex54_briar_add_user_tasks.json"
OPENCLAW_BIN="${OPENCLAW_BIN:-/opt/homebrew/bin/openclaw}"
PYTHON_BIN="${PYTHON_BIN:-/usr/bin/python3}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

LOCK_DIR="/Users/amunratis/.openclaw/codex54-bm-story-tick.lock"
LOG_DIR="/Users/amunratis/.openclaw/logs"
LOG_FILE="$LOG_DIR/codex54-bm-story-tick.log"
STALE_RUNNER_PID="/Users/amunratis/.openclaw/codex54-bm-story-runner.pid"

EXECUTOR_ID="d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1"
REVIEWER_ID="e42a5225-4d7a-4b4b-a2f4-f0467be4934e"

mkdir -p "$LOG_DIR"

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$*" >>"$LOG_FILE"
}

run_completion_supervisor() {
  local output
  local extra_args=()
  if [[ -n "${BRIAR_COMPLETION_INVITATION_URI:-}" ]]; then
    extra_args+=(--invitation-uri "$BRIAR_COMPLETION_INVITATION_URI")
  fi
  if (( ${#extra_args[@]} )); then
    output="$("$SCRIPT_DIR/codex54_queue_completion_supervisor.py" --queue-path "$QUEUE_FILE" --policy briar_add_user "${extra_args[@]}" 2>&1)" || {
      while IFS= read -r line; do
        [[ -n "$line" ]] && log "completion supervisor: $line"
      done <<<"$output"
      echo "error"
      return 0
    }
  else
    output="$("$SCRIPT_DIR/codex54_queue_completion_supervisor.py" --queue-path "$QUEUE_FILE" --policy briar_add_user 2>&1)" || {
      while IFS= read -r line; do
        [[ -n "$line" ]] && log "completion supervisor: $line"
      done <<<"$output"
      echo "error"
      return 0
    }
  fi
  while IFS= read -r line; do
    [[ -n "$line" ]] && log "completion supervisor: $line"
  done <<<"$output"
  printf '%s\n' "$output" | awk -F= '/^action=/{print $2; exit}'
}

cleanup() {
  rm -rf "$LOCK_DIR"
}

if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  exit 0
fi
trap cleanup EXIT

echo "$$" > "$LOCK_DIR/pid"

if [[ -f "$STALE_RUNNER_PID" ]]; then
  stale_pid="$(cat "$STALE_RUNNER_PID" 2>/dev/null || true)"
  if [[ -z "$stale_pid" ]] || ! kill -0 "$stale_pid" 2>/dev/null; then
    rm -f "$STALE_RUNNER_PID"
  fi
fi

if pgrep -f "cron:${EXECUTOR_ID}|cron:${REVIEWER_ID}" >/dev/null 2>&1; then
  log "skip: codex54 queue job already running"
  exit 0
fi

review_probe="$("$SCRIPT_DIR/codex54_task_next.sh" reviewer)"
if grep -q '^result: task$' <<<"$review_probe"; then
  action="reviewer"
else
  exec_probe="$("$SCRIPT_DIR/codex54_task_next.sh" executor)"
  if grep -q '^result: task$' <<<"$exec_probe"; then
    action="executor"
  else
    action="$("$PYTHON_BIN" - "$QUEUE_FILE" <<'PY'
import json
import sys
from pathlib import Path

queue = json.loads(Path(sys.argv[1]).read_text())
tasks = queue["tasks"]
if not tasks:
    print("none")
    raise SystemExit(0)

if all(task["status"] == "done" for task in tasks):
    print("done")
else:
    print("wait")
PY
)"
  fi
fi

case "$action" in
  reviewer)
    log "tick: starting reviewer"
    "$OPENCLAW_BIN" cron run "$REVIEWER_ID" --expect-final --timeout 3600000 >>"$LOG_FILE" 2>&1
    log "tick: reviewer completed"
    ;;
  executor)
    log "tick: starting executor"
    "$OPENCLAW_BIN" cron run "$EXECUTOR_ID" --expect-final --timeout 3600000 >>"$LOG_FILE" 2>&1
    log "tick: executor completed"
    ;;
  done)
    completion_action="$(run_completion_supervisor)"
    case "$completion_action" in
      executor)
        log "tick: seeded follow-up BU queue; starting executor"
        "$OPENCLAW_BIN" cron run "$EXECUTOR_ID" --expect-final --timeout 3600000 >>"$LOG_FILE" 2>&1
        log "tick: executor completed after completion supervisor"
        ;;
      done)
        log "tick: all queue tasks completed"
        ;;
      *)
        log "tick: completion supervisor returned $completion_action"
        ;;
    esac
    ;;
  wait)
    log "tick: queue waiting"
    ;;
  none)
    log "tick: no queue tasks found"
    ;;
  *)
    log "tick: unexpected action '$action'"
    ;;
esac
