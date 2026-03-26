#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="${REPO_DIR:-/Users/amunratis/AndroidStudioProjects/Arise-autowork}"

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <heartbeat-commit-message> [--tracked] [extra-path ...]" >&2
  exit 1
fi

commit_message="$1"
shift

if [[ "$commit_message" != heartbeat\(arise\):* ]]; then
  echo "commit message must start with 'heartbeat(arise):'" >&2
  exit 1
fi

default_paths=(
  "docs/autowork_state.json"
  ".openclaw/heartbeat-report.md"
)

stage_tracked=0
extra_paths=()

for arg in "$@"; do
  if [[ "$arg" == "--tracked" ]]; then
    stage_tracked=1
  else
    extra_paths+=("$arg")
  fi
done

if [[ $stage_tracked -eq 1 ]]; then
  git -C "$REPO_DIR" add -u -- .
fi

git -C "$REPO_DIR" add -- "${default_paths[@]}" "${extra_paths[@]}"

commit_output="$("$SCRIPT_DIR/codex54_git_commit.sh" executor "$commit_message")"
printf '%s\n' "$commit_output"

commit_sha="$(printf '%s\n' "$commit_output" | sed -n 's/^commit=//p' | tail -n 1)"
if [[ -z "$commit_sha" ]]; then
  echo "missing commit sha" >&2
  exit 1
fi

if [[ "${SKIP_PR_SYNC:-0}" == "1" ]]; then
  printf 'pr_sync_status=skipped\n'
  exit 0
fi

if ! pr_sync_output="$("$SCRIPT_DIR/codex54_pr_sync.py" --lane executor --commit "$commit_sha" 2>&1)"; then
  printf 'pr_sync_status=helper_failed\n'
  printf '%s\n' "$pr_sync_output"
else
  printf '%s\n' "$pr_sync_output"
fi
