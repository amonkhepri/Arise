#!/bin/zsh
set -euo pipefail

REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"
NOTE_FILE="$REPO_DIR/docs/gptoss_openclaw_runtime.md"

tracked_status="$(git -C "$REPO_DIR" status --porcelain --untracked-files=no)"
if [[ -n "$tracked_status" ]]; then
  echo "error=tracked_changes_present"
  exit 2
fi

if [[ -f "$NOTE_FILE" ]]; then
  echo "noop=docs_note_present"
  exit 0
fi

mkdir -p "${NOTE_FILE:h}"
cat >"$NOTE_FILE" <<'EOF'
# GPT-OSS OpenClaw Runtime Notes

- Backend: Codex CLI OSS routed to Ollama `gpt-oss:20b`.
- Startup hardening: disable Figma MCP for the OSS backend to avoid the large Codex startup tax.
- Validation target: executor and reviewer each land one docs-only commit under local bot identities before widening scope.
EOF

git -C "$REPO_DIR" add docs/gptoss_openclaw_runtime.md
git -C "$REPO_DIR" \
  -c user.name='gpt-oss-executor[openclaw]' \
  -c user.email='gptoss-executor@local' \
  commit -m 'autowork(arise): add gpt-oss runtime notes'

commit_sha="$(git -C "$REPO_DIR" rev-parse HEAD)"
echo "commit=$commit_sha"
