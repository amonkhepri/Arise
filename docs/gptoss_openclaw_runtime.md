# GPT-OSS OpenClaw Runtime Notes

- Backend: Codex CLI OSS routed to Ollama `gpt-oss:20b`.
- Startup hardening: disable Figma MCP for the OSS backend to avoid the large Codex startup tax.
- Validation target: executor and reviewer each land one docs-only commit under local bot identities before widening scope.
