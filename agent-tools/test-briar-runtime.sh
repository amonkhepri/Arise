#!/bin/bash
# Run unit tests for briar-runtime module
# Usage: ./agent-tools/test-briar-runtime.sh [--tests pattern] [--timeout seconds]

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"

TEST_FILTER=""
TIMEOUT_SECS=""

while (($#)); do
  case "$1" in
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
      echo "Usage: $0 [--tests pattern] [--timeout seconds]" >&2
      exit 1
      ;;
  esac
  shift
done

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

export GRADLE_USER_HOME="${ROOT_DIR}/.gradle"
mkdir -p "${GRADLE_USER_HOME}"

echo "Running briar-runtime unit tests..."
cd "${ROOT_DIR}"
./gradlew --stop >/dev/null 2>&1 || true
GRADLE_CMD=(./gradlew --no-daemon :briar-runtime:test)
if [ -n "${TEST_FILTER}" ]; then
  GRADLE_CMD+=(--tests "${TEST_FILTER}")
fi

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
