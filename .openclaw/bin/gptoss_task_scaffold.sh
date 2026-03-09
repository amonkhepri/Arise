#!/bin/bash

set -euo pipefail

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <task-id>" >&2
  exit 1
fi

task_id="$1"

case "$task_id" in
  AT-001)
    cat >"$REPO_DIR/agent-tools/open-deep-link.sh" <<'EOF'
#!/bin/bash

set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/open-deep-link.sh [--device SERIAL] URI

Open an Android VIEW intent for the supplied URI on a connected device.

Options:
  --device SERIAL  Use a specific device or emulator serial.
  --help, -h       Show this help message.
USAGE
}

pick_device() {
  "$ADB" devices | awk '/\tdevice$/{print $1; exit}'
}

DEVICE="${ANDROID_SERIAL:-}"
URI=""

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
    --*)
      echo "❌ Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -n "$URI" ]]; then
        echo "❌ Provide exactly one URI" >&2
        exit 1
      fi
      URI="$1"
      shift
      ;;
  esac
done

if [[ -z "$URI" ]]; then
  echo "❌ Missing URI" >&2
  show_help
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

echo "🔗 device $DEVICE"
echo "🔗 uri $URI"
"$ADB" -s "$DEVICE" shell am start -a android.intent.action.VIEW -d "$URI"
EOF
    chmod +x "$REPO_DIR/agent-tools/open-deep-link.sh"
    ;;
  AT-002)
    cat >"$REPO_DIR/agent-tools/invitation-link-smoke.sh" <<'EOF'
#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OPEN_LINK="$SCRIPT_DIR/open-deep-link.sh"

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/invitation-link-smoke.sh [--device SERIAL] INVITATION_URI

Launch an invitation link on a connected device and print a short manual checklist.

Options:
  --device SERIAL  Use a specific device or emulator serial.
  --help, -h       Show this help message.
USAGE
}

DEVICE_ARGS=()
URI=""

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
      DEVICE_ARGS=(--device "$2")
      shift 2
      ;;
    --*)
      echo "❌ Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -n "$URI" ]]; then
        echo "❌ Provide exactly one invitation URI" >&2
        exit 1
      fi
      URI="$1"
      shift
      ;;
  esac
done

if [[ -z "$URI" ]]; then
  echo "❌ Missing invitation URI" >&2
  show_help
  exit 1
fi

if [[ ! -x "$OPEN_LINK" ]]; then
  echo "❌ Missing helper: $OPEN_LINK" >&2
  exit 1
fi

"$OPEN_LINK" "${DEVICE_ARGS[@]}" "$URI"

cat <<'CHECKLIST'
Manual check:
1. Invitation onboarding opens from the link.
2. The invited contact resolves correctly.
3. Chat opens for that contact.
4. The first message can be attempted from the opened chat.
CHECKLIST
EOF
    chmod +x "$REPO_DIR/agent-tools/invitation-link-smoke.sh"
    ;;
  AT-003)
    python3 - <<'PY'
from pathlib import Path

path = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/agent-tools/QUICK_TEST_GUIDE.md")
text = path.read_text()
marker = "## Invitation Link Smoke"
if marker not in text:
    addition = """

## Invitation Link Smoke

Use `./agent-tools/invitation-link-smoke.sh '<invitation-uri>'` to open a Briar invitation link on a connected device. Add `--device SERIAL` when you need to target a specific phone or emulator.

Expected checks:
- Invitation onboarding opens from the link.
- The invited contact resolves correctly.
- Chat opens for that contact.
- The first message can be attempted from the opened chat.
""".rstrip() + "\n"
    path.write_text(text.rstrip() + addition)
PY
    ;;
  AT-004)
    python3 - <<'PY'
from pathlib import Path

path = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/docs/briar_invitation_chat_roadmap.md")
text = path.read_text()
old = "- [ ] Add a reusable QA script under `agent-tools/` for invitation-link happy-path smoke testing."
new = "- [x] Add a reusable QA script under `agent-tools/` for invitation-link happy-path smoke testing."
if old in text:
    text = text.replace(old, new, 1)
entry = "- 2026-03-06: Added `agent-tools/invitation-link-smoke.sh` and a matching quick-guide section so invitation-link smoke checks can be launched consistently from local agent tools."
if entry not in text:
    marker = "## Progress Log\n"
    text = text.replace(marker, marker + entry + "\n", 1)
path.write_text(text)
PY
    ;;
  AT-005)
    cat >"$REPO_DIR/agent-tools/invitation-link-logcat.sh" <<'EOF'
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

FILTER='BriarInvitation|Invitation|invite|TransportRouter|IdentityRegistry|BriarContact|onboarding|AndroidRuntime'
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
EOF
    chmod +x "$REPO_DIR/agent-tools/invitation-link-logcat.sh"
    ;;
  AT-006)
    python3 - <<'PY'
from pathlib import Path

path = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/agent-tools/QUICK_TEST_GUIDE.md")
text = path.read_text()
marker = "## Invitation Link Logs"
if marker not in text:
    anchor = "- The first message can be attempted from the opened chat.\n"
    addition = """

## Invitation Link Logs

Use `./agent-tools/invitation-link-logcat.sh --clear` before a fresh smoke run, then `./agent-tools/invitation-link-logcat.sh [--device SERIAL]` to dump invitation-focused logs without searching full logcat output.
""".rstrip() + "\n"
    if anchor in text:
        text = text.replace(anchor, anchor + addition, 1)
    else:
        text = text.rstrip() + addition
    path.write_text(text)
PY
    ;;
  AT-007)
    cat >"$REPO_DIR/agent-tools/invitation-link-capture.sh" <<'EOF'
#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
SMOKE_HELPER="$SCRIPT_DIR/invitation-link-smoke.sh"
LOGCAT_HELPER="$SCRIPT_DIR/invitation-link-logcat.sh"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/invitation-link-capture.sh [--device SERIAL] INVITATION_URI

Launch an invitation link, save filtered invitation logs, and capture a screenshot.

Options:
  --device SERIAL  Use a specific device or emulator serial.
  --help, -h       Show this help message.
USAGE
}

pick_device() {
  "$ADB" devices | awk '/\tdevice$/{print $1; exit}'
}

DEVICE="${ANDROID_SERIAL:-}"
URI=""

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
    --*)
      echo "❌ Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -n "$URI" ]]; then
        echo "❌ Provide exactly one invitation URI" >&2
        exit 1
      fi
      URI="$1"
      shift
      ;;
  esac
done

if [[ -z "$URI" ]]; then
  echo "❌ Missing invitation URI" >&2
  show_help
  exit 1
fi

if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

if [[ ! -x "$SMOKE_HELPER" || ! -x "$LOGCAT_HELPER" ]]; then
  echo "❌ Missing invitation helper dependency" >&2
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(pick_device)"
fi

if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

mkdir -p "$SCREENSHOT_DIR"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_PATH="$SCREENSHOT_DIR/${TIMESTAMP}-invitation-logcat.txt"
SHOT_PATH="$SCREENSHOT_DIR/${TIMESTAMP}-invitation.png"

echo "🔗 Launching invitation smoke on $DEVICE"
"$SMOKE_HELPER" --device "$DEVICE" "$URI"

echo "📝 Saving invitation logs to $LOG_PATH"
"$LOGCAT_HELPER" --device "$DEVICE" >"$LOG_PATH"

echo "📸 Saving screenshot to $SHOT_PATH"
"$ADB" -s "$DEVICE" exec-out screencap -p >"$SHOT_PATH"

echo "✅ Artifacts:"
echo "   $LOG_PATH"
echo "   $SHOT_PATH"
EOF
    chmod +x "$REPO_DIR/agent-tools/invitation-link-capture.sh"
    ;;
  AT-008)
    python3 - <<'PY'
from pathlib import Path

path = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/agent-tools/QUICK_TEST_GUIDE.md")
text = path.read_text()
marker = "## Invitation Capture Bundle"
if marker not in text:
    anchor = "Use `./agent-tools/invitation-link-logcat.sh --clear` before a fresh smoke run, then `./agent-tools/invitation-link-logcat.sh [--device SERIAL]` to dump invitation-focused logs without searching full logcat output.\n"
    addition = """

## Invitation Capture Bundle

Use `./agent-tools/invitation-link-capture.sh '<invitation-uri>'` to run the smoke helper, save filtered invitation logs, and capture a screenshot under `agent-tools/screenshots/` in one pass. Add `--device SERIAL` to target a specific device.
""".rstrip() + "\n"
    if anchor in text:
        text = text.replace(anchor, anchor + addition, 1)
    else:
        text = text.rstrip() + addition
    path.write_text(text)
PY
    ;;
  AT-009)
    python3 - <<'PY'
from pathlib import Path

path = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/docs/briar_invitation_chat_roadmap.md")
text = path.read_text()
entry = "- 2026-03-06: Added `agent-tools/invitation-link-logcat.sh` and `agent-tools/invitation-link-capture.sh` so invitation smoke runs can collect filtered logs and screenshots without ad-hoc adb commands."
if entry not in text:
    marker = "## Progress Log\n"
    text = text.replace(marker, marker + entry + "\n", 1)
    path.write_text(text)
PY
    ;;
  *)
    echo "unknown task id: $task_id" >&2
    exit 1
    ;;
esac

printf 'scaffold_status: ok\n'
printf 'scaffold_task: %s\n' "$task_id"
printf 'next_step: run listed validations and finalize\n'
