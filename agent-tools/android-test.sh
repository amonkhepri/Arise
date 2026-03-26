#!/bin/bash
# Builds instrumentation (androidTest) APK to surface compilation/migration issues.
# Usage: ./agent-tools/android-test.sh [extra gradle args]

set -euo pipefail

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "🧪 Building androidTest APK..."
./gradlew assembleDebugAndroidTest "$@"
