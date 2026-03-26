import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(
    "/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/bin/codex54_pr_sync.py"
)


def load_module():
    spec = importlib.util.spec_from_file_location("codex54_pr_sync", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def completed_process(command, returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess(command, returncode, stdout=stdout, stderr=stderr)


class Codex54PrSyncTest(unittest.TestCase):
    def setUp(self):
        self.module = load_module()
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.policy_path = self.root / "codex54_pr_policy.json"
        self.body_path = self.root / "codex54_pr_body.md"
        self.body_path.write_text("Body text\n", encoding="utf-8")
        self.policy_path.write_text(
            json.dumps(
                {
                    "version": 1,
                    "enabled": True,
                    "remote": "origin",
                    "branch": "feature/briar-user-chat-overnight",
                    "base": "master",
                    "title": "Autowork PR",
                    "bodyPath": str(self.body_path),
                }
            )
            + "\n",
            encoding="utf-8",
        )

    def tearDown(self):
        self.tempdir.cleanup()

    def test_no_policy_reports_no_policy(self):
        outputs = []

        def capture(key, value):
            outputs.append((key, value))

        old_output_line = self.module.output_line
        self.module.output_line = capture
        try:
            code = self.module.sync_pr(
                repo_dir=self.root,
                policy_path=self.root / "missing.json",
                lane="executor",
                commit_sha="abc123",
                runner=lambda command, cwd: completed_process(command),
            )
        finally:
            self.module.output_line = old_output_line

        self.assertEqual(0, code)
        self.assertIn(("pr_sync_status", "no_policy"), outputs)

    def test_branch_mismatch_skips_sync(self):
        outputs = []

        def capture(key, value):
            outputs.append((key, value))

        def runner(command, cwd):
            self.assertEqual(self.root, cwd)
            if command[:4] == ["git", "rev-parse", "--abbrev-ref", "HEAD"]:
                return completed_process(command, stdout="different-branch\n")
            self.fail(f"unexpected command: {command}")

        old_output_line = self.module.output_line
        self.module.output_line = capture
        try:
            code = self.module.sync_pr(
                repo_dir=self.root,
                policy_path=self.policy_path,
                lane="reviewer",
                commit_sha="def456",
                runner=runner,
            )
        finally:
            self.module.output_line = old_output_line

        self.assertEqual(0, code)
        self.assertIn(("pr_sync_status", "skipped_branch_mismatch"), outputs)

    def test_invalid_auth_pushes_branch_but_skips_pr(self):
        outputs = []
        commands = []

        def capture(key, value):
            outputs.append((key, value))

        def runner(command, cwd):
            commands.append(command)
            if command[:4] == ["git", "rev-parse", "--abbrev-ref", "HEAD"]:
                return completed_process(command, stdout="feature/briar-user-chat-overnight\n")
            if command[:3] == ["git", "push", "--set-upstream"]:
                return completed_process(command, stdout="pushed\n")
            if command[:3] == ["gh", "auth", "status"]:
                return completed_process(command, returncode=1, stderr="invalid token\n")
            self.fail(f"unexpected command: {command}")

        old_output_line = self.module.output_line
        self.module.output_line = capture
        try:
            code = self.module.sync_pr(
                repo_dir=self.root,
                policy_path=self.policy_path,
                lane="executor",
                commit_sha="abc123",
                runner=runner,
            )
        finally:
            self.module.output_line = old_output_line

        self.assertEqual(0, code)
        self.assertIn(("push_status", "ok"), outputs)
        self.assertIn(("pr_sync_status", "auth_invalid"), outputs)
        self.assertEqual(
            ["git", "push", "--set-upstream", "origin", "HEAD:feature/briar-user-chat-overnight"],
            commands[1],
        )

    def test_existing_pr_is_reused(self):
        outputs = []

        def capture(key, value):
            outputs.append((key, value))

        def runner(command, cwd):
            if command[:4] == ["git", "rev-parse", "--abbrev-ref", "HEAD"]:
                return completed_process(command, stdout="feature/briar-user-chat-overnight\n")
            if command[:3] == ["git", "push", "--set-upstream"]:
                return completed_process(command, stdout="pushed\n")
            if command[:3] == ["gh", "auth", "status"]:
                return completed_process(command, stdout="logged in\n")
            if command[:3] == ["gh", "pr", "list"]:
                return completed_process(
                    command,
                    stdout=json.dumps(
                        [
                            {
                                "number": 7,
                                "url": "https://example.test/pr/7",
                                "title": "Autowork PR",
                            }
                        ]
                    ),
                )
            self.fail(f"unexpected command: {command}")

        old_output_line = self.module.output_line
        self.module.output_line = capture
        try:
            code = self.module.sync_pr(
                repo_dir=self.root,
                policy_path=self.policy_path,
                lane="supervisor",
                commit_sha="abc123",
                runner=runner,
            )
        finally:
            self.module.output_line = old_output_line

        self.assertEqual(0, code)
        self.assertIn(("pr_status", "existing"), outputs)
        self.assertIn(("pr_number", 7), outputs)
        self.assertIn(("pr_sync_status", "ok"), outputs)

    def test_missing_pr_is_created(self):
        outputs = []
        commands = []

        def capture(key, value):
            outputs.append((key, value))

        def runner(command, cwd):
            commands.append(command)
            if command[:4] == ["git", "rev-parse", "--abbrev-ref", "HEAD"]:
                return completed_process(command, stdout="feature/briar-user-chat-overnight\n")
            if command[:3] == ["git", "push", "--set-upstream"]:
                return completed_process(command, stdout="pushed\n")
            if command[:3] == ["gh", "auth", "status"]:
                return completed_process(command, stdout="logged in\n")
            if command[:3] == ["gh", "pr", "list"]:
                return completed_process(command, stdout="[]")
            if command[:3] == ["gh", "pr", "create"]:
                return completed_process(command, stdout="https://example.test/pr/8\n")
            if command[:3] == ["gh", "pr", "view"]:
                return completed_process(
                    command,
                    stdout=json.dumps(
                        {
                            "number": 8,
                            "url": "https://example.test/pr/8",
                            "title": "Autowork PR",
                        }
                    ),
                )
            self.fail(f"unexpected command: {command}")

        old_output_line = self.module.output_line
        self.module.output_line = capture
        try:
            code = self.module.sync_pr(
                repo_dir=self.root,
                policy_path=self.policy_path,
                lane="executor",
                commit_sha="abc123",
                runner=runner,
            )
        finally:
            self.module.output_line = old_output_line

        self.assertEqual(0, code)
        self.assertIn(("pr_status", "created"), outputs)
        self.assertIn(("pr_number", 8), outputs)
        self.assertIn(("pr_sync_status", "ok"), outputs)
        self.assertTrue(any(command[:3] == ["gh", "pr", "create"] for command in commands))
