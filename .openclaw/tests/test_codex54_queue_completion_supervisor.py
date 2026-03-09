import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(
    "/Users/amunratis/AndroidStudioProjects/Arise-autowork/.openclaw/bin/codex54_queue_completion_supervisor.py"
)


def load_module():
    spec = importlib.util.spec_from_file_location("codex54_queue_completion_supervisor", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def completed_process(command, returncode=0, stdout="ok", stderr=""):
    return subprocess.CompletedProcess(command, returncode, stdout=stdout, stderr=stderr)


class QueueCompletionSupervisorTest(unittest.TestCase):
    def setUp(self):
        self.module = load_module()
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.queue_path = self.root / "codex54_briar_add_user_tasks.json"
        self.state_path = self.root / "workspace-state.json"
        self.roadmap_path = self.root / "briar_invitation_chat_roadmap.md"
        self.write_queue(
            {
                "schemaVersion": 1,
                "tasks": [
                    {
                        "id": "BU-001",
                        "status": "done",
                        "title": "Baseline add-user happy path",
                    }
                ],
            }
        )
        self.write_state({"version": 1})

    def tearDown(self):
        self.tempdir.cleanup()

    def write_queue(self, data):
        self.queue_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    def write_state(self, data):
        self.state_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    def write_roadmap(self, text):
        self.roadmap_path.write_text(text, encoding="utf-8")

    def test_policy_driven_done_queue_runs_validation_and_seeds_followup(self):
        self.write_roadmap(
            "\n".join(
                [
                    "# Briar Invitation-to-Chat Roadmap",
                    "- [ ] Add regression coverage for duplicate/expired/invalid invitation links and ensure clear user feedback.",
                ]
            )
        )
        commands = []

        def runner(command, cwd):
            commands.append((command, cwd))
            if "briar-invitation-e2e-diagnose.sh" in command:
                return completed_process(command, stdout=json.dumps({"result": "pass"}))
            return completed_process(command)

        result = self.module.run_completion_supervisor(
            queue_path=self.queue_path,
            state_path=self.state_path,
            repo_dir=self.root,
            policy_name="briar_add_user",
            runner=runner,
            roadmap_path=self.roadmap_path,
            invitation_uri="briar://real-link",
        )

        self.assertEqual("executor", result["action"])
        self.assertEqual("seeded_followup_queue", result["reason"])
        self.assertEqual(3, len(commands))

        queue = json.loads(self.queue_path.read_text(encoding="utf-8"))
        self.assertEqual(2, len(queue["tasks"]))
        seeded = queue["tasks"][-1]
        self.assertEqual("BU-002", seeded["id"])
        self.assertEqual("open", seeded["status"])
        self.assertEqual("raw-link-failure-cases", seeded["seedKey"])

        state = json.loads(self.state_path.read_text(encoding="utf-8"))
        completion = state["queueCompletion"]["briar_add_user"]
        self.assertEqual("passed", completion["validation"]["status"])
        self.assertEqual(["BU-002"], completion["seededTaskIds"])
        self.assertEqual("pass", completion["diagnostic"]["result"])
        self.assertTrue(completion["goal"]["satisfied"])

    def test_policy_driven_diagnostic_bug_seeds_bugfix_queue(self):
        self.write_roadmap("# Briar Invitation-to-Chat Roadmap\n")
        commands = []

        def runner(command, cwd):
            commands.append((command, cwd))
            if "briar-invitation-e2e-diagnose.sh" in command:
                return completed_process(
                    command,
                    stdout=json.dumps(
                        {
                            "result": "bug",
                            "bugCode": "briar_runtime_not_ready_on_external_invitation",
                            "summary": "Invitation onboarding fails because Briar runtime is not ready.",
                        }
                    ),
                )
            return completed_process(command)

        result = self.module.run_completion_supervisor(
            queue_path=self.queue_path,
            state_path=self.state_path,
            repo_dir=self.root,
            policy_name="briar_add_user",
            runner=runner,
            roadmap_path=self.roadmap_path,
            invitation_uri="briar://real-link",
        )

        self.assertEqual("executor", result["action"])
        self.assertEqual("diagnosed_bug_queue_seeded", result["reason"])

        queue = json.loads(self.queue_path.read_text(encoding="utf-8"))
        seeded = queue["tasks"][-1]
        self.assertEqual("runtime-not-ready-external-invite", seeded["seedKey"])
        self.assertIn("runtime", seeded["title"].lower())

        state = json.loads(self.state_path.read_text(encoding="utf-8"))
        completion = state["queueCompletion"]["briar_add_user"]
        self.assertFalse(completion["goal"]["satisfied"])
        self.assertEqual(
            "briar_runtime_not_ready_on_external_invitation",
            completion["goal"]["bugCode"],
        )

    def test_unmet_goal_without_specific_bug_mapping_seeds_fallback_queue(self):
        self.write_roadmap("# Briar Invitation-to-Chat Roadmap\n")

        def runner(command, cwd):
            if "briar-invitation-e2e-diagnose.sh" in command:
                return completed_process(
                    command,
                    stdout=json.dumps(
                        {
                            "result": "bug",
                            "bugCode": "messages_not_delivered_end_to_end",
                            "summary": "The invitation flow reaches chat, but the first Briar message never appears to send.",
                        }
                    ),
                )
            return completed_process(command)

        result = self.module.run_completion_supervisor(
            queue_path=self.queue_path,
            state_path=self.state_path,
            repo_dir=self.root,
            policy_name="briar_add_user",
            runner=runner,
            roadmap_path=self.roadmap_path,
            invitation_uri="briar://real-link",
        )

        self.assertEqual("executor", result["action"])
        self.assertEqual("unmet_goal_queue_seeded", result["reason"])
        self.assertEqual(["BU-002"], result["seededTaskIds"])

        queue = json.loads(self.queue_path.read_text(encoding="utf-8"))
        seeded = queue["tasks"][-1]
        self.assertEqual("briar-add-user-end-to-end-goal-gap", seeded["seedKey"])
        self.assertIn("end-to-end", seeded["title"].lower())
        self.assertTrue(
            any("first briar message" in instruction.lower() for instruction in seeded["instructions"])
        )

        state = json.loads(self.state_path.read_text(encoding="utf-8"))
        completion = state["queueCompletion"]["briar_add_user"]
        self.assertFalse(completion["goal"]["satisfied"])
        self.assertEqual("messages_not_delivered_end_to_end", completion["goal"]["bugCode"])
        self.assertEqual(["BU-002"], completion["seededTaskIds"])

    def test_unmet_goal_followup_can_seed_multiple_tasks(self):
        self.write_roadmap("# Briar Invitation-to-Chat Roadmap\n")
        original = copy.deepcopy(
            self.module.POLICIES["briar_add_user"]["diagnostic"]["unmetGoalFollowup"]
        )
        self.module.POLICIES["briar_add_user"]["diagnostic"]["unmetGoalFollowup"] = {
            "seedKey": "multi-step-goal-gap",
            "reason": "unmet_goal_queue_seeded",
            "tasks": [
                {
                    "status": "open",
                    "title": "Capture the first unmet Briar send goal gap",
                    "allowedPaths": ["app/src/test/java/example/GoalGapTest.kt"],
                    "contextFiles": ["docs/briar_invitation_chat_roadmap.md"],
                    "instructions": ["Turn the current end-to-end failure into a focused regression case."],
                    "validation": ["./agent-tools/test.sh --tests example.GoalGapTest --timeout 60"],
                    "commitMessage": "autowork(test): capture goal gap",
                    "reviewApproveMessage": "autowork(reviewer): approve {task_id}",
                    "reviewNeedsFixMessage": "autowork(reviewer): request fixes for {task_id}",
                    "reviewFocus": ["Check that the first task narrows the unmet goal clearly."],
                },
                {
                    "status": "blocked",
                    "blockedBy": "{previous_task_id}",
                    "title": "Close the narrowed Briar send goal gap",
                    "allowedPaths": ["app/src/main/java/example/GoalGap.kt"],
                    "contextFiles": ["app/src/test/java/example/GoalGapTest.kt"],
                    "instructions": ["Use the regression from the previous task to close the remaining goal gap."],
                    "validation": ["./agent-tools/test.sh --tests example.GoalGapTest --timeout 60"],
                    "commitMessage": "autowork(invite): close goal gap",
                    "reviewApproveMessage": "autowork(reviewer): approve {task_id}",
                    "reviewNeedsFixMessage": "autowork(reviewer): request fixes for {task_id}",
                    "reviewFocus": ["Check that the second task is blocked on the first seeded task."],
                },
            ],
        }
        try:
            def runner(command, cwd):
                if "briar-invitation-e2e-diagnose.sh" in command:
                    return completed_process(
                        command,
                        stdout=json.dumps(
                            {
                                "result": "bug",
                                "summary": "Chat opens, but the main goal is still unmet.",
                            }
                        ),
                    )
                return completed_process(command)

            result = self.module.run_completion_supervisor(
                queue_path=self.queue_path,
                state_path=self.state_path,
                repo_dir=self.root,
                policy_name="briar_add_user",
                runner=runner,
                roadmap_path=self.roadmap_path,
                invitation_uri="briar://real-link",
            )
        finally:
            self.module.POLICIES["briar_add_user"]["diagnostic"]["unmetGoalFollowup"] = original

        self.assertEqual("executor", result["action"])
        self.assertEqual("unmet_goal_queue_seeded", result["reason"])
        self.assertEqual(["BU-002", "BU-003"], result["seededTaskIds"])

        queue = json.loads(self.queue_path.read_text(encoding="utf-8"))
        first_seeded = queue["tasks"][-2]
        second_seeded = queue["tasks"][-1]
        self.assertEqual("BU-002", first_seeded["id"])
        self.assertEqual("open", first_seeded["status"])
        self.assertEqual("BU-003", second_seeded["id"])
        self.assertEqual("blocked", second_seeded["status"])
        self.assertEqual("BU-002", second_seeded["blockedBy"])

    def test_done_queue_without_policy_completes_without_extra_work(self):
        no_policy_queue = self.root / "arbitrary_queue.json"
        no_policy_queue.write_text(
            json.dumps({"schemaVersion": 1, "tasks": [{"id": "X-001", "status": "done", "title": "Done"}]}, indent=2)
            + "\n",
            encoding="utf-8",
        )
        commands = []

        def runner(command, cwd):
            commands.append((command, cwd))
            return completed_process(command)

        result = self.module.run_completion_supervisor(
            queue_path=no_policy_queue,
            state_path=self.state_path,
            repo_dir=self.root,
            runner=runner,
        )

        self.assertEqual("done", result["action"])
        self.assertEqual("queue_validated", result["reason"])
        self.assertEqual([], commands)
        state = json.loads(self.state_path.read_text(encoding="utf-8"))
        self.assertEqual("passed", state["queueCompletion"]["arbitrary_queue"]["validation"]["status"])

    def test_validation_failure_does_not_seed_followup_queue(self):
        self.write_roadmap(
            "\n".join(
                [
                    "# Briar Invitation-to-Chat Roadmap",
                    "- [ ] Add regression coverage for duplicate/expired/invalid invitation links and ensure clear user feedback.",
                ]
            )
        )

        def runner(command, cwd):
            return completed_process(command, returncode=1, stdout="", stderr="boom")

        result = self.module.run_completion_supervisor(
            queue_path=self.queue_path,
            state_path=self.state_path,
            repo_dir=self.root,
            policy_name="briar_add_user",
            runner=runner,
            roadmap_path=self.roadmap_path,
            invitation_uri="briar://real-link",
        )

        self.assertEqual("error", result["action"])
        self.assertEqual("validation_failed", result["reason"])
        queue = json.loads(self.queue_path.read_text(encoding="utf-8"))
        self.assertEqual(1, len(queue["tasks"]))

    def test_already_validated_queue_is_idempotent(self):
        self.write_roadmap(
            "\n".join(
                [
                    "# Briar Invitation-to-Chat Roadmap",
                    "- [x] Add regression coverage for duplicate/expired/invalid invitation links and ensure clear user feedback.",
                ]
            )
        )
        queue = json.loads(self.queue_path.read_text(encoding="utf-8"))
        fingerprint = self.module.queue_fingerprint(queue["tasks"])
        self.write_state(
            {
                "version": 1,
                "queueCompletion": {
                    "briar_add_user": {
                        "queueFingerprint": fingerprint,
                        "validation": {"status": "passed"},
                        "diagnosticInputs": {"invitation_uri": "briar://real-link"},
                        "diagnosticRunInputs": {"invitation_uri": "briar://real-link"},
                        "diagnostic": {"result": "pass", "status": "completed"},
                    }
                },
            }
        )
        calls = []

        def runner(command, cwd):
            calls.append((command, cwd))
            return completed_process(command)

        result = self.module.run_completion_supervisor(
            queue_path=self.queue_path,
            state_path=self.state_path,
            repo_dir=self.root,
            policy_name="briar_add_user",
            runner=runner,
            roadmap_path=self.roadmap_path,
            invitation_uri="briar://real-link",
        )

        self.assertEqual("done", result["action"])
        self.assertEqual("already_validated", result["reason"])
        self.assertEqual([], calls)


if __name__ == "__main__":
    unittest.main()
