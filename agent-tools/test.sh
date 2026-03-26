#!/bin/bash
# Run unit tests for Rise app
# Usage: ./agent-tools/test.sh [--twice] [--tests pattern] [--timeout seconds]

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"

RUN_COUNT=1
TEST_FILTER=""
TIMEOUT_SECS=""

while (($#)); do
  case "$1" in
    --twice)
      RUN_COUNT=2
      ;;
    --tests)
      if [ $# -lt 2 ]; then
        echo "Missing value for --tests" >&2
        exit 1
      fi
      TEST_FILTER="$2"
      shift
      ;;
    --timeout)
      if [ $# -lt 2 ]; then
        echo "Missing value for --timeout" >&2
        exit 1
      fi
      TIMEOUT_SECS="$2"
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Usage: $0 [--twice] [--tests pattern] [--timeout seconds]" >&2
      exit 1
      ;;
  esac
  shift
done

JAVA_MAJOR_VERSION=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  JAVA_MAJOR_VERSION="$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
  if [ "${JAVA_MAJOR_VERSION}" = "1" ]; then
    JAVA_MAJOR_VERSION="$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '[\".]' '/version/ {print $3; exit}')"
  fi
fi

if [ -z "${JAVA_HOME:-}" ] || [ "${JAVA_MAJOR_VERSION}" != "17" ]; then
  if [ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  elif [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  fi
fi

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
  if [ -n "${ORG_GRADLE_JAVA_INSTALLATIONS_PATHS:-}" ]; then
    export ORG_GRADLE_JAVA_INSTALLATIONS_PATHS="${JAVA_HOME}:${ORG_GRADLE_JAVA_INSTALLATIONS_PATHS}"
  else
    export ORG_GRADLE_JAVA_INSTALLATIONS_PATHS="${JAVA_HOME}"
  fi
fi

export GRADLE_USER_HOME="${ROOT_DIR}/.gradle"
mkdir -p "${GRADLE_USER_HOME}"

echo "🧪 Running unit tests..."
cd "${ROOT_DIR}"
./gradlew --stop >/dev/null 2>&1 || true
GRADLE_CMD=(./gradlew --no-daemon)
if [ -n "${JAVA_HOME:-}" ]; then
  GRADLE_CMD+=("-Dorg.gradle.java.installations.paths=${JAVA_HOME}")
fi
GRADLE_CMD+=(testDebugUnitTest)
if [ -n "${TEST_FILTER}" ]; then
  GRADLE_CMD+=(--tests "${TEST_FILTER}")
fi

run_gradle() {
  if [ -n "${TIMEOUT_SECS}" ]; then
    if command -v timeout >/dev/null 2>&1; then
      timeout "${TIMEOUT_SECS}" "${GRADLE_CMD[@]}"
    else
      python3 - "${TIMEOUT_SECS}" "${GRADLE_CMD[@]}" <<'PY'
import subprocess
import sys

timeout = float(sys.argv[1])
cmd = sys.argv[2:]

proc = subprocess.Popen(cmd)
try:
    return_code = proc.wait(timeout=timeout)
except subprocess.TimeoutExpired:
    proc.kill()
    proc.wait()
    sys.exit(124)
else:
    sys.exit(return_code)
PY
    fi
  else
    "${GRADLE_CMD[@]}"
  fi
}

for ((run=1; run<=RUN_COUNT; run++)); do
  if (( RUN_COUNT > 1 )); then
    echo "▶️ Run ${run}/${RUN_COUNT}"
  fi
  run_gradle
done
