#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"
IGNORE_CASE=0
PATTERN=""

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-log-search.sh [--ignore-case] PATTERN

Search the latest invitation capture log for a pattern.

Options:
  --ignore-case  Match case-insensitively.
  --help, -h     Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --ignore-case)
      IGNORE_CASE=1
      shift
      ;;
    --*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -n "$PATTERN" ]]; then
        echo "Provide exactly one PATTERN" >&2
        exit 1
      fi
      PATTERN="$1"
      shift
      ;;
  esac
done

if [[ -z "$PATTERN" ]]; then
  echo "Missing PATTERN" >&2
  show_help
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

printf 'LOG: %s\n' "$LOG_FILE"
if [[ "$IGNORE_CASE" -eq 1 ]]; then
  MATCHES="$(grep -niE "$PATTERN" "$LOG_FILE" || true)"
else
  MATCHES="$(grep -nE "$PATTERN" "$LOG_FILE" || true)"
fi

if [[ -z "$MATCHES" ]]; then
  echo "No matches found for pattern: $PATTERN" >&2
  exit 1
fi

printf '%s\n' "$MATCHES"
