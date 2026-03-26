#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <executor|reviewer>" >&2
  exit 1
fi

"$SCRIPT_DIR/codex54_task_queue.py" next "$1"
