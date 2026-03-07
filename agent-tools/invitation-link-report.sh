#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"
LINES=20

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-report.sh [--lines N] [--help|-h]

Print a Markdown summary for the latest invitation capture bundle.

Options:
  --lines N     Number of log lines to include (default: 20).
  --help, -h    Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --lines)
      if [[ $# -lt 2 || ! "$2" =~ ^[0-9]+$ || "$2" -lt 1 ]]; then
        echo "--lines requires a positive integer" >&2
        exit 1
      fi
      LINES="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

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

printf '# Invitation Artifact Report\n\n'
printf -- '- Prefix: %s\n' "$PREFIX"
printf -- '- Log: %s\n' "$LOG_FILE"
printf -- '- Screenshot: %s\n\n' "$SCREENSHOT_FILE"
printf '## Log Tail\n'
tail -n "$LINES" "$LOG_FILE"
