#!/bin/bash

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_HOME="$TMP_DIR/home"
ADB_DIR="$FAKE_HOME/Library/Android/sdk/platform-tools"
ADB_LOG="$TMP_DIR/adb.log"
SEQUENCE_FILE="$TMP_DIR/sequence.txt"
OUTPUT_FILE="$TMP_DIR/output.log"
mkdir -p "$ADB_DIR"

cat > "$ADB_DIR/adb" <<'EOF'
#!/bin/bash
set -euo pipefail

if [[ ${1:-} == "devices" ]]; then
  printf 'List of devices attached\nemulator-5554\tdevice\n'
  exit 0
fi

printf '%s\n' "$*" >> "$ADB_LOG"

# Simulate adb consuming stdin from the parent process. click.sh must shield its
# sequence reader from this or later commands will disappear.
cat >/dev/null || true
EOF
chmod +x "$ADB_DIR/adb"

cat > "$SEQUENCE_FILE" <<'EOF'
tap 10 20
text hello
key 66
EOF

HOME="$FAKE_HOME" ADB_LOG="$ADB_LOG" "$ROOT_DIR/agent-tools/click.sh" --file "$SEQUENCE_FILE" >"$OUTPUT_FILE" 2>&1

invocations=()
while IFS= read -r line || [[ -n "$line" ]]; do
  invocations+=("$line")
done < "$ADB_LOG"

if [[ ${#invocations[@]} -ne 3 ]]; then
  echo "Expected 3 scripted adb invocations, saw ${#invocations[@]}" >&2
  cat "$OUTPUT_FILE" >&2
  exit 1
fi

[[ ${invocations[0]} == "-s emulator-5554 shell input tap 10 20" ]] || {
  echo "Unexpected first invocation: ${invocations[0]}" >&2
  exit 1
}
[[ ${invocations[1]} == "-s emulator-5554 shell input text hello" ]] || {
  echo "Unexpected second invocation: ${invocations[1]}" >&2
  exit 1
}
[[ ${invocations[2]} == "-s emulator-5554 shell input keyevent 66" ]] || {
  echo "Unexpected third invocation: ${invocations[2]}" >&2
  exit 1
}

echo "click.sh file-mode sequence execution passed"
