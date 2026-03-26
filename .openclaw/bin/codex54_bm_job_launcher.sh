#!/bin/bash

set -euo pipefail

job_run_before_ts() {
  local label="$1"
  "$PYTHON_BIN" "$RUN_RESULT_BIN" "$label" --latest-ts
}

verify_job_run() {
  local label="$1"
  local before_ts="$2"
  local verification
  verification="$("$PYTHON_BIN" "$RUN_RESULT_BIN" "$label" --after-ts "$before_ts")"
  while IFS= read -r line; do
    [[ -n "$line" ]] && log "$label verification: $line"
  done <<<"$verification"
  printf '%s\n' "$verification" | awk -F= '/^outcome=/{print $2; exit}'
}

run_queue_job() {
  local job_id="$1"
  local label="$2"
  local start_message="$3"
  local success_message="$4"
  local before_ts
  local outcome

  before_ts="$(job_run_before_ts "$label")"
  log "$start_message"
  if ! "$OPENCLAW_BIN" cron run "$job_id" --expect-final --timeout 3600000 >>"$LOG_FILE" 2>&1; then
    log "$label launcher exited non-zero"
    return 1
  fi

  outcome="$(verify_job_run "$label" "$before_ts")"
  case "$outcome" in
    success|noop)
      log "$success_message"
      ;;
    *)
      log "$label failed post-run verification"
      return 1
      ;;
  esac
}
