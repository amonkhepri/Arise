#!/usr/bin/env python3

import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

REPO_DIR = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork")
QUEUE_PATH = REPO_DIR / ".openclaw/codex54_briar_add_user_tasks.json"
TASK_QUEUE = REPO_DIR / ".openclaw/bin/codex54_task_queue.py"
TICK_LOG = Path("/Users/amunratis/.openclaw/logs/codex54-bm-story-tick.log")
EXEC_RUN_LOG = Path("/Users/amunratis/.openclaw/cron/runs/d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1.jsonl")
REVIEW_RUN_LOG = Path("/Users/amunratis/.openclaw/cron/runs/e42a5225-4d7a-4b4b-a2f4-f0467be4934e.jsonl")
EXECUTOR_ID = "d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1"
REVIEWER_ID = "e42a5225-4d7a-4b4b-a2f4-f0467be4934e"
SUPERVISOR_NAME = "arise-codex54-briar-loop-supervisor"


def normalize_queue():
    subprocess.run(
        [sys.executable, str(TASK_QUEUE), "list"],
        cwd=str(REPO_DIR),
        check=True,
        capture_output=True,
        text=True,
    )


def load_queue():
    return json.loads(QUEUE_PATH.read_text())


def load_jsonl_last(path: Path):
    if not path.exists():
        return None
    lines = [line for line in path.read_text().splitlines() if line.strip()]
    if not lines:
        return None
    return json.loads(lines[-1])


def parse_summary(summary: str):
    data = {}
    if not summary:
        return data
    for key in ("result", "task", "commit", "decision", "author", "files"):
        match = re.search(rf"^{key}:\s*(.+)$", summary, re.MULTILINE)
        if match:
            data[key] = match.group(1).strip()
    return data


def process_running(pattern: str):
    result = subprocess.run(
        ["pgrep", "-f", pattern],
        check=False,
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def queue_counts(tasks):
    counts = {}
    for task in tasks:
        counts[task["status"]] = counts.get(task["status"], 0) + 1
    return counts


def first_incomplete(tasks):
    for task in tasks:
        if task["status"] != "done":
            return task
    return None


def immediate_successor(tasks, task_id):
    for task in tasks:
        if task.get("blockedBy") == task_id:
            return task
    return None


def read_tick_tail(limit=40):
    if not TICK_LOG.exists():
        return []
    lines = [line for line in TICK_LOG.read_text().splitlines() if line.strip()]
    return lines[-limit:]


def minutes_since(ts_ms, now_ms):
    if ts_ms is None:
        return None
    return (now_ms - ts_ms) / 60000.0


def main():
    normalize_queue()
    queue = load_queue()
    tasks = queue["tasks"]
    now = datetime.now(timezone.utc)
    now_ms = int(now.timestamp() * 1000)

    latest_executor = load_jsonl_last(EXEC_RUN_LOG)
    latest_reviewer = load_jsonl_last(REVIEW_RUN_LOG)
    latest_executor_summary = parse_summary((latest_executor or {}).get("summary", ""))
    latest_reviewer_summary = parse_summary((latest_reviewer or {}).get("summary", ""))

    current = first_incomplete(tasks)
    successor = immediate_successor(tasks, current["id"]) if current else None

    active = {
        "executor": process_running(f"cron:{EXECUTOR_ID}"),
        "reviewer": process_running(f"cron:{REVIEWER_ID}"),
        "supervisor": process_running(SUPERVISOR_NAME),
    }

    issues = []
    suggestions = []
    tick_tail = read_tick_tail()
    recent_waiting = [line for line in tick_tail if "tick: queue waiting" in line]
    tick_actions = [line for line in tick_tail if "tick:" in line]
    latest_tick_action = tick_actions[-1] if tick_actions else ""

    if current and current["status"] == "pending_review" and not active["reviewer"]:
        age = minutes_since((latest_executor or {}).get("ts"), now_ms)
        if age is not None and age > 3:
            issues.append("stale_pending_review")
            suggestions.append("run_reviewer")

    if current and current["status"] in {"open", "needs_fix"} and not active["executor"]:
        age = minutes_since((latest_reviewer or latest_executor or {}).get("ts"), now_ms)
        if age is not None and age > 3:
            issues.append("stale_open_task")
            suggestions.append("run_executor")

    if current and current["status"] in {"open", "pending_review"} and recent_waiting:
        if "tick: queue waiting" in latest_tick_action:
            issues.append("queue_visibility_gap")
            suggestions.append("normalize_queue_and_fix_tick")

    if current and latest_executor_summary.get("result") == "error" and latest_executor_summary.get("task") == current["id"]:
        issues.append("executor_task_error")
        suggestions.append("inspect_current_task")
        if (
            current["status"] == "open"
            and successor
            and all(path.startswith("app/src/test/") for path in current.get("allowedPaths", []))
            and any(path.startswith("app/src/main/") for path in successor.get("allowedPaths", []))
        ):
            issues.append("red_green_merge_candidate")
            suggestions.append(f"merge_successor:{current['id']}->{successor['id']}")

    health = {
        "healthy": len(issues) == 0,
        "now": now.astimezone().isoformat(),
        "queue": {
            "counts": queue_counts(tasks),
            "current": current,
            "successor": successor,
        },
        "active": active,
        "latestExecutor": {
            "meta": {
                "ts": (latest_executor or {}).get("ts"),
                "status": (latest_executor or {}).get("status"),
                "durationMs": (latest_executor or {}).get("durationMs"),
            },
            "parsed": latest_executor_summary,
        },
        "latestReviewer": {
            "meta": {
                "ts": (latest_reviewer or {}).get("ts"),
                "status": (latest_reviewer or {}).get("status"),
                "durationMs": (latest_reviewer or {}).get("durationMs"),
            },
            "parsed": latest_reviewer_summary,
        },
        "tickTail": tick_tail,
        "issues": issues,
        "suggestions": suggestions,
    }

    if "--json" in sys.argv:
        print(json.dumps(health, indent=2))
    else:
        print(f"healthy={str(health['healthy']).lower()}")
        print("issues=" + ",".join(issues or ["none"]))
        print("suggestions=" + ",".join(suggestions or ["none"]))
        print("current=" + (current["id"] if current else "none"))
        print("current_status=" + (current["status"] if current else "none"))


if __name__ == "__main__":
    main()
