## Purpose

This is the long-lived autowork PR for the Briar invitation -> add-user -> first-message slice.

## Workflow

- OpenClaw executor, reviewer, and supervisor lanes commit to the same branch.
- Repo-local finalize scripts push the branch and try to reuse this PR after each commit.
- If GitHub auth is unavailable, local commits still land and PR sync is reported as skipped.

## Notes

- Branch: `feature/briar-user-chat-overnight`
- Base: `master`
- Scope: keep the branch focused on the Briar invitation/add-user/chat flow until that slice is closed.
