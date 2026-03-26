#!/usr/bin/env bash

# invitation-link-artifacts.sh
# Helper to locate the most recent invitation capture artifacts.
# Artifacts are saved by invitation-link-capture.sh under agent-tools/screenshots
# with names like <TIMESTAMP>-invitation-logcat.txt and <TIMESTAMP>-invitation.png.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-artifacts.sh [--help|-h]

Prints the newest invitation capture log and screenshot paths.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

# Verify that screenshot directory exists
if [[ ! -d "$SCREENSHOT_DIR" ]]; then
  echo "❌ No invitation capture artifacts directory found: $SCREENSHOT_DIR" >&2
  exit 1
fi

# Find latest log file
LOG_PATHS=( "$SCREENSHOT_DIR"/*-invitation-logcat.txt )
if [[ ${#LOG_PATHS[@]} -eq 0 || ! -e "${LOG_PATHS[0]}" ]]; then
  echo "❌ No invitation log artifacts found in: $SCREENSHOT_DIR" >&2
  exit 1
fi

# Find latest screenshot file
SHOT_PATHS=( "$SCREENSHOT_DIR"/*-invitation.png )
if [[ ${#SHOT_PATHS[@]} -eq 0 || ! -e "${SHOT_PATHS[0]}" ]]; then
  echo "❌ No invitation screenshot artifacts found in: $SCREENSHOT_DIR" >&2
  exit 1
fi

# Sort and pick newest based on filename (timestamp prefix)
# Bash array sort using sort -V
LOG_FILE=$(printf '%s
' "${LOG_PATHS[@]}" | sort -V | tail -n 1)
SHOT_FILE=$(printf '%s
' "${SHOT_PATHS[@]}" | sort -V | tail -n 1)

# Output labeled lines
printf "LOG: %s\n" "$LOG_FILE"
printf "SCREENSHOT: %s\n" "$SHOT_FILE"

exit 0
