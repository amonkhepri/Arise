#!/usr/bin/env bash

set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
DEVICE="${ANDROID_SERIAL:-}"
PACKAGE="org.briarproject.briar.android"
LAUNCH=0
WAIT_SECONDS=2

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/briar-peer-state.sh [--device SERIAL] [--launch] [--wait-seconds N]

Inspect whether the official Briar app is installed on the target device and
classify its visible state from top activity plus UI dump text.

Options:
  --device SERIAL     Use a specific adb device or emulator serial.
  --launch            Launch Briar before classifying state.
  --wait-seconds N    Seconds to wait after launch before inspection (default: 2).
  --help, -h          Show this help message.
USAGE
}

pick_device() {
  "$ADB" devices | awk '/\tdevice$/{print $1; exit}'
}

while (($#)); do
  case "$1" in
    --device)
      DEVICE="$2"
      shift 2
      ;;
    --launch)
      LAUNCH=1
      shift
      ;;
    --wait-seconds)
      WAIT_SECONDS="$2"
      shift 2
      ;;
    --help|-h)
      show_help
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      show_help >&2
      exit 1
      ;;
  esac
done

if [[ ! -x "$ADB" ]]; then
  echo "adb not found at $ADB" >&2
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "no connected device/emulator detected" >&2
  exit 1
fi

packages="$("$ADB" -s "$DEVICE" shell pm list packages | tr -d '\r')"
installed=0
if grep -q "^package:${PACKAGE}$" <<<"$packages"; then
  installed=1
fi

if [[ "$LAUNCH" -eq 1 && "$installed" -eq 1 ]]; then
  "$ADB" -s "$DEVICE" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  sleep "$WAIT_SECONDS"
fi

top_line="$("$ADB" -s "$DEVICE" shell dumpsys activity activities 2>&1 | grep -m 1 -E 'mResumedActivity|mTopResumedActivity|topResumedActivity' || true)"
top_line="$(printf '%s' "$top_line" | tr '\n' ' ' | sed -E 's/[[:space:]]+/ /g; s/^ //; s/ $//')"
top_package=""
if [[ "$top_line" =~ ([A-Za-z0-9._]+)/(.*) ]]; then
  top_package="${BASH_REMATCH[1]}"
fi

ui_xml=""
if [[ "$top_package" == "$PACKAGE" ]]; then
  "$ADB" -s "$DEVICE" shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || true
  ui_xml="$("$ADB" -s "$DEVICE" shell cat /sdcard/window_dump.xml 2>/dev/null | tr -d '\r' || true)"
fi

state="missing"
summary="Briar is not installed on the target device."

if [[ "$installed" -eq 1 ]]; then
  state="installed_background"
  summary="Briar is installed but is not the top activity."
fi

if [[ "$top_package" == "$PACKAGE" ]]; then
  state="foreground_unknown"
  summary="Briar is foregrounded, but its exact screen could not be classified."

  if grep -q "Welcome to Briar" <<<"$ui_xml" && grep -q "Choose your nickname" <<<"$ui_xml"; then
    state="onboarding_nickname"
    summary="Briar is on first-run onboarding and still requires nickname setup."
  elif grep -q "Create a password" <<<"$ui_xml" || grep -q "Choose a password" <<<"$ui_xml"; then
    state="onboarding_password"
    summary="Briar is on first-run onboarding and still requires password setup."
  elif grep -q "Unlock Briar" <<<"$ui_xml" || grep -q "Enter password" <<<"$ui_xml"; then
    state="locked"
    summary="Briar is installed but currently locked behind its password screen."
  elif grep -q "Contacts" <<<"$ui_xml" || grep -q "Blogs" <<<"$ui_xml" || grep -q "Forums" <<<"$ui_xml"; then
    state="ready_home"
    summary="Briar appears configured and is showing its main authenticated UI."
  fi
fi

python3 - "$DEVICE" "$PACKAGE" "$installed" "$top_package" "$state" "$summary" "$top_line" <<'PY'
import json
import sys

device, package, installed, top_package, state, summary, top_activity = sys.argv[1:]

payload = {
    "device": device,
    "package": package,
    "installed": installed == "1",
    "topPackage": top_package or None,
    "state": state,
    "summary": summary,
    "topActivity": top_activity or None,
}

print(json.dumps(payload))
PY
