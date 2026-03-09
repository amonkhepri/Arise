#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <executor|reviewer> ..." >&2
  exit 1
fi

lane="$1"
shift

case "$lane" in
  executor)
    "$SCRIPT_DIR/gptoss_task_queue.py" complete-executor "$@"
    ;;
  reviewer)
    "$SCRIPT_DIR/gptoss_task_queue.py" complete-reviewer "$@"
    ;;
  *)
    echo "unknown lane: $lane" >&2
    exit 1
    ;;
esac
