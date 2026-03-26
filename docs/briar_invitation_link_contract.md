# Briar Invitation Link Contract

## Scope
- Defines the parser contract for the first invitation-to-chat onboarding slice.
- This contract is implemented by `BriarInvitationLinkParser` and covered by unit tests.

## Supported Input Shapes
- Raw Briar link:
  - `briar://<payload>`
- Wrapped Arise deep link:
  - `arise://briar/invite?link=<url-encoded-briar-link>[&alias=<url-encoded-alias>][&inviteId=<id>]`
- Wrapped Arise HTTPS link:
  - `https://arise.app/briar/invite?link=<url-encoded-briar-link>[&alias=<url-encoded-alias>][&inviteId=<id>]`
  - `https://www.arise.app/briar/invite?...`

## Required Fields
- Wrapped links must include a non-blank `link` query param.
- The decoded `link` value must be a valid Briar link (`briar://` prefix with non-blank payload).

## Optional Fields
- `alias`: optional contact alias suggestion.
- `inviteId` (preferred) or `invite_id` (legacy): optional duplicate-detection token.

## Duplicate Handling
- If `inviteId` or `invite_id` is present and non-blank:
  - `duplicateKey = "invite:<lowercased-id>"`
- Otherwise:
  - `duplicateKey = "link:<normalized-briar-link>"`

## Invalid Result Reasons
- `EMPTY_INPUT`: input is blank after trimming.
- `UNSUPPORTED_LINK`: unsupported scheme/host/path shape.
- `MISSING_REQUIRED_BRIAR_LINK`: wrapped invitation missing `link`.
- `INVALID_BRIAR_LINK`: wrapped or raw Briar value fails `briar://<payload>` validation.

## Normalization Rules
- Leading/trailing whitespace is trimmed.
- Briar scheme is normalized to lowercase (`briar://`).
- Alias is trimmed; blank alias resolves to `null`.
- Query parameters are URL-decoded as UTF-8.
