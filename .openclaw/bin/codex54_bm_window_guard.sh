#!/bin/bash

set -euo pipefail

STATE_FILE="/Users/amunratis/.openclaw/codex54-bm-window.env"
PAUSE_SCRIPT="/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/bin/codex54_bm_pause_window.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  exit 0
fi

# shellcheck disable=SC1090
source "$STATE_FILE"

if [[ -z "${WINDOW_END_EPOCH:-}" ]]; then
  exit 0
fi

now_epoch="$(date +%s)"
if (( now_epoch < WINDOW_END_EPOCH )); then
  exit 0
fi

/bin/bash "$PAUSE_SCRIPT" >/dev/null 2>&1 || true
exit 1
