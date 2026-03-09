#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
LOG_PATH="$REPO_DIR/docs/codex54_openclaw_review_log.md"

if [[ $# -ne 5 ]]; then
  echo "usage: $0 <task-id> <approved|needs_fix> <executor-commit> <reason> <commit-message>" >&2
  exit 1
fi

task_id="$1"
decision="$2"
executor_commit="$3"
reason="$4"
commit_message="$5"

case "$decision" in
  approved)
    log_decision="approved"
    ;;
  needs_fix)
    log_decision="needs-fix"
    ;;
  *)
    echo "invalid decision: $decision" >&2
    exit 1
    ;;
esac

reason="${reason%.}"
mkdir -p "$(dirname "$LOG_PATH")"
if [[ ! -f "$LOG_PATH" ]]; then
  printf '# Codex GPT-5.4 OpenClaw Review Log\n\n' > "$LOG_PATH"
fi

printf -- '- %s task %s commit %s %s: %s.\n' "$(date +%F)" "$task_id" "$executor_commit" "$log_decision" "$reason" >> "$LOG_PATH"

git -C "$REPO_DIR" add -- docs/codex54_openclaw_review_log.md

commit_output="$($SCRIPT_DIR/codex54_git_commit.sh reviewer "$commit_message")"
printf '%s\n' "$commit_output"

commit_sha="$(printf '%s\n' "$commit_output" | sed -n 's/^commit=//p' | tail -n 1)"
if [[ -z "$commit_sha" ]]; then
  echo "missing commit sha" >&2
  exit 1
fi

$SCRIPT_DIR/codex54_task_mark.sh reviewer "$task_id" "$decision" "$commit_sha"
printf 'decision=%s\n' "$decision"
printf 'task=%s\n' "$task_id"
printf 'commit=%s\n' "$commit_sha"
