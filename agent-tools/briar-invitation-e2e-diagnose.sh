#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
INSTALL_HELPER="$ROOT_DIR/agent-tools/install.sh"
PACKAGE="com.example.rise"
DEVICE="${ANDROID_SERIAL:-}"
SETTLE_SECONDS=6
SKIP_INSTALL=0
URI=""

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/briar-invitation-e2e-diagnose.sh [--device SERIAL] [--skip-install] [--package NAME] [--settle-seconds N] INVITATION_URI

Launch a Briar invitation on a device, inspect the outcome, and emit a JSON diagnosis.

Options:
  --device SERIAL      Use a specific device or emulator serial.
  --skip-install       Do not rebuild/reinstall before running the diagnosis.
  --package NAME       Target package for the VIEW intent. Default: com.example.rise
  --settle-seconds N   Seconds to wait after launch before inspecting logs/UI. Default: 6
  --help, -h           Show this help message.
USAGE
}

log() {
  printf '%s\n' "$*" >&2
}

pick_device() {
  "$ADB" devices | awk '/\tdevice$/{print $1; exit}'
}

emit_json() {
  local result="$1"
  local bug_code="${2:-}"
  local summary="${3:-}"
  local launch_output="${4:-}"
  local resumed_activity="${5:-}"
  local log_excerpt="${6:-}"

  RESULT="$result" \
  BUG_CODE="$bug_code" \
  SUMMARY="$summary" \
  DEVICE_VALUE="$DEVICE" \
  URI_VALUE="$URI" \
  PACKAGE_VALUE="$PACKAGE" \
  LAUNCH_OUTPUT_VALUE="$launch_output" \
  RESUMED_ACTIVITY_VALUE="$resumed_activity" \
  LOG_EXCERPT_VALUE="$log_excerpt" \
    python3 - <<'PY'
import json
import os

payload = {
    "result": os.environ["RESULT"],
    "device": os.environ.get("DEVICE_VALUE", ""),
    "uri": os.environ.get("URI_VALUE", ""),
    "package": os.environ.get("PACKAGE_VALUE", ""),
}

optional_fields = {
    "bugCode": os.environ.get("BUG_CODE", ""),
    "summary": os.environ.get("SUMMARY", ""),
    "launchOutput": os.environ.get("LAUNCH_OUTPUT_VALUE", ""),
    "resumedActivity": os.environ.get("RESUMED_ACTIVITY_VALUE", ""),
    "logExcerpt": os.environ.get("LOG_EXCERPT_VALUE", ""),
}
for key, value in optional_fields.items():
    if value:
        payload[key] = value

print(json.dumps(payload))
PY
}

filter_logs() {
  local raw_logs="$1"
  printf '%s\n' "$raw_logs" | grep -iE 'MainActivity|BriarInvitation|BriarContactRepository|onboarding|AndroidRuntime|IllegalStateException|Briar runtime is not ready' | tail -120 || true
}

read_resumed_activity() {
  "$ADB" -s "$DEVICE" shell dumpsys activity activities 2>/dev/null | grep -m1 -E 'mResumedActivity|ResumedActivity' || true
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --device)
      if [[ $# -lt 2 ]]; then
        log "Missing serial after --device"
        exit 1
      fi
      DEVICE="$2"
      shift 2
      ;;
    --skip-install)
      SKIP_INSTALL=1
      shift
      ;;
    --package)
      if [[ $# -lt 2 ]]; then
        log "Missing package name after --package"
        exit 1
      fi
      PACKAGE="$2"
      shift 2
      ;;
    --settle-seconds)
      if [[ $# -lt 2 ]]; then
        log "Missing value after --settle-seconds"
        exit 1
      fi
      SETTLE_SECONDS="$2"
      shift 2
      ;;
    --*)
      log "Unknown option: $1"
      exit 1
      ;;
    *)
      if [[ -n "$URI" ]]; then
        log "Provide exactly one invitation URI"
        exit 1
      fi
      URI="$1"
      shift
      ;;
  esac
done

if [[ -z "$URI" ]]; then
  log "Missing invitation URI"
  show_help >&2
  exit 1
fi

if [[ ! -x "$ADB" ]]; then
  log "adb not found at $ADB"
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  log "No connected device/emulator detected"
  exit 1
fi

if [[ "$SKIP_INSTALL" -eq 0 ]]; then
  if [[ ! -x "$INSTALL_HELPER" ]]; then
    log "Missing install helper: $INSTALL_HELPER"
    exit 1
  fi
  log "Installing latest debug build on $DEVICE"
  "$INSTALL_HELPER" >&2
fi

log "Diagnosing external invitation on $DEVICE"
"$ADB" -s "$DEVICE" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
"$ADB" -s "$DEVICE" logcat -c >/dev/null 2>&1 || true

launch_output="$("$ADB" -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "$URI" "$PACKAGE" 2>&1 || true)"
sleep "$SETTLE_SECONDS"

full_logs="$("$ADB" -s "$DEVICE" logcat -d -v time 2>&1 || true)"
log_excerpt="$(filter_logs "$full_logs")"
resumed_activity="$(read_resumed_activity)"

if grep -qi "unable to resolve Intent" <<<"$launch_output"; then
  emit_json \
    "bug" \
    "raw_briar_view_intent_unresolved" \
    "The app build on device does not resolve the raw briar VIEW intent." \
    "$launch_output" \
    "$resumed_activity" \
    "$log_excerpt"
  exit 0
fi

if grep -q "Briar runtime is not ready" <<<"$full_logs"; then
  emit_json \
    "bug" \
    "briar_runtime_not_ready_on_external_invitation" \
    "Invitation onboarding fails because Briar runtime is not ready." \
    "$launch_output" \
    "$resumed_activity" \
    "$log_excerpt"
  exit 0
fi

if grep -q "Failed to accept Briar invitation" <<<"$log_excerpt"; then
  emit_json \
    "bug" \
    "external_invitation_onboarding_failed" \
    "External Briar invitation onboarding failed before chat opened." \
    "$launch_output" \
    "$resumed_activity" \
    "$log_excerpt"
  exit 0
fi

if grep -q "ChatActivity" <<<"$resumed_activity"; then
  emit_json \
    "pass" \
    "" \
    "Chat opened from the external Briar invitation." \
    "$launch_output" \
    "$resumed_activity" \
    "$log_excerpt"
  exit 0
fi

emit_json \
  "bug" \
  "external_invitation_onboarding_did_not_reach_chat" \
  "External Briar invitation launch did not reach chat and no explicit acceptance failure was logged." \
  "$launch_output" \
  "$resumed_activity" \
  "$log_excerpt"
