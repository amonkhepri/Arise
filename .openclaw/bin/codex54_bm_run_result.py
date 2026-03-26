#!/usr/bin/env python3

import argparse
import json
import os
import re
from pathlib import Path


DEFAULT_EXECUTOR_RUN_LOG = Path(
    "/Users/amunratis/.openclaw/cron/runs/d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1.jsonl"
)
DEFAULT_REVIEWER_RUN_LOG = Path(
    "/Users/amunratis/.openclaw/cron/runs/e42a5225-4d7a-4b4b-a2f4-f0467be4934e.jsonl"
)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Inspect the latest codex54 BM executor/reviewer run result."
    )
    parser.add_argument("lane", choices=("executor", "reviewer"))
    parser.add_argument(
        "--after-ts",
        type=int,
        default=-1,
        help="Only consider runs strictly newer than this timestamp in milliseconds.",
    )
    parser.add_argument(
        "--latest-ts",
        action="store_true",
        help="Print only the latest run timestamp for the lane.",
    )
    return parser.parse_args()


def run_log_path(lane: str) -> Path:
    if lane == "executor":
        return Path(os.environ.get("CODEX54_EXEC_RUN_LOG", DEFAULT_EXECUTOR_RUN_LOG))
    return Path(os.environ.get("CODEX54_REVIEW_RUN_LOG", DEFAULT_REVIEWER_RUN_LOG))


def load_latest_entry(path: Path, after_ts: int):
    if not path.exists():
        return None

    latest = None
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line.strip():
            continue
        try:
            entry = json.loads(raw_line)
        except json.JSONDecodeError:
            continue
        if int(entry.get("ts", -1)) <= after_ts:
            continue
        latest = entry
    return latest


def parse_summary(summary: str):
    data = {}
    if not summary:
        return data
    for key in ("result", "task", "commit", "decision", "author", "files"):
        match = re.search(rf"^{key}:\s*(.+)$", summary, re.MULTILINE)
        if match:
            data[key] = match.group(1).strip()
    return data


def emit_result(entry):
    if not entry:
        print("outcome=missing")
        print("ts=0")
        print("runStatus=none")
        print("summaryResult=none")
        print("task=none")
        print("commit=none")
        return

    parsed = parse_summary(entry.get("summary", ""))
    summary_result = parsed.get("result", "unknown")
    if summary_result in {"success", "noop"}:
        outcome = summary_result
    elif summary_result == "error":
        outcome = "error"
    else:
        outcome = "unknown"

    print(f"outcome={outcome}")
    print(f"ts={entry.get('ts', 0)}")
    print(f"runStatus={entry.get('status', 'unknown')}")
    print(f"summaryResult={summary_result}")
    print(f"task={parsed.get('task', 'none')}")
    print(f"commit={parsed.get('commit', 'none')}")


def main():
    args = parse_args()
    path = run_log_path(args.lane)
    entry = load_latest_entry(path, args.after_ts)

    if args.latest_ts:
        print(entry.get("ts", 0) if entry else 0)
        return

    emit_result(entry)


if __name__ == "__main__":
    main()
