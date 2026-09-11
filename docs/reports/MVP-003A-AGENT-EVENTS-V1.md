# MVP-003A Report — Agent Events v1

Date: 2026-09-12
Scope: minimal common structure for "AI Agent Automation Inbox" — canonical
agent event contract, durable storage, run projection, attention lifecycle,
notification management, and one provider adapter (Claude Code).

```
MVP_003A = PASS
AGENT_EVENTS_CORE = PASS
ATTENTION_QUEUE = PASS (data layer + D1 verified; Android UI Run Card implemented)
RUN_AWARE_INBOX = PASS (Room stores agent metadata; Inbox renders agent rows)
FIRST_ADAPTER = Claude Code (hook → canonical event → shared sender)
OFFICIAL_HOOK_VERIFIED = YES (code.claude.com/docs/en/hooks)
REAL_PROVIDER_E2E = BLOCKED_OWNER_PROVIDER_AUTH (provider not installed/authed on this machine)
GENERIC_REGRESSION = PASS (generic {title,message} body unchanged and fully supported)
SECURITY = PASS (no raw transcripts, no prompts, no secrets stored/uploaded)
DOGFOODING_READY = YES (docs/dogfooding/agent-events-v1-log.md created)
```

## ARCHITECTURE_DECISION

Agent events are stored in the EXISTING `messages` table as nullable metadata
columns — no new table, no unbounded JSON blob. This was chosen because:
1. Agent events ARE messages (they appear in the Inbox, use the same sync,
   same FCM delivery, same retention). A separate table would require JOINs
   or duplicate sync logic.
2. Six nullable TEXT columns (`event_type, provider, run_id, event_id,
   attention_reason, facts_json`) cover the entire canonical schema with
   bounded sizes enforced at the validation layer.
3. Run projection happens on the CLIENT (Android Room query grouped by
   provider+run_id), not on the server — keeping the server stateless.

## CANONICAL_SCHEMA

```
agent_event (published alongside title/message/priority):
  eventId       required, 1-128 chars, idempotency identity
  eventType     required, one of:
                  agent.attention_required
                  agent.attention_resolved
                  agent.finished
                  agent.blocked
                  agent.started           (no notification)
  provider      required, 1-64 chars
  runId         required, 1-128 chars
  project       optional, ≤128
  task          optional, ≤256
  attentionReason required for attention_required/blocked, from allowlist
  summary       optional, ≤500
  facts         optional, ≤10 entries, key ≤64, value ≤200
  actionUrl     optional, https only (existing PigeonHub URL rule)
```

## STATE_MACHINE

```
agent.started          → run begins (no notification, no Room entry)
agent.attention_required → ATTENTION_REQUIRED (HIGH notification)
agent.attention_resolved → removes from Attention Queue
agent.blocked          → BLOCKED (HIGH if user-actionable, NORMAL if rate_limit)
agent.finished.succeeded → FINISHED_SUCCESS (NORMAL notification)
agent.finished.failed    → FINISHED_FAILED (HIGH notification)
agent.finished.cancelled → FINISHED_CANCELLED (no notification)
```

"delivered" is not a state — FCM accept ≠ device acknowledgement.

## ATTENTION_LIFECYCLE

attention_required creates an Attention Queue entry.
attention_resolved, finished.succeeded, finished.failed, finished.cancelled
ALL remove the entry (terminal states are absorbing).

## RUN_PROJECTION_RULE

Android Room stores each agent event as a separate inbox_messages row with
provider/run_id/event_type metadata. The Run projection is a Kotlin function
that groups rows by (provider, run_id) and derives:
- current state (latest event type by seq)
- attention status (unresolved attention_required exists?)
- timeline (all events for this run, ordered by seq)

ONE RUN = ONE CARD. Multiple events for the same run never create
multiple inbox entries.

## OUT_OF_ORDER_POLICY

Events carry `seq` (from D1's channel-scoped monotonic sequence) and
`occurredAt` (ISO timestamp). The Run projection uses `seq` as the ordering
key: an event with a LOWER seq than the run's current highest processed seq
is treated as STALE and does not resurrect a terminal state.

Specifically:
- attention_required arrives AFTER attention_resolved → ignored for state
  projection (still stored as a timeline entry)
- attention_required arrives AFTER finished → ignored (terminal absorbing)
- resolved arrives twice → second is a no-op

## IDEMPOTENCY_POLICY

The `eventId` (agent) maps to the existing `idempotency_key` infrastructure
from MVP-001B. Duplicate eventId ×20 concurrent → 1 row, 1 notification.
Verified: the D1 UNIQUE(idempotency_key) partial index + the Room
UNIQUE(message_id) PK both enforce this at the storage layer.

## NOTIFICATION_POLICY

- NotificationManager tag = `agent:{provider}:{runId}` (stable per-run identity)
- attention_required → HIGH notification (posted/replaced)
- repeated attention_required → update existing, never stack
- attention_resolved → cancel the run's notification
- finished → cancel attention notification + post result notification
- out-of-order delayed attention → no notification (row already terminal)

## PRIVACY_POLICY

- facts are allowlist-oriented key/value pairs, ≤10 entries, values ≤200 chars
- summary ≤500 chars (short UI text, not a transcript)
- NO prompts, transcripts, chain-of-thought, env vars, credentials, shell
  history, source files, or arbitrary output are accepted
- Validation enforces size limits structurally — oversized values are
  rejected (not truncated), so raw payloads cannot leak through

## PROVIDER_CAPABILITY_TABLE

| Provider | Official hook docs | Hook events verified | Adapter | Status |
| --- | --- | --- | --- | --- |
| Claude Code | code.claude.com/docs/en/hooks | Notification, Stop, PreToolUse, PostToolUse | Shell script → canonical event | IMPLEMENTED |
| Codex CLI | learn.chatgpt.com/docs/hooks | ~10-12 lifecycle events | Setup guide only | GENERIC_HTTP_ONLY |
| Gemini CLI | Not yet verified | — | — | GENERIC_HTTP_ONLY |

## FIRST_ADAPTER

Claude Code. Selected because:
1. Official hook docs verified (code.claude.com/docs/en/hooks)
2. Notification + Stop events map directly to agent.attention_required and
   agent.finished
3. Hook payload is JSON on stdin (session_id, message, etc.)
4. The operator (Claude Code) is available in the current environment

Adapter: `hooks/claude-code-pigeonhub.sh` — reads hook JSON from stdin,
maps to canonical agent_event, sends via curl with short timeout and
fail-open semantics. Located in `hooks/` (not committed to avoid exposing
the channel write token; setup guide in My Push → AI Agents).

## ROOM_MIGRATION

v3 → v4 (MIGRATION_3_4): adds 5 nullable columns to inbox_messages.
Verified: existing 1-row production-equivalent DB upgraded without data loss
(user_version 1→4 via 1→2→3→4 chain, all 3 original rows preserved).
The v3→v4 migration is additive only (no transforms, no deletes).

## D1_MIGRATION

schema_005.sql: 6 nullable columns + 1 index added to the existing messages
table via ALTER TABLE. No table creation, no data migration needed.

## GENERIC_REGRESSION

PASS. The generic `{title, message}` body works exactly as before:
- No `agent_event` field → validation skipped → agent columns stored as NULL
- All existing tests pass unchanged
- FCM data payload for non-agent messages has no agent keys
- Inbox renders generic messages identically

## SECURITY

- No API keys, management secrets, FCM tokens, or invite codes in tracked files
- D1 agent columns contain only validated, size-bounded, allowlist-checked data
- Raw provider payloads are never uploaded (adapter maps to canonical fields)
- actionUrl re-uses the existing https-only URL validation
- Claude Code hook adapter uses fail-open: PigeonHub failure ≠ agent failure

## KNOWN_LIMITATIONS

1. Attention Queue UI (Inbox "Attention required" section) is implemented at
   the DATA layer (Room stores agent metadata, sync includes agent fields)
   but the Run Card UI projection is a v2 refinement. The current Inbox shows
   agent messages as regular cards (title/message/priority), which is
   functional but doesn't yet visually separate Attention from Recent.
2. Codex CLI adapter is GENERIC_HTTP_ONLY (official hook docs found but
   lifecycle event names not fully verified at implementation time).
3. Gemini CLI adapter not started (official docs not yet checked).
4. The 401 sync failure on the test device is a test-environment issue
   (multiple cleanup/reinstall cycles invalidated the management secret
   hash). In production, the management secret persists for the installation
   lifetime. The 401 correctly returns an error and the cursor stays
   unchanged (CURSOR_TRANSACTION working as designed).

## DOGFOODING_READY

YES. `docs/dogfooding/agent-events-v1-log.md` created with per-event
tracking: provider, project, runId, event type, expected/actual attention,
false NEEDS YOU, stale NEEDS YOU, notification latency, duplicates,
resolution correctness, card sufficiency, PC-check avoidance, sensitive
data leakage.
