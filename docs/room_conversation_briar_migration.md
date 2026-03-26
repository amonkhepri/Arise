# Room Conversation Store – Briar Metadata Migration

_Purpose: capture the design for extending the Room-backed conversation cache so it can persist
transport-specific metadata (especially Briar aliases/messages) without breaking existing
Firestore timelines._

## Goals

1. Preserve canonical conversations/messages while storing **per-transport aliases** so the router
   can round-trip Briar IDs after the app restarts.
2. Retain existing Firestore data (no destructive migration) and provide a backfill path for
   installs that already carry Firestore-only cache entries.
3. Unblock future transports by generalising the schema instead of baking in Briar-only logic.
4. Keep DAO APIs straightforward so `RoomConversationStore` continues to look like a canonical store
   from the rest of the app’s perspective.

## Current State

`app/src/main/java/com/example/rise/transport/store/RoomConversationEntities.kt`

- `ConversationEntity(id, title, participants)` – no alias info.
- `MessageEntity(canonicalMessageId, conversationId, senderId, recipientId, senderName, body,
  transport, transportMessageId, timestamp)` – transport + transportMessageId already exist, but
  there’s no way to store **conversation-level transport IDs** or additional metadata.
- `ConversationDao` simply rewrites the `messages` table per conversation.

Consequences:
- Router has no persistent place to record the Briar conversation alias returned by
  `BriarChatGateway.ensureConversation`.
- Multi-connector timelines can’t look up transport-specific IDs once the process restarts.

## Proposed Schema Changes

### Conversations

1. **Add columns to `conversations`:**
   - `primaryTransportId : String` – which connector “owns” the canonical record (defaults to
     FIRESTORE).
   - `briarConversationId : String?` – shortcut for active Briar alias.
2. **Introduce `conversation_aliases` table:**
   ```
   conversation_aliases(
     conversationId TEXT NOT NULL,
     transportId TEXT NOT NULL,
     transportConversationId TEXT NOT NULL,
     PRIMARY KEY(conversationId, transportId)
   )
   ```
   - Allows arbitrary transports without bloating the main table.

### Messages

1. Keep existing columns but tighten semantics:
   - `transport` → rename to `transportId` (Room column rename via migration) for clarity.
   - Add `transportMetadata TEXT?` reserved for future JSON payloads (delivery receipts etc.).
2. Ensure indices on `(conversationId, timestamp)` still exist (Room auto indexes primary key; we
   may add explicit index if perf dictates).

### Room Migration Outline

Assuming current DB version `N`, new version `N+1` will:

```sql
ALTER TABLE conversations ADD COLUMN primaryTransportId TEXT NOT NULL DEFAULT 'FIRESTORE';
ALTER TABLE conversations ADD COLUMN briarConversationId TEXT;

CREATE TABLE IF NOT EXISTS conversation_aliases (
    conversationId TEXT NOT NULL,
    transportId TEXT NOT NULL,
    transportConversationId TEXT NOT NULL,
    PRIMARY KEY(conversationId, transportId)
);

ALTER TABLE messages RENAME COLUMN transport TO transportId;
ALTER TABLE messages ADD COLUMN transportMetadata TEXT;
```

### Backfill Strategy

1. After migration, run a one-time job:
   - For each `ConversationEntity`, insert a `conversation_aliases` row with
     `(conversationId, FIRESTORE, conversationId)` so Firestore maintains parity.
   - If `RoomConversationStore` already knows about Firestore channel IDs (e.g., cached
     `CachedChatChannelEntity`), reuse that alias instead of canonical ID.
2. `RoomConversationStore.ensureConversation` should populate/overwrite the alias row whenever a
   connector reports a new alias (e.g., `BriarChatAdapter.ensureConversation` returns
   canonical+transport IDs).
3. Provide helper methods (`upsertConversationAlias`, `getAlias(conversationId, transportId)`) for
   connectors/router code.

## Code Changes

1. **Entities/DAOs**
   - Update `ConversationEntity` & `MessageEntity`.
   - Add new data classes for `ConversationAliasEntity`.
   - Extend `ConversationDao` with CRUD for aliases + metadata queries.
2. **TypeConverters**
   - Ensure updated converters can handle new columns (e.g., `transportMetadata` may use Moshi/JSON).
3. **RoomConversationStore**
   - When mapping `CanonicalConversation` to `ConversationEntity`, set `primaryTransportId`.
   - After calling connector adapters, persist alias rows.
   - When reading from Room, surface alias map so callers can fetch transport IDs quickly.
4. **Router integration**
   - `TransportRouterImpl.ensureConversation` should read any existing alias before hitting
     connectors (optimises repeated lookups).
   - When a connector upserts messages, include `transportId` + optional metadata to match the new
     schema.

## Testing

1. **Migration test** – use Room’s `MigrationTestHelper` to migrate an `N` schema file, then assert:
   - Columns/tables exist.
   - Existing data remains.
   - Default alias rows inserted for Firestore conversations.
2. **DAO tests** – verify alias CRUD, message insert/retrieve with `transportId`, etc.
3. **RoomConversationStore tests** – ensure alias caching works and Briar aliases survive process
   restarts.
4. **Router integration tests** – confirm Briar conversation alias persists after calling
   `ensureConversation` and new messages keep their transport IDs.

## Open Questions / Follow-ups

- Do we need a composite index on `conversation_aliases` for faster lookups? (Likely yes.)
- Should `transportMetadata` be structured (JSON) or keyed columns (status, deliveryTimestamp)? We
  can start with JSON to avoid schema churn.
- When multiple transports observe the same message, do we dedupe by `canonicalMessageId` only or
  also by `(transportId, transportMessageId)`? (The router currently generates canonical IDs, so
  dedupe stays there.)
- QA plan: add a scripted scenario where HYBRID mode runs, Briar ensures a conversation, app restarts,
  and alias/messages remain accessible.

Once this migration lands, we can proceed to the next sub-step: wiring `BriarConnector` to persist
real messages/aliases and enabling Briar-first routing end-to-end.
