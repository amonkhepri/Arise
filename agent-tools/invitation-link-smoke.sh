#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OPEN_LINK="$SCRIPT_DIR/open-deep-link.sh"

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/invitation-link-smoke.sh [--device SERIAL] INVITATION_URI

Launch an invitation link on a connected device and print a short manual checklist.

Options:
  --device SERIAL  Use a specific device or emulator serial.
  --help, -h       Show this help message.
USAGE
}

DEVICE_ARGS=()
URI=""

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --device)
      if [[ $# -lt 2 ]]; then
        echo "❌ Missing serial after --device" >&2
        exit 1
      fi
      DEVICE_ARGS=(--device "$2")
      shift 2
      ;;
    --*)
      echo "❌ Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -n "$URI" ]]; then
        echo "❌ Provide exactly one invitation URI" >&2
        exit 1
      fi
      URI="$1"
      shift
      ;;
  esac
done

if [[ -z "$URI" ]]; then
  echo "❌ Missing invitation URI" >&2
  show_help
  exit 1
fi

if [[ ! -x "$OPEN_LINK" ]]; then
  echo "❌ Missing helper: $OPEN_LINK" >&2
  exit 1
fi

"$OPEN_LINK" "${DEVICE_ARGS[@]}" "$URI"

cat <<'CHECKLIST'
Manual check:
1. Invitation onboarding opens from the link.
2. The invited contact resolves correctly.
3. Chat opens for that contact.
4. The first message can be attempted from the opened chat.
CHECKLIST
