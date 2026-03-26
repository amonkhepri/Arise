#!/bin/bash

set -euo pipefail

export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"

STATE_FILE="/Users/amunratis/.openclaw/codex54-bm-window.env"
STORY_LOCK="/Users/amunratis/.openclaw/codex54-bm-story-tick.lock"
SUPERVISOR_LOCK="/Users/amunratis/.openclaw/codex54-bm-supervisor-tick.lock"
LOG_DIR="/Users/amunratis/.openclaw/logs"
LOG_FILE="$LOG_DIR/codex54-bm-window.log"

mkdir -p "$LOG_DIR"

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$*" >>"$LOG_FILE"
}

if [[ ! -f "$STATE_FILE" ]]; then
  log "pause-window: no state file present; creating locks only"
  mkdir -p "$STORY_LOCK" "$SUPERVISOR_LOCK"
  exit 0
fi

# shellcheck disable=SC1090
source "$STATE_FILE"

mkdir -p "$STORY_LOCK" "$SUPERVISOR_LOCK"
log "pause-window: locks created"

pkill -f 'codex54_bm_story_tick.sh|codex54_bm_supervisor_tick.sh|cron:d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1|cron:e42a5225-4d7a-4b4b-a2f4-f0467be4934e' >/dev/null 2>&1 || true
log "pause-window: active queue processes terminated"

if [[ -n "${CRONTAB_BACKUP:-}" && -f "${CRONTAB_BACKUP:-}" ]]; then
  crontab "$CRONTAB_BACKUP"
  log "pause-window: restored crontab from $CRONTAB_BACKUP"
else
  log "pause-window: missing crontab backup; skipped restore"
fi

rm -f "$STATE_FILE"
log "pause-window: completed"
