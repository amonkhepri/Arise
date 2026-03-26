#!/bin/bash

set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/invitation-link-logcat.sh [--device SERIAL] [--clear]

Print filtered invitation-link logcat lines for a connected device.

Options:
  --device SERIAL  Use a specific device or emulator serial.
  --clear          Clear logcat on the selected device and exit.
  --help, -h       Show this help message.
USAGE
}

pick_device() {
  "$ADB" devices | awk '/\tdevice$/{print $1; exit}'
}

DEVICE="${ANDROID_SERIAL:-}"
CLEAR_LOGS=0

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
    --clear)
      CLEAR_LOGS=1
      shift
      ;;
    --*)
      echo "❌ Unknown option: $1" >&2
      exit 1
      ;;
    *)
      echo "❌ Unexpected argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

if [[ "$CLEAR_LOGS" -eq 1 ]]; then
  echo "🧹 Clearing logcat on $DEVICE"
  "$ADB" -s "$DEVICE" logcat -c
  exit 0
fi

FILTER='SplashActivity|SignInActivity|MainActivity|BriarInvitation|Invitation|invite|TransportRouter|IdentityRegistry|BriarContact|RealBriarContactService|BriarPeopleSync|BriarRuntimeManager|BriarRuntimeHandle|TransportBridge|CompositeAuthState|RuntimeBriarAccountRepo|onboarding|AndroidRuntime'
echo "📝 device $DEVICE"
matches="$(
  "$ADB" -s "$DEVICE" logcat -d -v time 2>&1 \
    | grep -iE "$FILTER" \
    | tail -150 || true
)"

if [[ -z "$matches" ]]; then
  echo "ℹ️ No invitation-related log lines matched."
  exit 0
fi

printf '%s\n' "$matches"
