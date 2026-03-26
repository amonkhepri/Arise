#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"
LINES=40

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-log-tail.sh [--lines N] [--help|-h]

Print the tail of the latest invitation capture log.

Options:
  --lines N     Number of lines to print (default: 40).
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

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" ]]; then
  echo "Failed to resolve latest invitation log path" >&2
  exit 1
fi

printf 'LOG: %s\n' "$LOG_FILE"
printf 'TAIL: last %s lines\n' "$LINES"
tail -n "$LINES" "$LOG_FILE"
