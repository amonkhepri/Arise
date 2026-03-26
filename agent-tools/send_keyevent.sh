#!/bin/bash

set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

# Pick first connected device
DEVICE=$($ADB devices | awk '/\tdevice$/{print $1; exit}')
if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

if [[ $# -lt 1 ]]; then
  echo "❌ Missing keyevent parameter" >&2
  echo "Usage: $0 KEYCODE" >&2
  exit 1
fi

KEYCODE=$1
echo "⌨️  Sending keyevent: $KEYCODE"
$ADB -s "$DEVICE" shell input keyevent "$KEYCODE"
