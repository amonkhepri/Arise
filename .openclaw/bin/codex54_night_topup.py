#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

ROOT = Path('/Users/amunratis/AndroidStudioProjects/Arise-autowork')
QUEUE_PATH = ROOT / '.openclaw' / 'codex54_tasks.json'
TOOLKIT_DOC = 'docs/invitation_artifact_toolkit.md'
ROADMAP_DOC = 'docs/briar_invitation_chat_roadmap.md'
ARTIFACTS_HELPER = 'agent-tools/invitation-link-artifacts.sh'
SUMMARY_HELPER = 'agent-tools/invitation-link-summary.sh'

CATALOG = [
    {
        'slug': 'latest-prefix',
        'helperPath': 'agent-tools/invitation-link-prefix.sh',
        'helperTitle': 'Add invitation prefix helper',
        'helperCommit': 'autowork(agent-tools): add invitation prefix helper',
        'docTitle': 'Document invitation prefix helper in toolkit',
        'docHeading': 'Invitation Artifact Prefix',
        'docCommit': 'autowork(docs): document invitation prefix helper',
        'roadmapTitle': 'Update roadmap with invitation prefix helper',
        'roadmapCommit': 'autowork(roadmap): log invitation prefix helper',
        'roadmapTag': 'invitation-link-prefix',
        'helperInstructions': [
            'Create a small bash helper that prints the prefix for the latest saved invitation capture bundle.',
            'Support --help and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the existing invitation-link-artifacts.sh helper to resolve the newest invitation log before deriving the prefix.',
            'Print one labeled line with the latest prefix and keep the helper focused on prefix lookup only.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to latest prefix lookup.',
            'Check that it derives the prefix from the latest invitation capture naming pattern.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document when to use agent-tools/invitation-link-prefix.sh and show the expected command shape.',
        'roadmapSummary': 'Added agent-tools/invitation-link-prefix.sh and documented it in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'recent-bundles',
        'helperPath': 'agent-tools/invitation-link-bundles.sh',
        'helperTitle': 'Add invitation bundle list helper',
        'helperCommit': 'autowork(agent-tools): add invitation bundle list helper',
        'docTitle': 'Document invitation bundle list helper in toolkit',
        'docHeading': 'Invitation Artifact Bundles',
        'docCommit': 'autowork(docs): document invitation bundle list helper',
        'roadmapTitle': 'Update roadmap with invitation bundle list helper',
        'roadmapCommit': 'autowork(roadmap): log invitation bundle list helper',
        'roadmapTag': 'invitation-link-bundles',
        'helperInstructions': [
            'Create a small bash helper that lists recent invitation capture bundles from agent-tools/screenshots.',
            'Support --help plus an optional --limit N flag, and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Print the newest bundles first using the timestamp prefix produced by invitation-link-capture.sh.',
            'For each listed bundle, print labeled lines for PREFIX, LOG, and SCREENSHOT.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to listing saved invitation bundles.',
            'Check that bundles are ordered newest first and include explicit paths.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to list recent invitation bundles with agent-tools/invitation-link-bundles.sh and the --limit flag.',
        'roadmapSummary': 'Added agent-tools/invitation-link-bundles.sh and documented recent bundle lookup in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'log-tail',
        'helperPath': 'agent-tools/invitation-link-log-tail.sh',
        'helperTitle': 'Add invitation log tail helper',
        'helperCommit': 'autowork(agent-tools): add invitation log tail helper',
        'docTitle': 'Document invitation log tail helper in toolkit',
        'docHeading': 'Invitation Log Tail',
        'docCommit': 'autowork(docs): document invitation log tail helper',
        'roadmapTitle': 'Update roadmap with invitation log tail helper',
        'roadmapCommit': 'autowork(roadmap): log invitation log tail helper',
        'roadmapTag': 'invitation-link-log-tail',
        'helperInstructions': [
            'Create a small bash helper that prints the tail of the latest invitation capture log.',
            'Support --help and an optional --lines N flag, and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the existing invitation-link-artifacts.sh helper to resolve the newest log file.',
            'Print the log path before the requested tail output.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to tailing the latest saved invitation log.',
            'Check that the --lines flag is explicit and the latest log path is printed.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to tail the latest invitation log with agent-tools/invitation-link-log-tail.sh and --lines.',
        'roadmapSummary': 'Added agent-tools/invitation-link-log-tail.sh and documented latest log tail usage in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'log-search',
        'helperPath': 'agent-tools/invitation-link-log-search.sh',
        'helperTitle': 'Add invitation log search helper',
        'helperCommit': 'autowork(agent-tools): add invitation log search helper',
        'docTitle': 'Document invitation log search helper in toolkit',
        'docHeading': 'Invitation Log Search',
        'docCommit': 'autowork(docs): document invitation log search helper',
        'roadmapTitle': 'Update roadmap with invitation log search helper',
        'roadmapCommit': 'autowork(roadmap): log invitation log search helper',
        'roadmapTag': 'invitation-link-log-search',
        'helperInstructions': [
            'Create a small bash helper that searches the latest invitation capture log for a supplied pattern.',
            'Require one PATTERN argument unless --help is requested; optionally support --ignore-case.',
            'Keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log and print matching lines with line numbers or exit non-zero with a clear no-match message.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to searching the latest saved invitation log.',
            'Check that argument validation is explicit and matches include line numbers.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to search the latest invitation log with agent-tools/invitation-link-log-search.sh and an example pattern.',
        'roadmapSummary': 'Added agent-tools/invitation-link-log-search.sh and documented targeted invitation log search in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'report',
        'helperPath': 'agent-tools/invitation-link-report.sh',
        'helperTitle': 'Add invitation artifact report helper',
        'helperCommit': 'autowork(agent-tools): add invitation artifact report helper',
        'docTitle': 'Document invitation artifact report helper in toolkit',
        'docHeading': 'Invitation Artifact Report',
        'docCommit': 'autowork(docs): document invitation artifact report helper',
        'roadmapTitle': 'Update roadmap with invitation artifact report helper',
        'roadmapCommit': 'autowork(roadmap): log invitation artifact report helper',
        'roadmapTag': 'invitation-link-report',
        'helperInstructions': [
            'Create a small bash helper that prints a Markdown report for the latest invitation capture bundle.',
            'Support --help plus an optional --lines N flag for the included log tail, and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log and screenshot paths.',
            'Print a short Markdown summary with the bundle prefix, artifact paths, and a compact log tail.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to printing a report for the latest saved invitation bundle.',
            'Check that the Markdown output includes the latest paths and a compact log tail.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to print a Markdown report for the latest invitation bundle with agent-tools/invitation-link-report.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-report.sh and documented latest bundle reporting in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'json',
        'helperPath': 'agent-tools/invitation-link-json.sh',
        'helperTitle': 'Add invitation artifact json helper',
        'helperCommit': 'autowork(agent-tools): add invitation artifact json helper',
        'docTitle': 'Document invitation artifact json helper in toolkit',
        'docHeading': 'Invitation Artifact JSON',
        'docCommit': 'autowork(docs): document invitation artifact json helper',
        'roadmapTitle': 'Update roadmap with invitation artifact json helper',
        'roadmapCommit': 'autowork(roadmap): log invitation artifact json helper',
        'roadmapTag': 'invitation-link-json',
        'helperInstructions': [
            'Create a small bash helper that emits JSON metadata for the latest invitation capture bundle.',
            'Support --help and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log and screenshot paths plus simple file-size metadata.',
            'Print valid JSON to stdout and keep the helper focused on latest bundle metadata only.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to JSON metadata for the latest saved bundle.',
            'Check that the output includes the latest paths and simple size metadata.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to emit JSON metadata for the latest invitation bundle with agent-tools/invitation-link-json.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-json.sh and documented latest bundle JSON output in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'bundle-check',
        'helperPath': 'agent-tools/invitation-link-bundle-check.sh',
        'helperTitle': 'Add invitation bundle check helper',
        'helperCommit': 'autowork(agent-tools): add invitation bundle check helper',
        'docTitle': 'Document invitation bundle check helper in toolkit',
        'docHeading': 'Invitation Bundle Check',
        'docCommit': 'autowork(docs): document invitation bundle check helper',
        'roadmapTitle': 'Update roadmap with invitation bundle check helper',
        'roadmapCommit': 'autowork(roadmap): log invitation bundle check helper',
        'roadmapTag': 'invitation-link-bundle-check',
        'helperInstructions': [
            'Create a small bash helper that verifies the latest invitation capture bundle exists and both files are non-empty.',
            'Support --help and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log and screenshot paths.',
            'Print a short status summary with explicit result and file size lines.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to verifying the latest saved bundle.',
            'Check that it reports explicit status and file sizes for both artifacts.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to validate the latest invitation bundle with agent-tools/invitation-link-bundle-check.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-bundle-check.sh and documented latest bundle validation in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'screenshot-info',
        'helperPath': 'agent-tools/invitation-link-screenshot-info.sh',
        'helperTitle': 'Add invitation screenshot info helper',
        'helperCommit': 'autowork(agent-tools): add invitation screenshot info helper',
        'docTitle': 'Document invitation screenshot info helper in toolkit',
        'docHeading': 'Invitation Screenshot Info',
        'docCommit': 'autowork(docs): document invitation screenshot info helper',
        'roadmapTitle': 'Update roadmap with invitation screenshot info helper',
        'roadmapCommit': 'autowork(roadmap): log invitation screenshot info helper',
        'roadmapTag': 'invitation-link-screenshot-info',
        'helperInstructions': [
            'Create a small bash helper that prints metadata for the latest invitation capture screenshot.',
            'Support --help and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation screenshot path.',
            'Print the screenshot path, file size, and dimensions when available.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to latest screenshot metadata.',
            'Check that the output includes path, size, and dimensions when available.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to inspect the latest invitation screenshot with agent-tools/invitation-link-screenshot-info.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-screenshot-info.sh and documented latest screenshot inspection in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'log-stats',
        'helperPath': 'agent-tools/invitation-link-log-stats.sh',
        'helperTitle': 'Add invitation log stats helper',
        'helperCommit': 'autowork(agent-tools): add invitation log stats helper',
        'docTitle': 'Document invitation log stats helper in toolkit',
        'docHeading': 'Invitation Log Stats',
        'docCommit': 'autowork(docs): document invitation log stats helper',
        'roadmapTitle': 'Update roadmap with invitation log stats helper',
        'roadmapCommit': 'autowork(roadmap): log invitation log stats helper',
        'roadmapTag': 'invitation-link-log-stats',
        'helperInstructions': [
            'Create a small bash helper that prints summary statistics for the latest invitation capture log.',
            'Support --help and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log path.',
            'Print the log path, byte count, line count, and the first and last log lines.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to latest log statistics.',
            'Check that it reports path, byte count, line count, and boundary lines.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to inspect latest invitation log statistics with agent-tools/invitation-link-log-stats.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-log-stats.sh and documented latest log statistics in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'bundle-age',
        'helperPath': 'agent-tools/invitation-link-bundle-age.sh',
        'helperTitle': 'Add invitation bundle age helper',
        'helperCommit': 'autowork(agent-tools): add invitation bundle age helper',
        'docTitle': 'Document invitation bundle age helper in toolkit',
        'docHeading': 'Invitation Bundle Age',
        'docCommit': 'autowork(docs): document invitation bundle age helper',
        'roadmapTitle': 'Update roadmap with invitation bundle age helper',
        'roadmapCommit': 'autowork(roadmap): log invitation bundle age helper',
        'roadmapTag': 'invitation-link-bundle-age',
        'helperInstructions': [
            'Create a small bash helper that prints the age of the latest invitation capture bundle based on its timestamp prefix.',
            'Support --help and keep the helper read-only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log path and parse the capture prefix produced by invitation-link-capture.sh.',
            'Print the prefix and the bundle age in seconds.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to latest bundle age reporting.',
            'Check that it parses the existing timestamp prefix and reports the age explicitly.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to inspect the age of the latest invitation bundle with agent-tools/invitation-link-bundle-age.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-bundle-age.sh and documented latest bundle age lookup in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'prune-preview',
        'helperPath': 'agent-tools/invitation-link-prune-preview.sh',
        'helperTitle': 'Add invitation prune preview helper',
        'helperCommit': 'autowork(agent-tools): add invitation prune preview helper',
        'docTitle': 'Document invitation prune preview helper in toolkit',
        'docHeading': 'Invitation Prune Preview',
        'docCommit': 'autowork(docs): document invitation prune preview helper',
        'roadmapTitle': 'Update roadmap with invitation prune preview helper',
        'roadmapCommit': 'autowork(roadmap): log invitation prune preview helper',
        'roadmapTag': 'invitation-link-prune-preview',
        'helperInstructions': [
            'Create a small bash helper that previews which invitation capture bundles would be pruned while keeping the newest N bundles.',
            'Support --help plus an optional --keep N flag, and keep the helper read-only: no deletion, no adb, no device interaction, no app launch.',
            'Inspect saved invitation bundle files under agent-tools/screenshots.',
            'Print the prefixes and artifact paths that would be removed, or a clear none result if nothing would be pruned.'
        ],
        'helperReviewFocus': [
            'Check that the helper is read-only and limited to prune preview only.',
            'Check that it honors --keep and never deletes any files.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to preview pruning older invitation bundles with agent-tools/invitation-link-prune-preview.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-prune-preview.sh and documented prune preview usage in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'open-bundle',
        'helperPath': 'agent-tools/invitation-link-open.sh',
        'helperTitle': 'Add invitation artifact open helper',
        'helperCommit': 'autowork(agent-tools): add invitation artifact open helper',
        'docTitle': 'Document invitation artifact open helper in toolkit',
        'docHeading': 'Invitation Artifact Open',
        'docCommit': 'autowork(docs): document invitation artifact open helper',
        'roadmapTitle': 'Update roadmap with invitation artifact open helper',
        'roadmapCommit': 'autowork(roadmap): log invitation artifact open helper',
        'roadmapTag': 'invitation-link-open',
        'helperInstructions': [
            'Create a small bash helper that opens the latest invitation capture log and screenshot with the local macOS open command.',
            'Support --help and keep the helper limited to local artifact opening only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log and screenshot paths.',
            'Print which artifact paths are being opened before invoking open.'
        ],
        'helperReviewFocus': [
            'Check that the helper is limited to opening the latest saved artifacts locally.',
            'Check that it prints the selected paths before invoking open.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to open the latest invitation artifacts locally with agent-tools/invitation-link-open.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-open.sh and documented local artifact opening in docs/invitation_artifact_toolkit.md.'
    },
    {
        'slug': 'copy-paths',
        'helperPath': 'agent-tools/invitation-link-copy-paths.sh',
        'helperTitle': 'Add invitation artifact copy helper',
        'helperCommit': 'autowork(agent-tools): add invitation artifact copy helper',
        'docTitle': 'Document invitation artifact copy helper in toolkit',
        'docHeading': 'Invitation Artifact Copy',
        'docCommit': 'autowork(docs): document invitation artifact copy helper',
        'roadmapTitle': 'Update roadmap with invitation artifact copy helper',
        'roadmapCommit': 'autowork(roadmap): log invitation artifact copy helper',
        'roadmapTag': 'invitation-link-copy-paths',
        'helperInstructions': [
            'Create a small bash helper that copies the latest invitation artifact paths to the local macOS clipboard.',
            'Support --help and keep the helper limited to local clipboard behavior only: no adb, no device interaction, no file deletion, no app launch.',
            'Use the latest saved invitation log and screenshot paths.',
            'Print a short confirmation after copying the latest artifact paths.'
        ],
        'helperReviewFocus': [
            'Check that the helper is limited to copying the latest saved artifact paths locally.',
            'Check that it uses the latest artifact helper output and prints a clear confirmation.',
            'Check that no unrelated files changed.'
        ],
        'docSummary': 'Document how to copy the latest invitation artifact paths to the clipboard with agent-tools/invitation-link-copy-paths.sh.',
        'roadmapSummary': 'Added agent-tools/invitation-link-copy-paths.sh and documented local clipboard support in docs/invitation_artifact_toolkit.md.'
    },
]


def load_queue():
    with QUEUE_PATH.open('r', encoding='utf-8') as handle:
        return json.load(handle)


def save_queue(data):
    with QUEUE_PATH.open('w', encoding='utf-8') as handle:
        json.dump(data, handle, indent=2)
        handle.write('\n')


def task_number(task_id: str) -> int:
    return int(task_id.split('-')[1])


def next_task_id(tasks):
    highest = max(task_number(task['id']) for task in tasks)
    return f'AT-{highest + 1:03d}'


def unfinished_count(tasks):
    return sum(1 for task in tasks if task['status'] != 'done')


def existing_helper_paths(tasks):
    paths = set()
    for task in tasks:
        for path in task.get('allowedPaths', []):
            if path.startswith('agent-tools/'):
                paths.add(path)
    return paths


def queue_remaining_specs(tasks):
    seen = existing_helper_paths(tasks)
    return [spec for spec in CATALOG if spec['helperPath'] not in seen]


def make_helper_task(task_id, blocked_by, spec):
    task = {
        'id': task_id,
        'status': 'blocked' if blocked_by else 'open',
        'title': spec['helperTitle'],
        'allowedPaths': [spec['helperPath']],
        'contextFiles': [ARTIFACTS_HELPER, SUMMARY_HELPER],
        'instructions': spec['helperInstructions'],
        'validation': [
            f"bash -n {spec['helperPath']}",
            f"{spec['helperPath']} --help",
        ],
        'commitMessage': spec['helperCommit'],
        'reviewApproveMessage': f'autowork(reviewer): approve {task_id}',
        'reviewNeedsFixMessage': f'autowork(reviewer): request fixes for {task_id}',
        'reviewFocus': spec['helperReviewFocus'],
        'scaffoldCommand': f"./.openclaw/bin/codex54_seed_invitation_helper.sh {spec['slug']}",
    }
    if blocked_by:
        task['blockedBy'] = blocked_by
    return task


def make_doc_task(task_id, blocked_by, spec):
    section_heading = spec['docHeading']
    basename = Path(spec['helperPath']).name
    task = {
        'id': task_id,
        'status': 'blocked',
        'blockedBy': blocked_by,
        'title': spec['docTitle'],
        'allowedPaths': [TOOLKIT_DOC],
        'contextFiles': [TOOLKIT_DOC, spec['helperPath'], ARTIFACTS_HELPER],
        'instructions': [
            f"Add a compact section titled '{section_heading}' to {TOOLKIT_DOC}.",
            f"If {TOOLKIT_DOC} does not exist yet, create it with a short intro and then add the new section.",
            spec['docSummary'],
            'Keep the addition concise and limited to invitation artifact tooling.'
        ],
        'validation': [
            f"rg -n \"{section_heading}|{basename}\" {TOOLKIT_DOC}"
        ],
        'commitMessage': spec['docCommit'],
        'reviewApproveMessage': f'autowork(reviewer): approve {task_id}',
        'reviewNeedsFixMessage': f'autowork(reviewer): request fixes for {task_id}',
        'reviewFocus': [
            'Check that the new toolkit section is concise and invitation-specific.',
            f'Check that it references {basename} with a realistic command shape.',
            'Check that no unrelated docs changed.'
        ],
    }
    return task


def make_roadmap_task(task_id, blocked_by, spec):
    basename = Path(spec['helperPath']).stem
    task = {
        'id': task_id,
        'status': 'blocked',
        'blockedBy': blocked_by,
        'title': spec['roadmapTitle'],
        'allowedPaths': [ROADMAP_DOC],
        'contextFiles': [ROADMAP_DOC, spec['helperPath'], TOOLKIT_DOC],
        'instructions': [
            f"Add a concise progress-log entry dated 2026-03-06 that {spec['roadmapSummary']}",
            'Keep backlog ordering unchanged and do not mark additional roadmap items done.',
            'Keep the roadmap edit limited to the progress log.'
        ],
        'validation': [
            f"rg -n \"{basename}\" {ROADMAP_DOC}"
        ],
        'commitMessage': spec['roadmapCommit'],
        'reviewApproveMessage': f'autowork(reviewer): approve {task_id}',
        'reviewNeedsFixMessage': f'autowork(reviewer): request fixes for {task_id}',
        'reviewFocus': [
            'Check that the roadmap entry matches the landed helper and toolkit doc update.',
            'Check that only the progress log changed.',
            'Check that the entry is dated 2026-03-06.'
        ],
    }
    return task


def append_chain(tasks, spec):
    last_task_id = tasks[-1]['id'] if tasks else None
    helper_id = next_task_id(tasks)
    helper_task = make_helper_task(helper_id, last_task_id, spec)
    tasks.append(helper_task)

    doc_id = next_task_id(tasks)
    doc_task = make_doc_task(doc_id, helper_id, spec)
    tasks.append(doc_task)

    roadmap_id = next_task_id(tasks)
    roadmap_task = make_roadmap_task(roadmap_id, doc_id, spec)
    tasks.append(roadmap_task)

    return [helper_id, doc_id, roadmap_id]


def main():
    parser = argparse.ArgumentParser(description='Top up the Codex GPT-5.4 queue with the same curated invitation artifact tasks.')
    parser.add_argument('--target-unfinished', type=int, default=12, help='Try to keep at least this many unfinished tasks queued.')
    parser.add_argument('--max-chains-per-run', type=int, default=3, help='Append at most this many helper/doc/roadmap chains per run.')
    args = parser.parse_args()

    data = load_queue()
    tasks = data['tasks']
    before = unfinished_count(tasks)
    remaining = queue_remaining_specs(tasks)
    added = []

    while unfinished_count(tasks) < args.target_unfinished and remaining and len(added) < args.max_chains_per_run:
        spec = remaining.pop(0)
        chain_ids = append_chain(tasks, spec)
        added.append({'slug': spec['slug'], 'task_ids': chain_ids})

    if added:
        save_queue(data)

    after = unfinished_count(tasks)
    print(f'before_unfinished={before}')
    print(f'after_unfinished={after}')
    print(f'added_chains={len(added)}')
    if added:
        for item in added:
            print(f"added={item['slug']}:{','.join(item['task_ids'])}")
    else:
        print('result=noop')


if __name__ == '__main__':
    main()
