#!/bin/bash

set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  echo "❌ adb not found at $ADB" >&2
  exit 1
fi

# Pick first connected device in device state
DEVICE=$($ADB devices | awk '/\tdevice$/{print $1; exit}')
if [[ -z "$DEVICE" ]]; then
  echo "❌ No connected device/emulator detected" >&2
  exit 1
fi

tap() {
  local x=$1
  local y=$2
  echo "👉 tap $x $y"
  $ADB -s "$DEVICE" shell input tap "$x" "$y"
}

sleep_cmd() {
  local duration=$1
  echo "⏱  sleep $duration"
  sleep "$duration"
}

swipe() {
  local x1=$1 y1=$2 x2=$3 y2=$4 duration=${5:-200}
  echo "👉 swipe $x1,$y1 -> $x2,$y2 duration ${duration}ms"
  $ADB -s "$DEVICE" shell input swipe "$x1" "$y1" "$x2" "$y2" "$duration"
}

text_cmd() {
  local text="$1"
  echo "⌨️  text $text"
  $ADB -s "$DEVICE" shell input text "$text"
}

keyevent_cmd() {
  local key="$1"
  echo "⌨️  keyevent $key"
  $ADB -s "$DEVICE" shell input keyevent "$key"
}

show_help() {
  cat <<'USAGE'
Usage:
  ./agent-tools/click.sh x y [x y ...]
  ./agent-tools/click.sh --file agent-tools/sequence.txt
  ./agent-tools/click.sh --key KEYCODE
  ./agent-tools/click.sh --swipe x1 y1 x2 y2 [duration_ms]

Coordinates are in device pixels.
File format supports commands per line:
  tap x y
  swipe x1 y1 x2 y2 [duration_ms]
  sleep seconds
  text your_text_here
  key KEYCODE
Lines starting with # are ignored.
USAGE
}

if [[ $# -eq 0 ]]; then
  show_help
  exit 0
fi

if [[ $1 == "--help" || $1 == "-h" ]]; then
  show_help
  exit 0
fi

if [[ $1 == "--file" ]]; then
  if [[ $# -lt 2 ]]; then
    echo "❌ Missing file path" >&2
    exit 1
  fi
  file="$2"
  if [[ ! -f "$file" ]]; then
    echo "❌ File not found: $file" >&2
    exit 1
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"      # strip comments
    read -ra parts <<< "$line"
    [[ ${#parts[@]} -eq 0 ]] && continue
    cmd=${parts[0],,}
    case "$cmd" in
      tap)
        [[ ${#parts[@]} -eq 3 ]] || { echo "⚠️  tap requires x y" >&2; continue; }
        tap "${parts[1]}" "${parts[2]}"
        ;;
      swipe)
        if [[ ${#parts[@]} -lt 5 ]]; then
          echo "⚠️  swipe requires x1 y1 x2 y2 [duration]" >&2
          continue
        fi
        swipe "${parts[1]}" "${parts[2]}" "${parts[3]}" "${parts[4]}" "${parts[5]:-200}"
        ;;
      sleep)
        [[ ${#parts[@]} -eq 2 ]] || { echo "⚠️  sleep requires seconds" >&2; continue; }
        sleep_cmd "${parts[1]}"
        ;;
      text)
        [[ ${#parts[@]} -ge 2 ]] || { echo "⚠️  text requires content" >&2; continue; }
        text_cmd "${line#* }"
        ;;
      key)
        [[ ${#parts[@]} -eq 2 ]] || { echo "⚠️  key requires KEYCODE" >&2; continue; }
        keyevent_cmd "${parts[1]}"
        ;;
      *)
        echo "⚠️  Unknown command: $cmd" >&2
        ;;
    esac
  done < "$file"
  exit 0
fi

if [[ $1 == "--key" ]]; then
  if [[ $# -lt 2 ]]; then
    echo "❌ Missing key code" >&2
    exit 1
  fi
  keyevent_cmd "$2"
  exit 0
fi

if [[ $1 == "--swipe" ]]; then
  if [[ $# -lt 5 ]]; then
    echo "❌ swipe requires x1 y1 x2 y2 [duration]" >&2
    exit 1
  fi
  swipe "$2" "$3" "$4" "$5" "${6:-200}"
  exit 0
fi

if (( $# % 2 )); then
  echo "❌ Provide coordinates in pairs (x y)" >&2
  exit 1
fi

while (( $# )); do
  tap "$1" "$2"
  shift 2
  sleep 0.2
done
