import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(
    "/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/bin/codex54_bm_story_tick.sh"
)
EXECUTOR_ID = "d60fb5b7-b2d1-45f4-8f31-4bba5bdb8fb1"


class StoryTickTest(unittest.TestCase):
    def test_done_queue_with_executor_seed_runs_executor_without_invitation_uri(self):
        with tempfile.TemporaryDirectory() as tempdir:
            root = Path(tempdir)
            queue_path = root / "codex54_briar_add_user_tasks.json"
            queue_path.write_text(
                '{\n  "schemaVersion": 1,\n  "tasks": [\n    {"id": "BU-001", "status": "done", "title": "Done"}\n  ]\n}\n',
                encoding="utf-8",
            )

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

            script_copy = root / "codex54_bm_story_tick.sh"
            script_copy.write_text(script_text, encoding="utf-8")

            (root / "codex54_task_next.sh").write_text(
                "#!/bin/bash\nset -euo pipefail\necho 'result: noop'\necho 'reason: test'\n",
                encoding="utf-8",
            )
            (root / "codex54_queue_completion_supervisor.py").write_text(
                "#!/usr/bin/env python3\nprint('action=executor')\nprint('reason=test_seeded_followup_queue')\nprint('queue_fingerprint=test-fingerprint')\nprint('seeded_task_ids=BU-002')\n",
                encoding="utf-8",
            )

            openclaw_log = root / "openclaw.log"
            (root / "fake-openclaw").write_text(
                "#!/bin/bash\nset -euo pipefail\nprintf '%s\\n' \"$*\" >> \"$OPENCLAW_LOG\"\n",
                encoding="utf-8",
            )

            for path in (
                script_copy,
                root / "codex54_task_next.sh",
                root / "codex54_queue_completion_supervisor.py",
                root / "fake-openclaw",
            ):
                path.chmod(0o755)

            env = dict(os.environ)
            env["OPENCLAW_BIN"] = str(root / "fake-openclaw")
            env["OPENCLAW_LOG"] = str(openclaw_log)

            result = subprocess.run(
                ["/bin/bash", str(script_copy)],
                capture_output=True,
                text=True,
                check=False,
                env=env,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                f"cron run {EXECUTOR_ID} --expect-final --timeout 3600000\n",
                openclaw_log.read_text(encoding="utf-8"),
            )


if __name__ == "__main__":
    unittest.main()
