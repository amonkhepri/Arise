#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
LOG_PATH="$REPO_DIR/.openclaw/codex54_bm_supervisor_log.md"

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <summary> <commit-message> [path ...]" >&2
  exit 1
fi

summary="$1"
commit_message="$2"
shift 2

paths=("$@")
if [[ ${#paths[@]} -eq 0 ]]; then
  paths=(
    ".openclaw/codex54_briar_add_user_tasks.json"
    ".openclaw/bin/codex54_bm_story_tick.sh"
    ".openclaw/bin/codex54_bm_health_check.py"
    ".openclaw/bin/codex54_bm_supervisor_tick.sh"
    ".openclaw/bin/codex54_git_commit.sh"
    ".openclaw/bin/codex54_supervisor_finalize.sh"
    ".openclaw/bin/codex54_task_merge_successor.py"
    ".openclaw/codex54_bm_supervisor_log.md"
  )
fi

mkdir -p "$(dirname "$LOG_PATH")"
if [[ ! -f "$LOG_PATH" ]]; then
  printf '# Codex GPT-5.4 BM Loop Supervisor Log\n\n' > "$LOG_PATH"
fi

printf -- '- %s %s\n' "$(date '+%F %T %Z')" "$summary" >> "$LOG_PATH"

git -C "$REPO_DIR" add -- "${paths[@]}"

commit_output="$("$SCRIPT_DIR/codex54_git_commit.sh" supervisor "$commit_message")"
printf '%s\n' "$commit_output"
