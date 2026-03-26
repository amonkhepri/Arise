#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
SMOKE_HELPER="$SCRIPT_DIR/invitation-link-smoke.sh"
LOGCAT_HELPER="$SCRIPT_DIR/invitation-link-logcat.sh"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/invitation-link-capture.sh [--device SERIAL] INVITATION_URI

Launch an invitation link, save filtered invitation logs, and capture a screenshot.

Options:
  --device SERIAL  Use a specific device or emulator serial.
  --help, -h       Show this help message.
USAGE
}

pick_device() {
  "$ADB" devices | awk '/\tdevice$/{print $1; exit}'
}

DEVICE="${ANDROID_SERIAL:-}"
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
      DEVICE="$2"
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

if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

if [[ ! -x "$SMOKE_HELPER" || ! -x "$LOGCAT_HELPER" ]]; then
  echo "❌ Missing invitation helper dependency" >&2
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

mkdir -p "$SCREENSHOT_DIR"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_PATH="$SCREENSHOT_DIR/${TIMESTAMP}-invitation-logcat.txt"
SHOT_PATH="$SCREENSHOT_DIR/${TIMESTAMP}-invitation.png"

echo "🔗 Launching invitation smoke on $DEVICE"
"$SMOKE_HELPER" --device "$DEVICE" "$URI"

echo "📝 Saving invitation logs to $LOG_PATH"
"$LOGCAT_HELPER" --device "$DEVICE" >"$LOG_PATH"

echo "📸 Saving screenshot to $SHOT_PATH"
"$ADB" -s "$DEVICE" exec-out screencap -p >"$SHOT_PATH"

echo "✅ Artifacts:"
echo "   $LOG_PATH"
echo "   $SHOT_PATH"
