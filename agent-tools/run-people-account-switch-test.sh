#!/bin/bash
# Run the People conversation account switch instrumentation test.
# Usage:
#   ./agent-tools/run-people-account-switch-test.sh \
#     --primary-email you@example.com \
#     --primary-password Hunter2 \
#     --secondary-email other@example.com \
#     --secondary-password Secret!

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
SCRIPT_NAME="$(basename "$0")"

PRIMARY_EMAIL=""
PRIMARY_PASSWORD=""
SECONDARY_EMAIL=""
SECONDARY_PASSWORD=""

usage() {
  cat <<EOF
Usage: $SCRIPT_NAME --primary-email EMAIL --primary-password PASSWORD \\
         --secondary-email EMAIL --secondary-password PASSWORD

All parameters are optional. When omitted, the test falls back to the default
credentials baked into PeopleConversationAccountSwitchTest.
EOF
}

while (($#)); do
  case "$1" in
    --primary-email)
      PRIMARY_EMAIL="$2"
      shift 2
      ;;
    --primary-password)
      PRIMARY_PASSWORD="$2"
      shift 2
      ;;
    --secondary-email)
      SECONDARY_EMAIL="$2"
      shift 2
      ;;
    --secondary-password)
      SECONDARY_PASSWORD="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

ADB_BIN="$(command -v adb || true)"
if [ -z "$ADB_BIN" ]; then
  echo "❌ adb not found on PATH." >&2
  exit 1
fi

DEVICE_LINE="$("$ADB_BIN" devices | grep -v "List" | grep "device$" || true)"
if [ -z "$DEVICE_LINE" ]; then
  echo "❌ No connected Android device or emulator detected." >&2
  exit 1
fi

echo "📱 Using device: $(echo "$DEVICE_LINE" | awk '{print $1}')"

if [ -z "$PRIMARY_EMAIL" ] || [ -z "$PRIMARY_PASSWORD" ] || [ -z "$SECONDARY_EMAIL" ] || [ -z "$SECONDARY_PASSWORD" ]; then
  cat <<EOF
ℹ️  Credential flags were not provided; the test will rely on the default
    sign-in accounts baked into PeopleConversationAccountSwitchTest.
    Pass --primary/--secondary flags to override the defaults when needed.
EOF
fi

GRADLE_ARGS=(
  "--no-daemon"
  "connectedDebugAndroidTest"
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.rise.ui.dashboardNavigation.people.PeopleConversationAccountSwitchTest"
)

if [ -n "$PRIMARY_EMAIL" ]; then
  GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.primaryEmail=$PRIMARY_EMAIL")
fi
if [ -n "$PRIMARY_PASSWORD" ]; then
  GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.primaryPassword=$PRIMARY_PASSWORD")
fi
if [ -n "$SECONDARY_EMAIL" ]; then
  GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.secondaryEmail=$SECONDARY_EMAIL")
fi
if [ -n "$SECONDARY_PASSWORD" ]; then
  GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.secondaryPassword=$SECONDARY_PASSWORD")
fi

echo "🚀 Running PeopleConversationAccountSwitchTest via Gradle..."
cd "$ROOT_DIR"
./gradlew "${GRADLE_ARGS[@]}"
