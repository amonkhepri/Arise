#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <slug>" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
AGENT_TOOLS_DIR="$ROOT_DIR/agent-tools"
SLUG="$1"

write_script() {
  local target="$1"
  cat > "$target"
  chmod +x "$target"
}

case "$SLUG" in
  latest-prefix)
    write_script "$AGENT_TOOLS_DIR/invitation-link-prefix.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-prefix.sh [--help|-h]

Print the prefix for the latest invitation capture bundle.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-prefix.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" ]]; then
  echo "Failed to resolve latest invitation log path" >&2
  exit 1
fi

BASENAME="$(basename "$LOG_FILE")"
PREFIX="${BASENAME%-invitation-logcat.txt}"

printf 'PREFIX: %s\n' "$PREFIX"
SCRIPT
    ;;
  recent-bundles)
    write_script "$AGENT_TOOLS_DIR/invitation-link-bundles.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
LIMIT=5

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-bundles.sh [--limit N] [--help|-h]

List recent invitation capture bundles from agent-tools/screenshots.

Options:
  --limit N     Show up to N bundles (default: 5).
  --help, -h    Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --limit)
      if [[ $# -lt 2 || ! "$2" =~ ^[0-9]+$ || "$2" -lt 1 ]]; then
        echo "--limit requires a positive integer" >&2
        exit 1
      fi
      LIMIT="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "$SCREENSHOT_DIR" ]]; then
  echo "No screenshot directory found: $SCREENSHOT_DIR" >&2
  exit 1
fi

LOG_PATHS=( "$SCREENSHOT_DIR"/*-invitation-logcat.txt )
if [[ ${#LOG_PATHS[@]} -eq 0 || ! -e "${LOG_PATHS[0]}" ]]; then
  echo "No invitation bundles found in: $SCREENSHOT_DIR" >&2
  exit 1
fi

COUNT=0
while IFS= read -r log_path; do
  prefix="$(basename "$log_path")"
  prefix="${prefix%-invitation-logcat.txt}"
  shot_path="$SCREENSHOT_DIR/${prefix}-invitation.png"
  printf 'PREFIX: %s\n' "$prefix"
  printf 'LOG: %s\n' "$log_path"
  printf 'SCREENSHOT: %s\n' "$shot_path"
  printf '\n'
  COUNT=$((COUNT + 1))
  if [[ "$COUNT" -ge "$LIMIT" ]]; then
    break
  fi
done < <(printf '%s\n' "${LOG_PATHS[@]}" | sort -Vr)
SCRIPT
    ;;
  log-tail)
    write_script "$AGENT_TOOLS_DIR/invitation-link-log-tail.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"
LINES=40

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-log-tail.sh [--lines N] [--help|-h]

Print the tail of the latest invitation capture log.

Options:
  --lines N     Number of lines to print (default: 40).
  --help, -h    Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --lines)
      if [[ $# -lt 2 || ! "$2" =~ ^[0-9]+$ || "$2" -lt 1 ]]; then
        echo "--lines requires a positive integer" >&2
        exit 1
      fi
      LINES="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" ]]; then
  echo "Failed to resolve latest invitation log path" >&2
  exit 1
fi

printf 'LOG: %s\n' "$LOG_FILE"
printf 'TAIL: last %s lines\n' "$LINES"
tail -n "$LINES" "$LOG_FILE"
SCRIPT
    ;;
  log-search)
    write_script "$AGENT_TOOLS_DIR/invitation-link-log-search.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"
IGNORE_CASE=0
PATTERN=""

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-log-search.sh [--ignore-case] PATTERN

Search the latest invitation capture log for a pattern.

Options:
  --ignore-case  Match case-insensitively.
  --help, -h     Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --ignore-case)
      IGNORE_CASE=1
      shift
      ;;
    --*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -n "$PATTERN" ]]; then
        echo "Provide exactly one PATTERN" >&2
        exit 1
      fi
      PATTERN="$1"
      shift
      ;;
  esac
done

if [[ -z "$PATTERN" ]]; then
  echo "Missing PATTERN" >&2
  show_help
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" ]]; then
  echo "Failed to resolve latest invitation log path" >&2
  exit 1
fi

printf 'LOG: %s\n' "$LOG_FILE"
if [[ "$IGNORE_CASE" -eq 1 ]]; then
  MATCHES="$(grep -niE "$PATTERN" "$LOG_FILE" || true)"
else
  MATCHES="$(grep -nE "$PATTERN" "$LOG_FILE" || true)"
fi

if [[ -z "$MATCHES" ]]; then
  echo "No matches found for pattern: $PATTERN" >&2
  exit 1
fi

printf '%s\n' "$MATCHES"
SCRIPT
    ;;
  report)
    write_script "$AGENT_TOOLS_DIR/invitation-link-report.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"
LINES=20

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-report.sh [--lines N] [--help|-h]

Print a Markdown summary for the latest invitation capture bundle.

Options:
  --lines N     Number of log lines to include (default: 20).
  --help, -h    Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --lines)
      if [[ $# -lt 2 || ! "$2" =~ ^[0-9]+$ || "$2" -lt 1 ]]; then
        echo "--lines requires a positive integer" >&2
        exit 1
      fi
      LINES="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""
SCREENSHOT_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
    "SCREENSHOT: "*)
      SCREENSHOT_FILE="${line#SCREENSHOT: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" || -z "$SCREENSHOT_FILE" ]]; then
  echo "Failed to resolve latest invitation artifacts" >&2
  exit 1
fi

PREFIX="$(basename "$LOG_FILE")"
PREFIX="${PREFIX%-invitation-logcat.txt}"

printf '# Invitation Artifact Report\n\n'
printf -- '- Prefix: %s\n' "$PREFIX"
printf -- '- Log: %s\n' "$LOG_FILE"
printf -- '- Screenshot: %s\n\n' "$SCREENSHOT_FILE"
printf '## Log Tail\n'
tail -n "$LINES" "$LOG_FILE"
SCRIPT
    ;;
  json)
    write_script "$AGENT_TOOLS_DIR/invitation-link-json.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-json.sh [--help|-h]

Print JSON metadata for the latest invitation capture bundle.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-json.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""
SCREENSHOT_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
    "SCREENSHOT: "*)
      SCREENSHOT_FILE="${line#SCREENSHOT: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" || -z "$SCREENSHOT_FILE" ]]; then
  echo "Failed to resolve latest invitation artifacts" >&2
  exit 1
fi

PREFIX="$(basename "$LOG_FILE")"
PREFIX="${PREFIX%-invitation-logcat.txt}"
LOG_BYTES="$(wc -c < "$LOG_FILE" | tr -d ' ')"
SHOT_BYTES="$(wc -c < "$SCREENSHOT_FILE" | tr -d ' ')"

printf '{\n'
printf '  "prefix": "%s",\n' "$PREFIX"
printf '  "log": "%s",\n' "$LOG_FILE"
printf '  "screenshot": "%s",\n' "$SCREENSHOT_FILE"
printf '  "logBytes": %s,\n' "$LOG_BYTES"
printf '  "screenshotBytes": %s\n' "$SHOT_BYTES"
printf '}\n'
SCRIPT
    ;;
  bundle-check)
    write_script "$AGENT_TOOLS_DIR/invitation-link-bundle-check.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-bundle-check.sh [--help|-h]

Verify that the latest invitation capture bundle exists and is non-empty.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-bundle-check.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""
SCREENSHOT_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
    "SCREENSHOT: "*)
      SCREENSHOT_FILE="${line#SCREENSHOT: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ ! -s "$LOG_FILE" ]]; then
  echo "Latest invitation log is missing or empty: $LOG_FILE" >&2
  exit 1
fi

if [[ ! -s "$SCREENSHOT_FILE" ]]; then
  echo "Latest invitation screenshot is missing or empty: $SCREENSHOT_FILE" >&2
  exit 1
fi

printf 'result: ok\n'
printf 'log: %s\n' "$LOG_FILE"
printf 'screenshot: %s\n' "$SCREENSHOT_FILE"
printf 'logBytes: %s\n' "$(wc -c < "$LOG_FILE" | tr -d ' ')"
printf 'screenshotBytes: %s\n' "$(wc -c < "$SCREENSHOT_FILE" | tr -d ' ')"
SCRIPT
    ;;
  screenshot-info)
    write_script "$AGENT_TOOLS_DIR/invitation-link-screenshot-info.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-screenshot-info.sh [--help|-h]

Print metadata for the latest invitation capture screenshot.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-screenshot-info.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
SCREENSHOT_FILE=""

while IFS= read -r line; do
  case "$line" in
    "SCREENSHOT: "*)
      SCREENSHOT_FILE="${line#SCREENSHOT: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$SCREENSHOT_FILE" || ! -f "$SCREENSHOT_FILE" ]]; then
  echo "Failed to resolve latest invitation screenshot" >&2
  exit 1
fi

DIMENSIONS="unknown"
if command -v sips >/dev/null 2>&1; then
  WIDTH="$(sips -g pixelWidth "$SCREENSHOT_FILE" 2>/dev/null | awk '/pixelWidth:/{print $2}')"
  HEIGHT="$(sips -g pixelHeight "$SCREENSHOT_FILE" 2>/dev/null | awk '/pixelHeight:/{print $2}')"
  if [[ -n "$WIDTH" && -n "$HEIGHT" ]]; then
    DIMENSIONS="${WIDTH}x${HEIGHT}"
  fi
fi

printf 'SCREENSHOT: %s\n' "$SCREENSHOT_FILE"
printf 'BYTES: %s\n' "$(wc -c < "$SCREENSHOT_FILE" | tr -d ' ')"
printf 'DIMENSIONS: %s\n' "$DIMENSIONS"
SCRIPT
    ;;
  log-stats)
    write_script "$AGENT_TOOLS_DIR/invitation-link-log-stats.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-log-stats.sh [--help|-h]

Print summary statistics for the latest invitation capture log.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-log-stats.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" || ! -f "$LOG_FILE" ]]; then
  echo "Failed to resolve latest invitation log" >&2
  exit 1
fi

printf 'LOG: %s\n' "$LOG_FILE"
printf 'BYTES: %s\n' "$(wc -c < "$LOG_FILE" | tr -d ' ')"
printf 'LINES: %s\n' "$(wc -l < "$LOG_FILE" | tr -d ' ')"
printf 'FIRST: %s\n' "$(head -n 1 "$LOG_FILE")"
printf 'LAST: %s\n' "$(tail -n 1 "$LOG_FILE")"
SCRIPT
    ;;
  bundle-age)
    write_script "$AGENT_TOOLS_DIR/invitation-link-bundle-age.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-bundle-age.sh [--help|-h]

Print the age of the latest invitation capture bundle.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-bundle-age.sh [--help|-h]" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" ]]; then
  echo "Failed to resolve latest invitation log path" >&2
  exit 1
fi

PREFIX="$(basename "$LOG_FILE")"
PREFIX="${PREFIX%-invitation-logcat.txt}"
CAPTURE_EPOCH="$(date -j -f '%Y%m%d-%H%M%S' "$PREFIX" '+%s' 2>/dev/null || true)"
if [[ -z "$CAPTURE_EPOCH" ]]; then
  echo "Failed to parse capture prefix: $PREFIX" >&2
  exit 1
fi
NOW_EPOCH="$(date '+%s')"
AGE_SECONDS=$((NOW_EPOCH - CAPTURE_EPOCH))

printf 'PREFIX: %s\n' "$PREFIX"
printf 'CAPTURED_AT: %s\n' "$PREFIX"
printf 'AGE_SECONDS: %s\n' "$AGE_SECONDS"
SCRIPT
    ;;
  prune-preview)
    write_script "$AGENT_TOOLS_DIR/invitation-link-prune-preview.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
KEEP=5

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-prune-preview.sh [--keep N] [--help|-h]

List invitation capture bundles that would be pruned while keeping the latest N.

Options:
  --keep N      Keep the latest N bundles (default: 5).
  --help, -h    Show this help message.
USAGE
}

while (( $# )); do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --keep)
      if [[ $# -lt 2 || ! "$2" =~ ^[0-9]+$ || "$2" -lt 1 ]]; then
        echo "--keep requires a positive integer" >&2
        exit 1
      fi
      KEEP="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

LOG_PATHS=( "$SCREENSHOT_DIR"/*-invitation-logcat.txt )
if [[ ${#LOG_PATHS[@]} -eq 0 || ! -e "${LOG_PATHS[0]}" ]]; then
  echo "No invitation bundles found in: $SCREENSHOT_DIR" >&2
  exit 1
fi

INDEX=0
FOUND=0
while IFS= read -r log_path; do
  INDEX=$((INDEX + 1))
  if [[ "$INDEX" -le "$KEEP" ]]; then
    continue
  fi
  prefix="$(basename "$log_path")"
  prefix="${prefix%-invitation-logcat.txt}"
  printf 'PRUNE_PREFIX: %s\n' "$prefix"
  printf 'LOG: %s\n' "$log_path"
  printf 'SCREENSHOT: %s\n' "$SCREENSHOT_DIR/${prefix}-invitation.png"
  printf '\n'
  FOUND=1
done < <(printf '%s\n' "${LOG_PATHS[@]}" | sort -Vr)

if [[ "$FOUND" -eq 0 ]]; then
  printf 'result: none\n'
  printf 'message: no bundles would be pruned while keeping %s\n' "$KEEP"
fi
SCRIPT
    ;;
  open-bundle)
    write_script "$AGENT_TOOLS_DIR/invitation-link-open.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-open.sh [--help|-h]

Open the latest invitation capture log and screenshot with macOS `open`.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-open.sh [--help|-h]" >&2
  exit 1
fi

if ! command -v open >/dev/null 2>&1; then
  echo "macOS open command not available" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
LOG_FILE=""
SCREENSHOT_FILE=""

while IFS= read -r line; do
  case "$line" in
    "LOG: "*)
      LOG_FILE="${line#LOG: }"
      ;;
    "SCREENSHOT: "*)
      SCREENSHOT_FILE="${line#SCREENSHOT: }"
      ;;
  esac
done <<< "$ARTIFACT_OUTPUT"

if [[ -z "$LOG_FILE" || -z "$SCREENSHOT_FILE" ]]; then
  echo "Failed to resolve latest invitation artifacts" >&2
  exit 1
fi

printf 'OPENING_LOG: %s\n' "$LOG_FILE"
printf 'OPENING_SCREENSHOT: %s\n' "$SCREENSHOT_FILE"
open "$LOG_FILE" "$SCREENSHOT_FILE"
SCRIPT
    ;;
  copy-paths)
    write_script "$AGENT_TOOLS_DIR/invitation-link-copy-paths.sh" <<'SCRIPT'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_SCRIPT="$SCRIPT_DIR/invitation-link-artifacts.sh"

show_help() {
  cat <<'USAGE'
Usage: ./agent-tools/invitation-link-copy-paths.sh [--help|-h]

Copy the latest invitation artifact paths to the macOS clipboard.

Options:
  --help, -h   Show this help message.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./agent-tools/invitation-link-copy-paths.sh [--help|-h]" >&2
  exit 1
fi

if ! command -v pbcopy >/dev/null 2>&1; then
  echo "pbcopy not available" >&2
  exit 1
fi

ARTIFACT_OUTPUT="$($ARTIFACTS_SCRIPT)"
printf '%s\n' "$ARTIFACT_OUTPUT" | pbcopy
printf 'COPIED: latest invitation artifact paths\n'
SCRIPT
    ;;
  *)
    echo "unknown helper slug: $SLUG" >&2
    exit 1
    ;;
esac
