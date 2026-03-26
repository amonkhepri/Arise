#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

# Pick first connected device in device state
DEVICE=$($ADB devices | awk '/\tdevice$/{print $1; exit}')
if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

# Generate filename with timestamp
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
FILENAME="$SCREENSHOT_DIR/${TIMESTAMP}-run.png"

# Create screenshots directory if it doesn't exist
mkdir -p "$SCREENSHOT_DIR"

echo "📸 Capturing screenshot..."
$ADB -s "$DEVICE" shell screencap -p > "$FILENAME"

if [[ -f "$FILENAME" ]]; then
  echo "✅ Screenshot saved: $FILENAME"
else
  echo "❌ Failed to save screenshot" >&2
  exit 1
fi
