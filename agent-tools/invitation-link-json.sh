#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-json.sh [--help|-h]

Print JSON metadata for the latest invitation capture bundle.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-json.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""
SCREENSHOT_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
    "SCREENSHOT: "*)
      SCREENSHOT_FILE="${line#SCREENSHOT: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" || -z "$SCREENSHOT_FILE" ]]; then
  echo "Failed to resolve latest invitation artifacts" >&2
  exit 1
fi

PREFIX="$(basename "$LOG_FILE")"
PREFIX="${PREFIX%-invitation-logcat.txt}"
LOG_BYTES="$(wc -c < "$LOG_FILE" | tr -d ' ')"
SHOT_BYTES="$(wc -c < "$SCREENSHOT_FILE" | tr -d ' ')"

printf '{\n'
printf '  "prefix": "%s",\n' "$PREFIX"
printf '  "log": "%s",\n' "$LOG_FILE"
printf '  "screenshot": "%s",\n' "$SCREENSHOT_FILE"
printf '  "logBytes": %s,\n' "$LOG_BYTES"
printf '  "screenshotBytes": %s\n' "$SHOT_BYTES"
printf '}\n'
