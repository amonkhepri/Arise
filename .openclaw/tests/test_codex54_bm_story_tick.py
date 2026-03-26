import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(
    "/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/bin/codex54_bm_story_tick.sh"
)
ROOT_DIR = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork")
EXECUTOR_ID = "d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1"


class StoryTickTest(unittest.TestCase):
    def make_script_copy(self, root: Path, queue_path: Path) -> Path:
        script_text = SCRIPT_PATH.read_text(encoding="utf-8")
        script_text = script_text.replace(
            'REPO_DIR="/Users/amunratis/AndroidStudioProjects/Arise-autowork"',
            f'REPO_DIR="{root}"',
        )
        script_text = script_text.replace(
            'QUEUE_FILE="$REPO_DIR/.openclaw/codex54_briar_add_user_tasks.json"',
            f'QUEUE_FILE="{queue_path}"',
        )
        script_text = script_text.replace(
            'LOCK_DIR="/Users/amunratis/.openclaw/codex54-bm-story-tick.lock"',
            f'LOCK_DIR="{root / "codex54-bm-story-tick.lock"}"',
        )
        script_text = script_text.replace(
            'LOG_DIR="/Users/amunratis/.openclaw/logs"',
            f'LOG_DIR="{root / "logs"}"',
        )
        script_text = script_text.replace(
            'if pgrep -f "cron:${EXECUTOR_ID}|cron:${REVIEWER_ID}" >/dev/null 2>&1; then',
            'if false; then',
        )

        script_copy = root / "codex54_bm_story_tick.sh"
        script_copy.write_text(script_text, encoding="utf-8")
        return script_copy

    def write_launcher_fixtures(self, root: Path, *, completion_action: str):
        (root / "codex54_task_next.sh").write_text(
            "#!/bin/bash\nset -euo pipefail\necho 'result: noop'\necho 'reason: test'\n",
            encoding="utf-8",
        )
        (root / "codex54_queue_completion_supervisor.py").write_text(
            "#!/usr/bin/env python3\n"
            f"print('action={completion_action}')\n"
            "print('reason=test_seeded_followup_queue')\n"
            "print('queue_fingerprint=test-fingerprint')\n"
            "print('seeded_task_ids=BU-002')\n",
            encoding="utf-8",
        )
        run_result_script = (
            ROOT_DIR / ".openclaw" / "bin" / "codex54_bm_run_result.py"
        ).read_text(encoding="utf-8")
        launcher_script = (
            ROOT_DIR / ".openclaw" / "bin" / "codex54_bm_job_launcher.sh"
        ).read_text(encoding="utf-8")
        (root / "codex54_bm_run_result.py").write_text(
            run_result_script,
            encoding="utf-8",
        )
        (root / "codex54_bm_job_launcher.sh").write_text(
            launcher_script,
            encoding="utf-8",
        )
        (root / "fake-openclaw").write_text(
            "#!/bin/bash\n"
            "set -euo pipefail\n"
            "printf '%s\\n' \"$*\" >> \"$OPENCLAW_LOG\"\n"
            "python3 - \"$RUN_LOG\" \"$RUN_SUMMARY_RESULT\" \"$RUN_TASK\" <<'PY'\n"
            "import json\n"
            "import sys\n"
            "import time\n"
            "from pathlib import Path\n"
            "\n"
            "run_log, summary_result, task_id = sys.argv[1:]\n"
            "commit = 'abc123' if summary_result == 'success' else 'none'\n"
            "summary = (\n"
            "    f'result: {summary_result}\\n'\n"
            "    f'task: {task_id}\\n'\n"
            "    f'commit: {commit}\\n'\n"
            "    'author: codex-executor[openclaw] <codex-executor@local>\\n'\n"
            "    'files: none'\n"
            ")\n"
            "payload = {\n"
            "    'ts': int(time.time() * 1000),\n"
            "    'status': 'ok',\n"
            "    'summary': summary,\n"
            "}\n"
            "with Path(run_log).open('a', encoding='utf-8') as fh:\n"
            "    fh.write(json.dumps(payload) + '\\n')\n"
            "PY\n",
            encoding="utf-8",
        )

        for path in (
            root / "codex54_task_next.sh",
            root / "codex54_queue_completion_supervisor.py",
            root / "codex54_bm_run_result.py",
            root / "codex54_bm_job_launcher.sh",
            root / "fake-openclaw",
        ):
            path.chmod(0o755)

    def run_tick(self, root: Path, script_copy: Path, *, summary_result: str):
        openclaw_log = root / "openclaw.log"
        run_log = root / "executor-runs.jsonl"
        env = dict(os.environ)
        env["OPENCLAW_BIN"] = str(root / "fake-openclaw")
        env["OPENCLAW_LOG"] = str(openclaw_log)
        env["RUN_LOG"] = str(run_log)
        env["RUN_SUMMARY_RESULT"] = summary_result
        env["RUN_TASK"] = "BU-002"
        env["CODEX54_EXEC_RUN_LOG"] = str(run_log)

        result = subprocess.run(
            ["/bin/bash", str(script_copy)],
            capture_output=True,
            text=True,
            check=False,
            env=env,
        )
        return result, openclaw_log, root / "logs" / "codex54-bm-story-tick.log"

    def test_done_queue_with_executor_seed_runs_executor_without_invitation_uri(self):
        with tempfile.TemporaryDirectory() as tempdir:
            root = Path(tempdir)
            queue_path = root / "codex54_briar_add_user_tasks.json"
            queue_path.write_text(
                '{\n  "schemaVersion": 1,\n  "tasks": [\n    {"id": "BU-001", "status": "done", "title": "Done"}\n  ]\n}\n',
                encoding="utf-8",
            )
            script_copy = self.make_script_copy(root, queue_path)
            self.write_launcher_fixtures(root, completion_action="executor")
            script_copy.chmod(0o755)
            result, openclaw_log, tick_log = self.run_tick(
                root,
                script_copy,
                summary_result="success",
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                f"cron run {EXECUTOR_ID} --expect-final --timeout 3600000\n",
                openclaw_log.read_text(encoding="utf-8"),
            )
            self.assertIn(
                "tick: executor completed after completion supervisor",
                tick_log.read_text(encoding="utf-8"),
            )

    def test_done_queue_with_executor_seed_fails_when_run_summary_reports_error(self):
        with tempfile.TemporaryDirectory() as tempdir:
            root = Path(tempdir)
            queue_path = root / "codex54_briar_add_user_tasks.json"
            queue_path.write_text(
                '{\n  "schemaVersion": 1,\n  "tasks": [\n    {"id": "BU-001", "status": "done", "title": "Done"}\n  ]\n}\n',
                encoding="utf-8",
            )
            script_copy = self.make_script_copy(root, queue_path)
            self.write_launcher_fixtures(root, completion_action="executor")
            script_copy.chmod(0o755)

            result, openclaw_log, tick_log = self.run_tick(
                root,
                script_copy,
                summary_result="error",
            )

            self.assertNotEqual(0, result.returncode)
            self.assertEqual(
                f"cron run {EXECUTOR_ID} --expect-final --timeout 3600000\n",
                openclaw_log.read_text(encoding="utf-8"),
            )
            tick_log_text = tick_log.read_text(encoding="utf-8")
            self.assertIn("executor verification: summaryResult=error", tick_log_text)
            self.assertIn("executor failed post-run verification", tick_log_text)


if __name__ == "__main__":
    unittest.main()
