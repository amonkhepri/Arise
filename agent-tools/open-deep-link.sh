#!/bin/bash

set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/open-deep-link.sh [--device SERIAL] URI

Open an Android VIEW intent for the supplied URI on a connected device.

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
        echo "❌ Provide exactly one URI" >&2
        exit 1
      fi
      URI="$1"
      shift
      ;;
  esac
done

if [[ -z "$URI" ]]; then
  echo "❌ Missing URI" >&2
  show_help
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

echo "🔗 device $DEVICE"
echo "🔗 uri $URI"
"$ADB" -s "$DEVICE" shell am start -a android.intent.action.VIEW -d "$URI"
