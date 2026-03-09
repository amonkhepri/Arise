#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
QUEUE_PATH="$REPO_DIR/.openclaw/codex54_briar_add_user_tasks.json"

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <task-id> <commit-message>" >&2
  exit 1
fi

task_id="$1"
commit_message="$2"

allowed_paths=()
while IFS= read -r path; do
  [[ -n "$path" ]] && allowed_paths+=("$path")
done < <(python3 - "$task_id" "$QUEUE_PATH" <<'PY'
import json
import sys
from pathlib import Path

task_id = sys.argv[1]
queue_path = Path(sys.argv[2])
data = json.loads(queue_path.read_text())
for task in data["tasks"]:
    if task["id"] == task_id:
        for path in task.get("allowedPaths", []):
            print(path)
        break
else:
    raise SystemExit(f"task not found: {task_id}")
PY
)

if [[ ${#allowed_paths[@]} -eq 0 ]]; then
  echo "no allowed paths for task: $task_id" >&2
  exit 1
fi

git -C "$REPO_DIR" add -- "${allowed_paths[@]}"

commit_output="$($SCRIPT_DIR/codex54_git_commit.sh executor "$commit_message")"
printf '%s\n' "$commit_output"

commit_sha="$(printf '%s\n' "$commit_output" | sed -n 's/^commit=//p' | tail -n 1)"
if [[ -z "$commit_sha" ]]; then
  echo "missing commit sha" >&2
  exit 1
fi

$SCRIPT_DIR/codex54_task_mark.sh executor "$task_id" "$commit_sha"
printf 'task=%s\n' "$task_id"
printf 'commit=%s\n' "$commit_sha"
