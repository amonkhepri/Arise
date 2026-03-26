#!/usr/bin/env python3

import json
import sys
from pathlib import Path

QUEUE_PATH = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/codex54_briar_add_user_tasks.json")


def uniq(items):
    seen = set()
    out = []
    for item in items:
        if item not in seen:
            seen.add(item)
            out.append(item)
    return out


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: codex54_task_merge_successor.py <task-id>")

    task_id = sys.argv[1]
    data = json.loads(QUEUE_PATH.read_text())
    tasks = data["tasks"]

    current_index = None
    successor_index = None

    for index, task in enumerate(tasks):
        if task["id"] == task_id:
            current_index = index
        elif task.get("blockedBy") == task_id and successor_index is None:
            successor_index = index

    if current_index is None:
        raise SystemExit(f"task not found: {task_id}")
    if successor_index is None:
        raise SystemExit(f"task {task_id} has no immediate successor to merge")

    current = tasks[current_index]
    successor = tasks[successor_index]

    current["title"] = f"{current['title']} + {successor['title']}"
    current["allowedPaths"] = uniq(current.get("allowedPaths", []) + successor.get("allowedPaths", []))
    current["contextFiles"] = uniq(current.get("contextFiles", []) + successor.get("contextFiles", []))
    current["instructions"] = uniq(
        current.get("instructions", [])
        + ["After reproducing the regression, make the minimal production change required for the same validation command to pass."]
        + successor.get("instructions", [])
    )
    current["validation"] = uniq(current.get("validation", []) + successor.get("validation", []))
    current["reviewFocus"] = uniq(current.get("reviewFocus", []) + successor.get("reviewFocus", []))
    current["commitMessage"] = successor.get("commitMessage", current.get("commitMessage"))
    current["mergedSuccessorTask"] = successor["id"]

    successor_id = successor["id"]
    for task in tasks:
        if task.get("blockedBy") == successor_id:
            task["blockedBy"] = current["id"]

    tasks.pop(successor_index)

    QUEUE_PATH.write_text(json.dumps(data, indent=2) + "\n")
    print(f"merged={current['id']}+{successor_id}")


if __name__ == "__main__":
    main()
