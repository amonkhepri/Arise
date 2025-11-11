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

echo "📋 Dumping UI hierarchy..."
$ADB -s "$DEVICE" shell uiautomator dump

if [[ $# -gt 0 ]]; then
  echo "🔍 Searching for: $1"
  $ADB -s "$DEVICE" shell cat /sdcard/window_dump.xml | grep -i "$1"
else
  $ADB -s "$DEVICE" shell cat /sdcard/window_dump.xml
fi
