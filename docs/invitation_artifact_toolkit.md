# Invitation Artifact Toolkit

## Invitation Artifact Prefix

The **Invitation Artifact Prefix** helper (`invitation-link-prefix.sh`) generates a URL prefix that can be appended to invitation links.  The script accepts a domain or a full base URL and outputs a standard prefix used throughout the project.

### Usage

```sh
# Generate a prefix for the domain example.com
./invitation-link-prefix.sh example.com
# → https://example.com/invite
```

The script is intentionally lightweight and can be invoked from build scripts, CI pipelines, or the Android app to keep invitation URLs consistent.

> **Note:** The prefix is a constant part of invitation links; the rest of the link (the invitation token) is appended by the caller.

---

For more details, see the implementation in the `scripts` directory.
## Invitation Artifact Bundles

This section explains how to list recent invitation bundles using the
`agent-tools/invitation-link-bundles.sh` script. The script outputs a
list of invitation bundle identifiers, optionally limited with the
`--limit` flag.

### Usage

```sh
# List the 10 most recent bundles
./agent-tools/invitation-link-bundles.sh --limit 10
# → bundle12345
```

The output is suitable for use in scripts or manual inspection.
## Invitation Log Tail

The `agent-tools/invitation-link-log-tail.sh` script prints the tail of the latest invitation capture log. It supports a `--lines` flag to specify how many lines to output (default is 40).

```sh
# Tail the last 40 lines of the most recent invitation log
./agent-tools/invitation-link-log-tail.sh --lines 40
```

This helper is useful for debugging or inspecting the most recent invitation capture without scrolling through the entire log file.
## Invitation Log Search

The `agent-tools/invitation-link-log-search.sh` script allows searching the most recent invitation capture log for a specific pattern. It accepts a mandatory `PATTERN` argument and optional `--ignore-case` flag.

```sh
# Search for the token `abc123` case‑sensitively
./agent-tools/invitation-link-log-search.sh abc123
# → LOG: /path/to/log
# 123: https://example.com/invite/abc123

# Search ignoring case
./agent-tools/invitation-link-log-search.sh --ignore-case "invitation token"
```

The script prints the log file location and each matching line with its line number. If no matches are found, it exits with a non‑zero status.
## Invitation Artifact Report

The `agent-tools/invitation-link-report.sh` script prints a Markdown summary for the most recent invitation capture bundle. It includes the bundle prefix, the log file path, a screenshot reference, and a tail of the log.

**Usage**

```sh
./agent-tools/invitation-link-report.sh [--lines N]
```

* `--lines N` – number of log lines to include in the log tail (default 20).

The output is ready to be copied into Markdown documentation or displayed directly in the terminal.
## Invitation Artifact JSON

The `agent-tools/invitation-link-json.sh` script prints JSON metadata for the latest invitation capture bundle. The output includes the following fields:

- `prefix`: bundle prefix
- `log`: path to the log file
- `screenshot`: path to the screenshot image
- `logBytes`: size of the log file in bytes
- `screenshotBytes`: size of the screenshot in bytes

**Usage**

```sh
./agent-tools/invitation-link-json.sh
```

**Example Output**

```json
{
  "prefix": "https://example.com/invite",
  "log": "/tmp/invitation-2026-03-07.log",
  "screenshot": "/tmp/invitation-2026-03-07.png",
  "logBytes": 12345,
  "screenshotBytes": 6789
}
```

The JSON output is convenient for programmatic consumption or embedding in documentation.
