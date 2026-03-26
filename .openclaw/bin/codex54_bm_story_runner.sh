#!/bin/bash
set -euo pipefail

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
QUEUE_FILE="$REPO_DIR/.openclaw/codex54_briar_add_user_tasks.json"
LOG_DIR="/Users/amunratis/.openclaw/logs"
LOG_FILE="$LOG_DIR/codex54-bm-story-runner.log"
PID_FILE="/Users/amunratis/.openclaw/codex54-bm-story-runner.pid"
OPENCLAW_BIN="${OPENCLAW_BIN:-$(command -v openclaw)}"
PYTHON_BIN="${PYTHON_BIN:-/usr/bin/python3}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUN_RESULT_BIN="$SCRIPT_DIR/codex54_bm_run_result.py"

EXECUTOR_ID="d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1"
REVIEWER_ID="e42a5225-4d7a-4b4b-a2f4-f0467be4934e"

mkdir -p "$LOG_DIR"

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$*" | tee -a "$LOG_FILE"
}

source "$SCRIPT_DIR/codex54_bm_job_launcher.sh"

cleanup() {
  rm -f "$PID_FILE"
}

trap cleanup EXIT

if [[ -f "$PID_FILE" ]]; then
  existing_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$existing_pid" ]] && kill -0 "$existing_pid" 2>/dev/null; then
    log "runner already active with pid=$existing_pid"
    exit 0
  fi
fi

echo "$$" > "$PID_FILE"

next_action() {
  python3 - "$QUEUE_FILE" <<'PY'
import json
import sys
from pathlib import Path

queue = json.loads(Path(sys.argv[1]).read_text())
tasks = queue["tasks"]
if not tasks:
    print("error:no-bm-tasks")
    raise SystemExit(1)

statuses = [t["status"] for t in tasks]
if all(status == "done" for status in statuses):
    print("done")
    raise SystemExit(0)

if any(status == "pending_review" for status in statuses):
    print("reviewer")
    raise SystemExit(0)

if any(status == "open" for status in statuses):
    print("executor")
    raise SystemExit(0)

print("wait")
PY
}

job_process_running() {
  pgrep -f "cron:${EXECUTOR_ID}|cron:${REVIEWER_ID}" >/dev/null 2>&1
}

wait_for_idle() {
  while job_process_running; do
    log "waiting for active codex54 BM job to finish"
    sleep 10
  done
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

cd "$REPO_DIR"
log "story runner boot"

while true; do
  action="$(next_action)"
  case "$action" in
    done)
      completion_action="$(run_completion_supervisor)"
      case "$completion_action" in
        executor)
          wait_for_idle
          run_queue_job "$EXECUTOR_ID" executor "starting executor" "executor completed" || sleep 30
          ;;
        done)
          log "all queue tasks completed"
          exit 0
          ;;
        *)
          log "queue completion paused with action=$completion_action"
          sleep 30
          ;;
      esac
      ;;
    reviewer)
      wait_for_idle
      run_queue_job "$REVIEWER_ID" reviewer "starting reviewer" "reviewer completed" || sleep 30
      ;;
    executor)
      wait_for_idle
      run_queue_job "$EXECUTOR_ID" executor "starting executor" "executor completed" || sleep 30
      ;;
    wait)
      log "queue waiting; retrying soon"
      sleep 20
      ;;
    *)
      log "unexpected action: $action"
      sleep 30
      ;;
  esac
  sleep 5
done
