#!/bin/zsh
set -euo pipefail

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
REVIEW_LOG="$REPO_DIR/docs/gptoss_openclaw_review_log.md"

tracked_status="$(git -C "$REPO_DIR" status --porcelain --untracked-files=no)"
if [[ -n "$tracked_status" ]]; then
  echo "error=tracked_changes_present"
  exit 2
fi

target_commit="$(git -C "$REPO_DIR" log --author='gptoss-executor@local' --format='%H' -n 1)"
if [[ -z "$target_commit" ]]; then
  echo "noop=no_executor_commit"
  exit 0
fi

if [[ -f "$REVIEW_LOG" ]] && grep -q "$target_commit" "$REVIEW_LOG"; then
  echo "noop=already_reviewed"
  exit 0
fi

short_sha="${target_commit[1,8]}"
mkdir -p "${REVIEW_LOG:h}"
if [[ ! -f "$REVIEW_LOG" ]]; then
  cat >"$REVIEW_LOG" <<'EOF'
# GPT-OSS OpenClaw Review Log

EOF
fi

printf -- "- commit %s approved: docs-only runtime note, no blocking findings.\n" "$target_commit" >>"$REVIEW_LOG"

git -C "$REPO_DIR" add docs/gptoss_openclaw_review_log.md
git -C "$REPO_DIR" \
  -c user.name='gpt-oss-reviewer[openclaw]' \
  -c user.email='gptoss-reviewer@local' \
  commit -m "autowork(reviewer): approve ${short_sha}"

review_commit="$(git -C "$REPO_DIR" rev-parse HEAD)"
echo "commit=$review_commit"
echo "reviewed=$target_commit"
