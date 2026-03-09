#!/bin/bash

set -euo pipefail

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
QUEUE_PATH="$REPO_DIR/.openclaw/codex54_briar_add_user_tasks.json"

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <task-id>" >&2
  exit 1
fi

task_id="$1"

eval "$(python3 - "$task_id" "$QUEUE_PATH" <<'PY'
import json
import shlex
import sys
from pathlib import Path

task_id = sys.argv[1]
queue_path = Path(sys.argv[2])
data = json.loads(queue_path.read_text())

task = None
for candidate in data["tasks"]:
    if candidate["id"] == task_id:
        task = candidate
        break

if task is None:
    raise SystemExit(f"task not found: {task_id}")

if task.get("status") != "pending_review":
    raise SystemExit(f"task {task_id} not pending review: {task.get('status')}")

executor_commit = task.get("executorCommit") or ""
if not executor_commit:
    raise SystemExit(f"task {task_id} is missing executor commit")

print(f"executor_commit={shlex.quote(executor_commit)}")
print("allowed_paths=(" + " ".join(shlex.quote(path) for path in task.get("allowedPaths", [])) + ")")
print("validations=(" + " ".join(shlex.quote(cmd) for cmd in task.get("validation", [])) + ")")
PY
)"

if [[ -z "${executor_commit:-}" ]]; then
  echo "missing executor commit" >&2
  exit 1
fi

if [[ ${#allowed_paths[@]} -eq 0 ]]; then
  echo "no allowed paths for task: $task_id" >&2
  exit 1
fi

changed_files=()
while IFS= read -r changed_file; do
  [[ -n "$changed_file" ]] && changed_files+=("$changed_file")
done < <(git -C "$REPO_DIR" diff-tree --no-commit-id --name-only -r "$executor_commit")

if [[ ${#changed_files[@]} -eq 0 ]]; then
  echo "commit has no changed files: $executor_commit" >&2
  exit 1
fi

for changed_file in "${changed_files[@]}"; do
  allowed=0
  for allowed_path in "${allowed_paths[@]}"; do
    if [[ "$changed_file" == "$allowed_path" ]]; then
      allowed=1
      break
    fi
  done
  if [[ $allowed -ne 1 ]]; then
    echo "commit changed file outside allowed paths: $changed_file" >&2
    exit 1
  fi
done

for validation in "${validations[@]}"; do
  /bin/zsh -lc "cd '$REPO_DIR' && $validation"
done

printf 'result: approved\n'
printf 'task: %s\n' "$task_id"
printf 'executor_commit: %s\n' "$executor_commit"
printf 'reason: validation commands passed and commit stayed within allowed paths\n'
