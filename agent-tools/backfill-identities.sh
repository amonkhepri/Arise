#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"

echo "🔨 Installing latest debug build..."
(cd "$ROOT_DIR" && ./gradlew installDebug >/tmp/backfill-gradle.log && tail -n 20 /tmp/backfill-gradle.log)

echo "📡 Triggering identity backfill worker via debug broadcast..."
adb shell am broadcast -a com.example.rise.debug.RUN_IDENTITY_BACKFILL

echo "✅ Backfill broadcast sent. Use 'adb logcat | grep IdentityBackfill' to monitor progress."
