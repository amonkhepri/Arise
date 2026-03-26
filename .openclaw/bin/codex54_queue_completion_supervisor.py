#!/usr/bin/env python3

import argparse
import copy
import hashlib
import json
import re
import shlex
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

DEFAULT_REPO_DIR = Path("/Users/amunratis/AndroidStudioProjects/Arise-autowork")
DEFAULT_STATE_PATH = DEFAULT_REPO_DIR / ".openclaw/workspace-state.json"

POLICIES = {
    "briar_add_user": {
        "stateKey": "briar_add_user",
        "defaultQueuePath": ".openclaw/codex54_briar_add_user_tasks.json",
        "roadmapPath": "docs/briar_invitation_chat_roadmap.md",
        "goal": {
            "description": "A user can add another Briar user from the people screen or an external briar:// invitation, reach chat end to end, and be ready to send the first Briar message.",
        },
        "validationCommands": [
            "./agent-tools/test.sh --tests com.example.rise.ui.dashboardNavigation.people.peopleFragment.BriarPeopleAddUserFlowBriarOnlyTest --timeout 600",
            "./agent-tools/test.sh --tests com.example.rise.ui.dashboardNavigation.people.peopleFragment.BriarPeopleAddUserFlowHybridTest --timeout 600",
        ],
        "diagnostic": {
            "requiredInputs": ["invitation_uri"],
            "commandTemplate": "./agent-tools/briar-invitation-e2e-diagnose.sh {invitation_uri}",
            "passResults": ["pass"],
            "bugFollowups": {
                "briar_runtime_not_ready_on_external_invitation": {
                    "seedKey": "runtime-not-ready-external-invite",
                    "reason": "diagnosed_bug_queue_seeded",
                    "task": {
                        "status": "open",
                        "title": "Handle cold-start external Briar invitations when the runtime comes up late",
                        "allowedPaths": [
                            "app/src/main/java/com/example/rise/ui/mainActivity/MainActivity.kt",
                            "app/src/main/java/com/example/rise/ui/mainActivity/MainActivityViewModel.kt",
                            "app/src/main/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinator.kt",
                            "app/src/main/java/com/example/rise/transport/briar/BriarContactRepository.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/MainActivityViewModelTest.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinatorTest.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowBriarOnlyTest.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowHybridTest.kt",
                        ],
                        "contextFiles": [
                            "app/src/main/java/com/example/rise/ui/SplashActivityViewModel.kt",
                            "app/src/main/java/com/example/rise/ui/mainActivity/MainActivity.kt",
                            "app/src/main/java/com/example/rise/ui/mainActivity/MainActivityViewModel.kt",
                            "app/src/main/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinator.kt",
                            "app/src/main/java/com/example/rise/transport/briar/BriarContactRepository.kt",
                            "app/src/main/java/com/example/rise/transport/TransportRuntimeBridgeImpl.kt",
                            "briar-runtime/src/main/java/com/example/rise/briar/runtime/BriarRuntimeManagerImpl.kt",
                            "briar-runtime/src/main/java/com/example/rise/briar/runtime/RealBriarContactService.kt",
                            "briar-runtime/src/main/java/com/example/rise/briar/runtime/BriarComponentFactoryImpl.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/MainActivityViewModelTest.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinatorTest.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowBriarOnlyTest.kt",
                            "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowHybridTest.kt",
                        ],
                        "instructions": [
                            "Reproduce the regression with the provided external invitation URI `{invitation_uri}` before changing production code.",
                            "Use the diagnosed failure signal `{specific_signal_source}={specific_signal}` as the narrow hypothesis for this queue.",
                            "Add failing regression coverage first for a cold-start external Briar invitation where invitation acceptance begins before sign-in and the embedded Briar runtime are ready.",
                            "Make external invitation onboarding wait, resume, or retry against readiness instead of dropping the invitation immediately with a generic failure toast.",
                            "Keep raw briar deep-link routing intact while fixing the diagnosed bug: {summary}",
                        ],
                        "validation": [
                            "./agent-tools/test.sh --tests com.example.rise.ui.mainActivity.MainActivityViewModelTest --timeout 300",
                            "./agent-tools/test.sh --tests com.example.rise.ui.mainActivity.BriarInvitationOnboardingCoordinatorTest --timeout 300",
                            "./agent-tools/test.sh --tests com.example.rise.ui.mainActivity.BriarInvitationChatFlowBriarOnlyTest --timeout 600",
                            "./agent-tools/test.sh --tests com.example.rise.ui.mainActivity.BriarInvitationChatFlowHybridTest --timeout 600",
                        ],
                        "commitMessage": "autowork(invite): handle runtime-late external onboarding",
                        "reviewApproveMessage": "autowork(reviewer): approve {task_id}",
                        "reviewNeedsFixMessage": "autowork(reviewer): request fixes for {task_id}",
                        "reviewFocus": [
                            "Check that the fix adds regression coverage for the runtime-not-ready cold-start path before changing production code.",
                            "Check that the invitation is preserved until runtime readiness or that the user gets explicit recoverable behavior instead of a dropped flow.",
                            "Check that the change stays scoped to external invitation onboarding and does not regress the existing chat happy-path tests.",
                        ],
                        "seededBySupervisor": True,
                    },
                }
            },
            "unmetGoalFollowup": {
                "seedKey": "briar-add-user-end-to-end-goal-gap",
                "reason": "unmet_goal_queue_seeded",
                "task": {
                    "status": "open",
                    "title": "Close the remaining Briar add-user end-to-end goal gap",
                    "allowedPaths": [
                        "app/src/main/java/com/example/rise/ui/mainActivity/MainActivity.kt",
                        "app/src/main/java/com/example/rise/ui/mainActivity/MainActivityViewModel.kt",
                        "app/src/main/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinator.kt",
                        "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinator.kt",
                        "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModel.kt",
                        "app/src/main/java/com/example/rise/transport/briar/invite/BriarInvitationAcceptanceUseCase.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/MainActivityViewModelTest.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinatorTest.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowBriarOnlyTest.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowHybridTest.kt",
                        "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinatorTest.kt",
                        "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModelTest.kt",
                    ],
                    "contextFiles": [
                        "app/src/main/java/com/example/rise/ui/mainActivity/MainActivity.kt",
                        "app/src/main/java/com/example/rise/ui/mainActivity/MainActivityViewModel.kt",
                        "app/src/main/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinator.kt",
                        "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinator.kt",
                        "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModel.kt",
                        "app/src/main/java/com/example/rise/transport/briar/invite/BriarInvitationAcceptanceUseCase.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/MainActivityViewModelTest.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationOnboardingCoordinatorTest.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowBriarOnlyTest.kt",
                        "app/src/test/java/com/example/rise/ui/mainActivity/BriarInvitationChatFlowHybridTest.kt",
                        "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinatorTest.kt",
                        "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModelTest.kt",
                    ],
                    "instructions": [
                        "The queue completed, but the overreaching goal is still unmet: {goal}.",
                        "Fresh failure signal for this follow-up: `{specific_signal_source}={specific_signal}`.",
                        "Reproduce the current end-to-end failure with the provided external invitation URI `{invitation_uri}` before changing production code.",
                        "Use the latest supervisor evidence to narrow and close the remaining gap. Current failure summary: {summary}",
                        "If the app reaches chat, confirm the first Briar message is actually sendable and does not get dropped silently.",
                        "Add or strengthen failing regression coverage first for the unmet end-to-end case, then implement the minimal fix while keeping the existing invitation-to-chat tests green.",
                    ],
                    "validation": [
                        "./agent-tools/test.sh --tests com.example.rise.ui.dashboardNavigation.people.peopleFragment.BriarPeopleAddUserFlowBriarOnlyTest --timeout 600",
                        "./agent-tools/test.sh --tests com.example.rise.ui.dashboardNavigation.people.peopleFragment.BriarPeopleAddUserFlowHybridTest --timeout 600",
                        "./agent-tools/test.sh --tests com.example.rise.ui.mainActivity.BriarInvitationChatFlowBriarOnlyTest --timeout 600",
                        "./agent-tools/test.sh --tests com.example.rise.ui.mainActivity.BriarInvitationChatFlowHybridTest --timeout 600",
                    ],
                    "commitMessage": "autowork(invite): close remaining briar add-user goal gap",
                    "reviewApproveMessage": "autowork(reviewer): approve {task_id}",
                    "reviewNeedsFixMessage": "autowork(reviewer): request fixes for {task_id}",
                    "reviewFocus": [
                        "Check that the task is driven by the unmet end-to-end goal rather than a speculative refactor.",
                        "Check that the fix keeps the Briar add-user and invitation-to-chat flows green in both BRIAR_ONLY and HYBRID modes.",
                        "Check that the change preserves explicit user-visible behavior for the remaining failure path.",
                    ],
                    "seededBySupervisor": True,
                },
            },
        },
        "followup": {
            "seedKey": "raw-link-failure-cases",
            "roadmapMarker": "- [ ] Add regression coverage for duplicate/expired/invalid invitation links and ensure clear user feedback.",
            "reason": "seeded_followup_queue",
            "task": {
                "status": "open",
                "title": "Cover duplicate, expired, and invalid raw Briar add-user links with explicit feedback",
                "allowedPaths": [
                    "app/src/main/java/com/example/rise/transport/briar/invite/BriarInvitationAcceptanceUseCase.kt",
                    "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinator.kt",
                    "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModel.kt",
                    "app/src/test/java/com/example/rise/transport/briar/invite/BriarInvitationAcceptanceUseCaseTest.kt",
                    "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinatorTest.kt",
                    "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModelTest.kt",
                ],
                "contextFiles": [
                    "app/src/main/java/com/example/rise/transport/briar/invite/BriarInvitationAcceptanceUseCase.kt",
                    "app/src/test/java/com/example/rise/transport/briar/invite/BriarInvitationAcceptanceUseCaseTest.kt",
                    "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinator.kt",
                    "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/BriarManualInvitationCoordinatorTest.kt",
                    "app/src/main/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModel.kt",
                    "app/src/test/java/com/example/rise/ui/dashboardNavigation/people/peopleFragment/PeopleViewModelTest.kt",
                    "docs/briar_invitation_chat_roadmap.md",
                ],
                "instructions": [
                    "Add failing regression coverage for duplicate, expired, and invalid raw Briar invitation links through the people-screen add-user path before changing production code.",
                    "Keep user feedback explicit so duplicate, expired, and invalid links do not collapse into one generic failure state when the underlying flow can distinguish them.",
                    "Limit the scope to the invitation acceptance, manual coordinator, and people-screen state path while keeping BU-006 and BU-007 happy-path coverage green.",
                ],
                "validation": [
                    "./agent-tools/test.sh --tests com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCaseTest --timeout 600",
                    "./agent-tools/test.sh --tests com.example.rise.ui.dashboardNavigation.people.peopleFragment.BriarManualInvitationCoordinatorTest --timeout 600",
                    "./agent-tools/test.sh --tests com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleViewModelTest --timeout 600",
                ],
                "commitMessage": "autowork(invite): cover raw-link add-user failure cases",
                "reviewApproveMessage": "autowork(reviewer): approve {task_id}",
                "reviewNeedsFixMessage": "autowork(reviewer): request fixes for {task_id}",
                "reviewFocus": [
                    "Check that duplicate, expired, and invalid link outcomes are each covered by failing tests first.",
                    "Check that user-visible feedback stays explicit on the people-screen raw-link add flow.",
                    "Check that the change stays scoped to invitation acceptance/coordinator/viewmodel paths without unrelated chat-flow edits.",
                ],
                "seededBySupervisor": True,
            },
        },
    }
}


def load_json(path: Path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def queue_fingerprint(tasks):
    payload = json.dumps(tasks, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def all_done(tasks):
    return bool(tasks) and all(task.get("status") == "done" for task in tasks)


def next_task_id(tasks):
    highest = 0
    for task in tasks:
        match = re.fullmatch(r"([A-Z]+)-(\d+)", task.get("id", ""))
        if match:
            highest = max(highest, int(match.group(2)))
    prefix_match = re.fullmatch(r"([A-Z]+)-\d+", tasks[-1]["id"]) if tasks else None
    prefix = prefix_match.group(1) if prefix_match else "Q"
    return f"{prefix}-{highest + 1:03d}"


def clip_output(text, max_lines=20):
    lines = [line for line in (text or "").splitlines() if line.strip()]
    if len(lines) <= max_lines:
        return "\n".join(lines)
    return "\n".join(lines[-max_lines:])


def command_runner(command, cwd):
    return subprocess.run(
        ["/bin/zsh", "-lc", command],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        check=False,
    )


def resolve_policy(policy_name, queue_path: Path, repo_dir: Path, roadmap_path: Optional[Path]):
    if policy_name:
        if policy_name not in POLICIES:
            raise SystemExit(f"unknown completion policy: {policy_name}")
        policy = copy.deepcopy(POLICIES[policy_name])
    else:
        policy = {
            "stateKey": queue_path.stem,
            "validationCommands": [],
        }
    policy["name"] = policy_name or policy["stateKey"]
    policy["queuePath"] = queue_path
    if roadmap_path is not None:
        policy["roadmapPath"] = roadmap_path
    elif "roadmapPath" in policy:
        policy["roadmapPath"] = repo_dir / policy["roadmapPath"]
    return policy


def get_completion_bucket(state, state_key):
    queue_completion = state.setdefault("queueCompletion", {})
    if state_key in queue_completion:
        return queue_completion[state_key]
    if state_key == "briar_add_user" and "briarAddUserCompletion" in state:
        queue_completion[state_key] = state["briarAddUserCompletion"]
        return queue_completion[state_key]
    queue_completion[state_key] = {}
    return queue_completion[state_key]


def seed_key_exists(tasks, seed_key):
    return any(task.get("seedKey") == seed_key for task in tasks)


def active_seed_key_exists(tasks, seed_key):
    return any(task.get("seedKey") == seed_key and task.get("status") != "done" for task in tasks)


def merge_diagnostic_inputs(completion, invitation_uri=None, diagnostic_inputs=None):
    merged = dict(completion.get("diagnosticInputs", {}))
    for key, value in (diagnostic_inputs or {}).items():
        if value is not None:
            merged[key] = str(value).strip()
    if invitation_uri is not None:
        merged["invitation_uri"] = str(invitation_uri).strip()
    if merged:
        completion["diagnosticInputs"] = merged
    return merged


def run_validation(commands, repo_dir: Path, runner):
    records = []
    for command in commands:
        result = runner(command, repo_dir)
        record = {
            "command": command,
            "returncode": result.returncode,
            "stdout": clip_output(result.stdout),
            "stderr": clip_output(result.stderr),
        }
        records.append(record)
        if result.returncode != 0:
            return {
                "status": "failed",
                "commands": records,
            }
    return {
        "status": "passed",
        "commands": records,
    }


def build_diagnostic_command(template, inputs):
    quoted_inputs = {
        key: shlex.quote(str(value))
        for key, value in inputs.items()
        if value is not None
    }
    return template.format(**quoted_inputs)


def run_diagnostic(policy, repo_dir: Path, runner, inputs):
    diagnostic_policy = policy.get("diagnostic")
    if not diagnostic_policy:
        return None

    missing_inputs = [
        key for key in diagnostic_policy.get("requiredInputs", []) if not inputs.get(key)
    ]
    if missing_inputs:
        return {
            "status": "input_missing",
            "missingInputs": missing_inputs,
        }

    command = build_diagnostic_command(diagnostic_policy["commandTemplate"], inputs)
    result = runner(command, repo_dir)
    diagnostic = {
        "status": "completed" if result.returncode == 0 else "failed",
        "command": command,
        "returncode": result.returncode,
        "stdout": clip_output(result.stdout),
        "stderr": clip_output(result.stderr),
    }
    if result.returncode != 0:
        return diagnostic

    try:
        payload = json.loads((result.stdout or "").strip() or "{}")
    except json.JSONDecodeError as exc:
        diagnostic.update(
            {
                "status": "invalid_output",
                "error": f"diagnostic output was not valid JSON: {exc}",
            }
        )
        return diagnostic

    if not isinstance(payload, dict):
        diagnostic.update(
            {
                "status": "invalid_output",
                "error": "diagnostic output must be a JSON object",
            }
        )
        return diagnostic

    diagnostic.update(payload)
    return diagnostic


def should_seed_followup(policy, tasks):
    followup = policy.get("followup")
    if not followup:
        return False
    roadmap_path = policy.get("roadmapPath")
    if roadmap_path is None or not Path(roadmap_path).exists():
        return False
    if seed_key_exists(tasks, followup["seedKey"]):
        return False
    roadmap_text = Path(roadmap_path).read_text(encoding="utf-8")
    return followup["roadmapMarker"] in roadmap_text


def format_seed_value(value, context):
    if isinstance(value, str):
        return value.format_map(context)
    if isinstance(value, list):
        return [format_seed_value(item, context) for item in value]
    if isinstance(value, dict):
        return {key: format_seed_value(item, context) for key, item in value.items()}
    return value


class SeedFormatContext(dict):
    def __missing__(self, key):
        return "{" + key + "}"


def make_seed_task(seed_spec, task_id, format_context):
    task_template = copy.deepcopy(seed_spec["task"])
    task_template["id"] = task_id
    task_template["seedKey"] = seed_spec["seedKey"]
    context = SeedFormatContext(task_id=task_id, **format_context)
    task_template = format_seed_value(task_template, context)
    return task_template


def make_seed_tasks(seed_spec, existing_tasks, format_context):
    task_templates = seed_spec.get("tasks")
    if task_templates is None:
        return [make_seed_task(seed_spec, next_task_id(existing_tasks), format_context)]

    seeded_tasks = []
    previous_task_id = None
    first_task_id = None
    for template in task_templates:
        task_id = next_task_id(existing_tasks + seeded_tasks)
        task_template = copy.deepcopy(template)
        task_template["id"] = task_id
        task_template["seedKey"] = seed_spec["seedKey"]
        current_context = SeedFormatContext(
            task_id=task_id,
            first_task_id=first_task_id or task_id,
            previous_task_id=previous_task_id or "",
            **format_context,
        )
        task_template = format_seed_value(task_template, current_context)
        if task_template.get("blockedBy") == "":
            task_template.pop("blockedBy", None)
        seeded_tasks.append(task_template)
        first_task_id = first_task_id or task_id
        previous_task_id = task_id
    return seeded_tasks


def goal_description(policy):
    return policy.get("goal", {}).get("description")


def goal_check_passed(policy, diagnostic):
    diagnostic_policy = policy.get("diagnostic")
    if not diagnostic_policy:
        return True
    pass_results = diagnostic_policy.get("passResults", ["pass"])
    return diagnostic.get("result") in pass_results


def build_goal_record(policy, diagnostic):
    record = {
        "description": goal_description(policy),
        "satisfied": goal_check_passed(policy, diagnostic),
    }
    for key in (
        "status",
        "result",
        "summary",
        "bugCode",
        "failureCode",
        "hypothesisCode",
        "command",
        "returncode",
    ):
        if key in diagnostic:
            record[key] = diagnostic[key]
    return record


def has_successful_diagnostic(policy, completion):
    diagnostic_policy = policy.get("diagnostic")
    if not diagnostic_policy:
        return True
    diagnostic = completion.get("diagnostic", {})
    if not goal_check_passed(policy, diagnostic):
        return False
    required_inputs = diagnostic_policy.get("requiredInputs", [])
    if not required_inputs:
        return True
    configured_inputs = completion.get("diagnosticInputs", {})
    run_inputs = completion.get("diagnosticRunInputs", {})
    return all(run_inputs.get(key) == configured_inputs.get(key) for key in required_inputs)


def same_diagnostic_inputs(completion, diagnostic_inputs):
    return completion.get("diagnosticInputs", {}) == diagnostic_inputs and completion.get(
        "diagnosticRunInputs", {}
    ) == diagnostic_inputs


def specific_followup_signal(diagnostic):
    for key in ("hypothesisCode", "failureCode", "bugCode"):
        value = str(diagnostic.get(key, "")).strip()
        if value:
            return key, value
    return None, None


def last_seed_signal(completion, tasks=None):
    signal = str(completion.get("lastSeedSignal", "")).strip()
    if signal:
        return signal
    if completion.get("seededTaskIds"):
        _, inferred_signal = specific_followup_signal(completion.get("diagnostic", {}))
        return inferred_signal
    for task in reversed(tasks or []):
        signal = str(task.get("specificSignal", "")).strip()
        if signal:
            return signal
    return ""


def diagnostic_followup_plan(policy, tasks, diagnostic, completion):
    if goal_check_passed(policy, diagnostic):
        return {"kind": "none"}

    signal_source, signal = specific_followup_signal(diagnostic)
    if not signal:
        return {
            "kind": "hold",
            "reason": "goal_unmet_missing_specific_signal",
            "signalSource": None,
            "signal": None,
        }

    if last_seed_signal(completion, tasks) == signal:
        return {
            "kind": "hold",
            "reason": "goal_unmet_signal_not_fresh",
            "signalSource": signal_source,
            "signal": signal,
        }

    diagnostic_policy = policy.get("diagnostic", {})
    bug_code = diagnostic.get("bugCode")
    bug_followups = diagnostic_policy.get("bugFollowups", {})
    if bug_code in bug_followups:
        seed_spec = bug_followups[bug_code]
        if active_seed_key_exists(tasks, seed_spec["seedKey"]):
            return {"kind": "none"}
        return {
            "kind": "seed",
            "seedSpec": seed_spec,
            "signalSource": signal_source,
            "signal": signal,
        }

    unmet_goal_followup = diagnostic_policy.get("unmetGoalFollowup")
    if unmet_goal_followup:
        if active_seed_key_exists(tasks, unmet_goal_followup["seedKey"]):
            return {"kind": "none"}
        return {
            "kind": "seed",
            "seedSpec": unmet_goal_followup,
            "signalSource": signal_source,
            "signal": signal,
        }

    if diagnostic.get("result") == "bug" and not bug_code:
        return {"kind": "error", "reason": "missing_bug_code"}
    if diagnostic.get("result") == "bug" and bug_code not in bug_followups:
        return {"kind": "error", "reason": "unknown_bug_code"}
    return {"kind": "error", "reason": "goal_not_met_unmapped"}


def finalize_seeded_queue(
    *,
    queue,
    tasks,
    queue_path,
    state,
    state_path,
    completion,
    seed_spec,
    format_context,
    queue_fingerprint_value,
    diagnosed_bug_code=None,
    specific_signal=None,
    specific_signal_source=None,
):
    seeded_tasks = make_seed_tasks(seed_spec, tasks, format_context)
    for task in seeded_tasks:
        if specific_signal:
            task["specificSignal"] = specific_signal
        if specific_signal_source:
            task["specificSignalSource"] = specific_signal_source
    queue["tasks"].extend(seeded_tasks)
    save_json(queue_path, queue)

    seeded_task_ids = [task["id"] for task in seeded_tasks]
    completion["seededTaskIds"] = seeded_task_ids
    completion["seedReason"] = seed_spec["seedKey"]
    if diagnosed_bug_code:
        completion["diagnosedBugCode"] = diagnosed_bug_code
    if specific_signal:
        completion["lastSeedSignal"] = specific_signal
    if specific_signal_source:
        completion["lastSeedSignalSource"] = specific_signal_source
    completion.pop("followupDecision", None)
    completion.pop("followupDecisionAt", None)

    save_json(state_path, state)
    return {
        "action": "executor",
        "reason": seed_spec.get("reason", "seeded_followup_queue"),
        "queueFingerprint": queue_fingerprint_value,
        "seededTaskIds": seeded_task_ids,
    }


def run_completion_supervisor(
    queue_path,
    state_path,
    repo_dir,
    policy_name=None,
    runner=command_runner,
    roadmap_path=None,
    invitation_uri=None,
    diagnostic_inputs=None,
):
    queue = load_json(queue_path, {"schemaVersion": 1, "tasks": []})
    tasks = queue.get("tasks", [])
    if not all_done(tasks):
        return {
            "action": "wait",
            "reason": "queue_not_finished",
            "queueFingerprint": queue_fingerprint(tasks) if tasks else "none",
            "seededTaskIds": [],
        }

    policy = resolve_policy(policy_name, queue_path, repo_dir, roadmap_path)
    state = load_json(state_path, {"version": 1})
    completion = get_completion_bucket(state, policy["stateKey"])
    fingerprint = queue_fingerprint(tasks)
    same_queue = completion.get("queueFingerprint") == fingerprint

    diagnostic_inputs = merge_diagnostic_inputs(
        completion,
        invitation_uri=invitation_uri,
        diagnostic_inputs=diagnostic_inputs,
    )

    if (
        completion.get("queueFingerprint") == fingerprint
        and completion.get("validation", {}).get("status") == "passed"
        and has_successful_diagnostic(policy, completion)
        and completion.get("diagnosticInputs", {}) == diagnostic_inputs
    ):
        return {
            "action": "done",
            "reason": "already_validated",
            "queueFingerprint": fingerprint,
            "seededTaskIds": completion.get("seededTaskIds", []),
        }

    if (
        completion.get("queueFingerprint") == fingerprint
        and completion.get("validation", {}).get("status") == "passed"
        and completion.get("followupDecision")
        and same_diagnostic_inputs(completion, diagnostic_inputs)
    ):
        return {
            "action": "hold",
            "reason": completion["followupDecision"],
            "queueFingerprint": fingerprint,
            "seededTaskIds": completion.get("seededTaskIds", []),
        }

    if not (same_queue and completion.get("validation", {}).get("status") == "passed"):
        validation = run_validation(policy.get("validationCommands", []), repo_dir, runner)
        completion.pop("diagnostic", None)
        completion.pop("goal", None)
        completion.pop("diagnosticRunInputs", None)
        completion.pop("seedReason", None)
        completion.pop("diagnosedBugCode", None)
        completion.pop("followupDecision", None)
        completion.pop("followupDecisionAt", None)
        completion.pop("diagnosticSignal", None)
        completion.pop("diagnosticSignalSource", None)
        completion.update(
            {
                "queueFingerprint": fingerprint,
                "validatedAt": datetime.now(timezone.utc).isoformat(),
                "validation": validation,
                "seededTaskIds": [],
                "policy": policy["name"],
            }
        )
    else:
        validation = completion["validation"]
        completion.update(
            {
                "queueFingerprint": fingerprint,
                "seededTaskIds": completion.get("seededTaskIds", []),
                "policy": policy["name"],
            }
        )

    if validation["status"] != "passed":
        save_json(state_path, state)
        return {
            "action": "error",
            "reason": "validation_failed",
            "queueFingerprint": fingerprint,
            "seededTaskIds": [],
        }

    diagnostic = run_diagnostic(policy, repo_dir, runner, diagnostic_inputs)
    if diagnostic is not None:
        completion["diagnostic"] = diagnostic
        completion["goal"] = build_goal_record(policy, diagnostic)
        signal_source, signal = specific_followup_signal(diagnostic)
        if signal:
            completion["diagnosticSignal"] = signal
            completion["diagnosticSignalSource"] = signal_source
        else:
            completion.pop("diagnosticSignal", None)
            completion.pop("diagnosticSignalSource", None)
        if diagnostic["status"] == "completed":
            completion["diagnosticRunInputs"] = dict(diagnostic_inputs)
        if diagnostic["status"] == "input_missing":
            save_json(state_path, state)
            return {
                "action": "error",
                "reason": "diagnostic_input_missing",
                "queueFingerprint": fingerprint,
                "seededTaskIds": [],
            }
        if diagnostic["status"] not in {"completed"}:
            save_json(state_path, state)
            return {
                "action": "error",
                "reason": "diagnostic_failed",
                "queueFingerprint": fingerprint,
                "seededTaskIds": [],
            }

        followup_plan = diagnostic_followup_plan(policy, tasks, diagnostic, completion)
        if followup_plan["kind"] == "error":
            save_json(state_path, state)
            return {
                "action": "error",
                "reason": "diagnostic_bug_unmapped",
                "queueFingerprint": fingerprint,
                "seededTaskIds": [],
            }
        if followup_plan["kind"] == "hold":
            completion["followupDecision"] = followup_plan["reason"]
            completion["followupDecisionAt"] = datetime.now(timezone.utc).isoformat()
            save_json(state_path, state)
            return {
                "action": "hold",
                "reason": followup_plan["reason"],
                "queueFingerprint": fingerprint,
                "seededTaskIds": [],
            }
        if followup_plan["kind"] == "seed":
            seed_spec = followup_plan["seedSpec"]
            return finalize_seeded_queue(
                queue=queue,
                tasks=tasks,
                queue_path=queue_path,
                state=state,
                state_path=state_path,
                completion=completion,
                seed_spec=seed_spec,
                format_context={
                    "goal": goal_description(policy) or "",
                    "summary": diagnostic.get("summary", ""),
                    "bug_code": diagnostic.get("bugCode", ""),
                    "invitation_uri": diagnostic_inputs.get("invitation_uri", ""),
                    "specific_signal": followup_plan["signal"] or "",
                    "specific_signal_source": followup_plan["signalSource"] or "",
                },
                queue_fingerprint_value=fingerprint,
                diagnosed_bug_code=diagnostic.get("bugCode"),
                specific_signal=followup_plan["signal"],
                specific_signal_source=followup_plan["signalSource"],
            )

    if should_seed_followup(policy, tasks):
        return finalize_seeded_queue(
            queue=queue,
            tasks=tasks,
            queue_path=queue_path,
            state=state,
            state_path=state_path,
            completion=completion,
            seed_spec=policy["followup"],
            format_context={"goal": goal_description(policy) or ""},
            queue_fingerprint_value=fingerprint,
        )

    save_json(state_path, state)
    return {
        "action": "done",
        "reason": "queue_validated",
        "queueFingerprint": fingerprint,
        "seededTaskIds": [],
    }


def main():
    parser = argparse.ArgumentParser(
        description="Handle the end of a queue generically: validate the finished slice, optionally seed a follow-up queue, and report the next action."
    )
    parser.add_argument("--queue-path", type=Path, required=True)
    parser.add_argument("--state-path", type=Path, default=DEFAULT_STATE_PATH)
    parser.add_argument("--repo-dir", type=Path, default=DEFAULT_REPO_DIR)
    parser.add_argument("--policy", default=None)
    parser.add_argument("--roadmap-path", type=Path, default=None)
    parser.add_argument("--invitation-uri", default=None)
    parser.add_argument(
        "--input",
        action="append",
        default=[],
        help="Optional diagnostic input in key=value form. Can be repeated.",
    )
    args = parser.parse_args()

    diagnostic_inputs = {}
    for raw_input in args.input:
        key, separator, value = raw_input.partition("=")
        if not separator or not key:
            raise SystemExit(f"invalid --input value: {raw_input}")
        diagnostic_inputs[key] = value

    result = run_completion_supervisor(
        queue_path=args.queue_path,
        state_path=args.state_path,
        repo_dir=args.repo_dir,
        policy_name=args.policy,
        roadmap_path=args.roadmap_path,
        invitation_uri=args.invitation_uri,
        diagnostic_inputs=diagnostic_inputs,
    )
    print(f"action={result['action']}")
    print(f"reason={result['reason']}")
    print(f"queue_fingerprint={result['queueFingerprint']}")
    print(f"seeded_task_ids={','.join(result.get('seededTaskIds', [])) or 'none'}")
    if "missingInputs" in result:
        print(f"missing_inputs={','.join(result['missingInputs'])}")
    if "diagnostic" in result:
        print(f"diagnostic_result={result['diagnostic']}")


if __name__ == "__main__":
    main()
