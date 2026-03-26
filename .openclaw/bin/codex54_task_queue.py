#!/usr/bin/env python3

import json
import sys
from pathlib import Path

QUEUE_PATH = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/codex54_briar_add_user_tasks.json")


def load_queue():
    with QUEUE_PATH.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def save_queue(data):
    with QUEUE_PATH.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def find_task(tasks, task_id):
    for task in tasks:
        if task["id"] == task_id:
            return task
    raise SystemExit(f"task not found: {task_id}")


def dependency_done(tasks, task):
    blocked_by = task.get("blockedBy")
    if not blocked_by:
        return True
    return find_task(tasks, blocked_by)["status"] == "done"


def unblock_ready_tasks(tasks):
    changed = False
    for task in tasks:
        if task.get("status") == "blocked" and dependency_done(tasks, task):
            task["status"] = "open"
            changed = True
    return changed


def first_incomplete(tasks):
    for task in tasks:
        if task["status"] != "done":
            return task
    return None


def print_noop(reason):
    print("result: noop")
    print(f"reason: {reason}")


def print_task(task, lane):
    print("result: task")
    print(f"lane: {lane}")
    print(f"task_id: {task['id']}")
    print(f"status: {task['status']}")
    print(f"title: {task['title']}")
    if task.get("scaffoldCommand"):
        print("task_mode: scaffold")
        print(f"required_first_command: {task['scaffoldCommand']}")
    else:
        print("task_mode: manual")
    print(f"allowed_paths: {','.join(task.get('allowedPaths', []))}")
    print(f"commit_message: {task['commitMessage']}")
    print(f"review_approve_message: {task['reviewApproveMessage']}")
    print(f"review_needs_fix_message: {task['reviewNeedsFixMessage']}")
    print(f"context_files: {','.join(task.get('contextFiles', []))}")
    if task.get("scaffoldCommand"):
        print(f"scaffold_command: {task['scaffoldCommand']}")
    print(f"executor_commit: {task.get('executorCommit') or 'none'}")
    print(f"review_commit: {task.get('reviewCommit') or 'none'}")
    for index, item in enumerate(task.get("instructions", []), start=1):
        print(f"instruction_{index}: {item}")
    for index, item in enumerate(task.get("validation", []), start=1):
        print(f"validation_{index}: {item}")
    for index, item in enumerate(task.get("reviewFocus", []), start=1):
        print(f"review_focus_{index}: {item}")


def cmd_next(lane):
    data = load_queue()
    tasks = data["tasks"]
    if unblock_ready_tasks(tasks):
        save_queue(data)
    if lane == "executor":
        task = first_incomplete(tasks)
        if not task:
            return print_noop("no_open_tasks")
        if task["status"] == "pending_review":
            return print_noop(f"awaiting_review:{task['id']}")
        if task["status"] not in {"open", "needs_fix"}:
            return print_noop(f"blocked:{task['id']}")
        return print_task(task, lane)
    if lane == "reviewer":
        for task in tasks:
            if task["status"] == "pending_review":
                return print_task(task, lane)
        return print_noop("no_pending_review")
    raise SystemExit(f"unknown lane: {lane}")


def cmd_complete_executor(task_id, commit_sha):
    data = load_queue()
    task = find_task(data["tasks"], task_id)
    if task["status"] not in {"open", "needs_fix"}:
        raise SystemExit(f"task {task_id} not ready for executor completion: {task['status']}")
    task["status"] = "pending_review"
    task["executorCommit"] = commit_sha
    save_queue(data)
    print(f"task={task_id}")
    print("status=pending_review")


def cmd_complete_reviewer(task_id, decision, commit_sha):
    if decision not in {"approved", "needs_fix"}:
        raise SystemExit(f"invalid decision: {decision}")
    data = load_queue()
    task = find_task(data["tasks"], task_id)
    if task["status"] != "pending_review":
        raise SystemExit(f"task {task_id} not pending review: {task['status']}")
    task["status"] = "done" if decision == "approved" else "needs_fix"
    task["reviewCommit"] = commit_sha
    save_queue(data)
    print(f"task={task_id}")
    print(f"status={task['status']}")


def cmd_list():
    data = load_queue()
    tasks = data["tasks"]
    if unblock_ready_tasks(tasks):
        save_queue(data)
    for task in tasks:
        print(
            "\t".join(
                [
                    task["id"],
                    task["status"],
                    task["title"],
                    task.get("executorCommit") or "-",
                    task.get("reviewCommit") or "-",
                ]
            )
        )


def main(argv):
    if len(argv) < 2:
        raise SystemExit("usage: codex54_task_queue.py <next|complete-executor|complete-reviewer|list> ...")
    command = argv[1]
    if command == "next":
        if len(argv) != 3:
            raise SystemExit("usage: codex54_task_queue.py next <executor|reviewer>")
        return cmd_next(argv[2])
    if command == "complete-executor":
        if len(argv) != 4:
            raise SystemExit("usage: codex54_task_queue.py complete-executor <task-id> <commit-sha>")
        return cmd_complete_executor(argv[2], argv[3])
    if command == "complete-reviewer":
        if len(argv) != 5:
            raise SystemExit("usage: codex54_task_queue.py complete-reviewer <task-id> <approved|needs_fix> <review-commit-sha>")
        return cmd_complete_reviewer(argv[2], argv[3], argv[4])
    if command == "list":
        return cmd_list()
    raise SystemExit(f"unknown command: {command}")


if __name__ == "__main__":
    main(sys.argv)
