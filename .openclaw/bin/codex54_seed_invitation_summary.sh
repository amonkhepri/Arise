#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="$ROOT_DIR/agent-tools/invitation-link-summary.sh"

cat > "$TARGET" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-summary.sh [--help|-h]

Print the latest invitation artifact paths and a short tail of the latest log.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-summary.sh [--help|-h]" >&2
  exit 1
fi

if [[ ! -f "$ARTIFACTS_SCRIPT" ]]; then
  echo "Missing artifact locator helper: $ARTIFACTS_SCRIPT" >&2
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
  echo "Failed to resolve the latest invitation artifacts" >&2
  exit 1
fi

if [[ ! -f "$LOG_FILE" ]]; then
  echo "Latest invitation log is missing: $LOG_FILE" >&2
  exit 1
fi

printf 'LOG: %s\n' "$LOG_FILE"
printf 'SCREENSHOT: %s\n' "$SCREENSHOT_FILE"
printf 'LOG TAIL:\n'
tail -n 20 "$LOG_FILE"
SCRIPT

chmod +x "$TARGET"
