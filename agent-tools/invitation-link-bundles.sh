#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
LIMIT=5

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-bundles.sh [--limit N] [--help|-h]

List recent invitation capture bundles from agent-tools/screenshots.

Options:
  --limit N     Show up to N bundles (default: 5).
  --help, -h    Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --limit)
      if [[ $# -lt 2 || ! "$2" =~ ^[0-9]+$ || "$2" -lt 1 ]]; then
        echo "--limit requires a positive integer" >&2
        exit 1
      fi
      LIMIT="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "$SCREENSHOT_DIR" ]]; then
  echo "No screenshot directory found: $SCREENSHOT_DIR" >&2
  exit 1
fi

LOG_PATHS=( "$SCREENSHOT_DIR"/*-invitation-logcat.txt )
if [[ ${#LOG_PATHS[@]} -eq 0 || ! -e "${LOG_PATHS[0]}" ]]; then
  echo "No invitation bundles found in: $SCREENSHOT_DIR" >&2
  exit 1
fi

COUNT=0
while IFS= read -r log_path; do
  prefix="$(basename "$log_path")"
  prefix="${prefix%-invitation-logcat.txt}"
  shot_path="$SCREENSHOT_DIR/${prefix}-invitation.png"
  printf 'PREFIX: %s\n' "$prefix"
  printf 'LOG: %s\n' "$log_path"
  printf 'SCREENSHOT: %s\n' "$shot_path"
  printf '\n'
  COUNT=$((COUNT + 1))
  if [[ "$COUNT" -ge "$LIMIT" ]]; then
    break
  fi
done < <(printf '%s\n' "${LOG_PATHS[@]}" | sort -Vr)
