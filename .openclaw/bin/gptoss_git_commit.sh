#!/bin/bash

set -euo pipefail

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <executor|reviewer> <message>" >&2
  exit 1
fi

lane="$1"
shift
message="$1"

case "$lane" in
  executor)
    name="gpt-oss-executor[openclaw]"
    email="gptoss-executor@local"
    ;;
  reviewer)
    name="gpt-oss-reviewer[openclaw]"
    email="gptoss-reviewer@local"
    ;;
  *)
    echo "unknown lane: $lane" >&2
    exit 1
    ;;
esac

if git -C "$REPO_DIR" diff --cached --quiet; then
  echo "error=no_staged_changes" >&2
  exit 2
fi

git -C "$REPO_DIR" \
  -c user.name="$name" \
  -c user.email="$email" \
  commit -m "$message"

echo "commit=$(git -C "$REPO_DIR" rev-parse HEAD)"
