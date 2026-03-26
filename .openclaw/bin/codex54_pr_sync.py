#!/usr/bin/env python3

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve()
DEFAULT_REPO_DIR = SCRIPT_PATH.parents[2]
DEFAULT_POLICY_PATH = DEFAULT_REPO_DIR / ".openclaw" / "codex54_pr_policy.json"


def run_command(command, cwd):
    return subprocess.run(
        command,
        cwd=cwd,
        text=True,
        capture_output=True,
        check=False,
    )


def output_line(key, value):
    print(f"{key}={value}")


def first_line(text):
    stripped = text.strip()
    if not stripped:
        return ""
    return stripped.splitlines()[0]


def load_policy(path):
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def resolve_body(repo_dir, policy):
    body_path = policy.get("bodyPath")
    if body_path:
        resolved = Path(body_path)
        if not resolved.is_absolute():
            resolved = repo_dir / resolved
        return resolved.read_text(encoding="utf-8")

    body = policy.get("body")
    if isinstance(body, list):
        return "\n".join(body)
    if isinstance(body, str):
        return body
    return ""


def git_current_branch(repo_dir, runner):
    result = runner(["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=repo_dir)
    if result.returncode != 0:
        raise RuntimeError(first_line(result.stderr) or "git rev-parse failed")
    return result.stdout.strip()


def push_branch(repo_dir, remote, branch, runner):
    return runner(["git", "push", "--set-upstream", remote, f"HEAD:{branch}"], cwd=repo_dir)


def gh_auth_status(repo_dir, runner):
    return runner(["gh", "auth", "status"], cwd=repo_dir)


def gh_find_open_pr(repo_dir, branch, base, runner):
    result = runner(
        [
            "gh",
            "pr",
            "list",
            "--head",
            branch,
            "--base",
            base,
            "--state",
            "open",
            "--json",
            "number,url,title",
            "--limit",
            "1",
        ],
        cwd=repo_dir,
    )
    if result.returncode != 0:
        return result, None

    data = json.loads(result.stdout or "[]")
    return result, data[0] if data else None


def gh_create_pr(repo_dir, branch, base, title, body, runner):
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
        handle.write(body)
        body_path = handle.name

    try:
        result = runner(
            [
                "gh",
                "pr",
                "create",
                "--head",
                branch,
                "--base",
                base,
                "--title",
                title,
                "--body-file",
                body_path,
            ],
            cwd=repo_dir,
        )
    finally:
        Path(body_path).unlink(missing_ok=True)

    return result


def gh_view_pr(repo_dir, branch, runner):
    result = runner(
        [
            "gh",
            "pr",
            "view",
            branch,
            "--json",
            "number,url,title",
        ],
        cwd=repo_dir,
    )
    if result.returncode != 0:
        return result, None
    return result, json.loads(result.stdout or "{}")


def sync_pr(repo_dir, policy_path, lane, commit_sha, runner=run_command):
    output_line("lane", lane)
    output_line("commit", commit_sha)

    policy = load_policy(policy_path)
    if policy is None:
        output_line("pr_sync_status", "no_policy")
        return 0

    if not policy.get("enabled", False):
        output_line("pr_sync_status", "disabled")
        return 0

    expected_branch = policy["branch"]
    current_branch = git_current_branch(repo_dir, runner)
    output_line("current_branch", current_branch)
    output_line("expected_branch", expected_branch)

    if current_branch == "HEAD":
        output_line("pr_sync_status", "detached_head")
        return 0

    if current_branch != expected_branch:
        output_line("pr_sync_status", "skipped_branch_mismatch")
        return 0

    remote = policy.get("remote", "origin")
    base = policy.get("base", "master")
    title = policy.get("title", f"Autowork: {current_branch}")
    body = resolve_body(repo_dir, policy)

    push_result = push_branch(repo_dir, remote, expected_branch, runner)
    output_line("push_remote", remote)
    if push_result.returncode != 0:
        output_line("push_status", "failed")
        output_line("push_error", first_line(push_result.stderr) or first_line(push_result.stdout))
        output_line("pr_sync_status", "push_failed")
        return 0

    output_line("push_status", "ok")

    auth_result = gh_auth_status(repo_dir, runner)
    if auth_result.returncode != 0:
        output_line("gh_auth", "invalid")
        output_line("gh_auth_error", first_line(auth_result.stderr) or first_line(auth_result.stdout))
        output_line("pr_sync_status", "auth_invalid")
        return 0

    output_line("gh_auth", "ok")

    list_result, pr_data = gh_find_open_pr(repo_dir, expected_branch, base, runner)
    if list_result.returncode != 0:
        output_line("pr_sync_status", "pr_lookup_failed")
        output_line("pr_error", first_line(list_result.stderr) or first_line(list_result.stdout))
        return 0

    if pr_data:
        output_line("pr_status", "existing")
        output_line("pr_number", pr_data.get("number", ""))
        output_line("pr_url", pr_data.get("url", ""))
        output_line("pr_title", pr_data.get("title", ""))
        output_line("pr_sync_status", "ok")
        return 0

    create_result = gh_create_pr(repo_dir, expected_branch, base, title, body, runner)
    if create_result.returncode != 0:
        output_line("pr_status", "create_failed")
        output_line("pr_error", first_line(create_result.stderr) or first_line(create_result.stdout))
        output_line("pr_sync_status", "pr_create_failed")
        return 0

    view_result, created_pr = gh_view_pr(repo_dir, expected_branch, runner)
    if view_result.returncode != 0 or created_pr is None:
        output_line("pr_status", "created")
        output_line("pr_url", first_line(create_result.stdout))
        output_line("pr_sync_status", "ok")
        return 0

    output_line("pr_status", "created")
    output_line("pr_number", created_pr.get("number", ""))
    output_line("pr_url", created_pr.get("url", ""))
    output_line("pr_title", created_pr.get("title", ""))
    output_line("pr_sync_status", "ok")
    return 0


def main():
    parser = argparse.ArgumentParser(
        description="Push the codex54 branch and create or reuse a long-lived PR."
    )
    parser.add_argument("--lane", required=True, choices=("executor", "reviewer", "supervisor"))
    parser.add_argument("--commit", required=True)
    parser.add_argument("--repo-dir", default=str(DEFAULT_REPO_DIR))
    parser.add_argument("--policy-path", default=str(DEFAULT_POLICY_PATH))
    args = parser.parse_args()

    repo_dir = Path(args.repo_dir).resolve()
    policy_path = Path(args.policy_path).resolve()

    try:
        return sync_pr(repo_dir, policy_path, args.lane, args.commit)
    except Exception as exc:
        output_line("pr_sync_status", "helper_error")
        output_line("pr_sync_error", str(exc))
        return 0


if __name__ == "__main__":
    sys.exit(main())
