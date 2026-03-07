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
