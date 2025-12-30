#!/usr/bin/env bash
# Simple helper to drive a review→plan→execute→review loop with Codex.
# Fill the CODEx_* commands with the invocations you use locally (CLI, API, etc.).

set -euo pipefail

# Commands or functions that talk to Codex.
# Defaults use `codex exec -- "<prompt>"`; override via CODEX_* env vars if desired.
CODEX_REVIEW_CMD=${CODEX_REVIEW_CMD:-"codex exec -- \"Review uncommitted changes in the repo. Focus on risks, bugs, and missing tests.\""}
CODEX_PLAN_CMD=${CODEX_PLAN_CMD:-"codex exec -- \"Create a read-only plan based on the supplied review. Do not make changes.\""}
CODEX_EXECUTE_CMD=${CODEX_EXECUTE_CMD:-"codex exec -- \"Execute the provided plan and apply necessary code changes.\""}

# Stop condition substring (case-insensitive) that signals there are no problems left
STOP_TOKEN=${STOP_TOKEN:-"no problems"}

while true; do
  echo "== Running review =="
  review_out="$(bash -lc "$CODEX_REVIEW_CMD")"
  printf '%s\n' "$review_out" | tee /tmp/codex_last_review.log

  # Exit if review says we're clean
  if printf '%s\n' "$review_out" | grep -iq "$STOP_TOKEN"; then
    echo "Review reported no problems. Done."
    break
  fi

  echo "== Building plan =="
  plan_out="$(printf '%s\n' "$review_out" | bash -lc "$CODEX_PLAN_CMD")"
  printf '%s\n' "$plan_out" | tee /tmp/codex_last_plan.log

  echo "== Executing plan =="
  # Pass the plan text as part of the prompt to Codex so it knows what to execute.
  # Using a temp file to preserve newlines in the prompt.
  tmp_plan="$(mktemp /tmp/codex_plan.XXXXXX)"
  printf '%s\n' "$plan_out" > "$tmp_plan"
  exec_out="$(bash -lc "$CODEX_EXECUTE_CMD \"Execute the following plan:\\n$(cat "$tmp_plan")\"")"
  rm -f "$tmp_plan"
  printf '%s\n' "$exec_out" | tee /tmp/codex_last_execute.log

  # Loop continues with a fresh review of the new state
done

echo "Loop finished."
