#!/usr/bin/env bash

set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APK_URL="${BRIAR_APK_URL:-https://briarproject.org/apk/briar.apk}"
APK_PATH_DEFAULT="${XDG_CACHE_HOME:-$HOME/.cache}/arise/briar/briar.apk"
APK_PATH="$APK_PATH_DEFAULT"
DEVICE="${ANDROID_SERIAL:-}"
DOWNLOAD_ONLY=0
SKIP_DOWNLOAD=0
LAUNCH_AFTER_INSTALL=0

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/install-briar-direct.sh [--device SERIAL] [--apk-path PATH] [--apk-url URL] [--download-only] [--skip-download] [--launch]

Download the official Briar direct-download APK and install it on an emulator/device.

Options:
  --device SERIAL     Use a specific adb device serial.
  --apk-path PATH     Where to store/read the APK (default: $XDG_CACHE_HOME/arise/briar/briar.apk).
  --apk-url URL       Override the Briar direct-download URL.
  --download-only     Only download the APK; do not install it.
  --skip-download     Reuse the APK already present at --apk-path.
  --launch            Launch Briar after a successful install.
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
    --apk-path)
      APK_PATH="$2"
      shift 2
      ;;
    --apk-url)
      APK_URL="$2"
      shift 2
      ;;
    --download-only)
      DOWNLOAD_ONLY=1
      shift
      ;;
    --skip-download)
      SKIP_DOWNLOAD=1
      shift
      ;;
    --launch)
      LAUNCH_AFTER_INSTALL=1
      shift
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

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required to download Briar" >&2
  exit 1
fi

mkdir -p "$(dirname "$APK_PATH")"

if [[ "$SKIP_DOWNLOAD" -eq 0 ]]; then
  echo "downloading Briar APK from $APK_URL" >&2
  curl --fail --location --silent --show-error \
    --output "$APK_PATH" \
    "$APK_URL"
elif [[ ! -f "$APK_PATH" ]]; then
  echo "--skip-download was set but APK does not exist at $APK_PATH" >&2
  exit 1
fi

if [[ "$DOWNLOAD_ONLY" -eq 1 ]]; then
  printf '%s\n' "$APK_PATH"
  exit 0
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "no connected device/emulator detected" >&2
  exit 1
fi

echo "installing Briar APK on $DEVICE" >&2
"$ADB" -s "$DEVICE" install -r -d "$APK_PATH" >&2

installed_packages="$("$ADB" -s "$DEVICE" shell pm list packages | tr -d '\r')"
briar_package="$(printf '%s\n' "$installed_packages" | awk -F: '/briarproject/ {print $2; exit}')"

if [[ -z "$briar_package" ]]; then
  echo "Briar installed, but package name could not be resolved from pm list packages" >&2
else
  echo "installed package: $briar_package" >&2
fi

if [[ "$LAUNCH_AFTER_INSTALL" -eq 1 && -n "$briar_package" ]]; then
  echo "launching $briar_package on $DEVICE" >&2
  "$ADB" -s "$DEVICE" shell monkey -p "$briar_package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
fi

printf '%s\n' "${briar_package:-unknown}"
