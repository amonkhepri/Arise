#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-prefix.sh [--help|-h]

Print the prefix for the latest invitation capture bundle.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-prefix.sh [--help|-h]" >&2
  exit 1
fi

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

BASENAME="$(basename "$LOG_FILE")"
PREFIX="${BASENAME%-invitation-logcat.txt}"

printf 'PREFIX: %s\n' "$PREFIX"
