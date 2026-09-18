# RELIABILITY — MVP-019

## P0 — stored-but-502 duplicate rows

### Root cause (audited)

`durablePublish` (worker/src/index.ts) stored the message in D1, then on a
**permanent** FCM failure (e.g. 404 NotRegistered) returned **HTTP 502 with
`stored: true`**. Two compounding defects:

1. A 5xx after a durable insert teaches clients the publish failed, so they
   retry an event that is already durable.
2. The CLI never sent an `Idempotency-Key`, so the server had no way to
   converge retries — every attempt inserted a fresh row.

Reproduction from the MVP-017.5→018 overnight campaign: one DONE event on a
dead channel produced **8 duplicate rows**; two sessions produced 17.

### Fix — durability and delivery are different outcomes

**Worker** (deployed to prod as `a308d20c`, test as `f2f121a2`):

| Situation | Before | After |
|---|---|---|
| stored + delivered | 200 `push_status:"fcm_accepted"` | unchanged |
| stored + permanent delivery failure (NotRegistered, 4xx) | **502** `stored:true` | **200** `ok:true, stored:true, push_status:"failed", delivery:{retryable:false}` |
| stored + transient failure | 502 `push_status:"pending"` | 502 `pending` + `delivery:{retryable:true}` (retries converge via key) |
| replay (same Idempotency-Key + payload) | 200 `idempotent_replay` | 200 replay **+ `push_status` + `error` carried over** |

Failure-injection paths (`failAt`) are untouched — they intentionally model
lost responses.

**CLI** (pigeonhub/core.py, also covers connectors via the shared module):

- Every publish carries `Idempotency-Key = sha256(canonical payload)[:40]` —
  stable across retries of one logical event, unique per event.
- Retry only on network error / 429 / 5xx. `stored: true` (any status) ends
  the loop: stored-but-undelivered is a success for the transport.
- `PublishResult` gained `push_status` / `delivered` / `stale_pairing`.
- Output: `[pigeonhub] DONE saved, delivery failed` instead of a fake `OK` or
  a retry storm.

### Verification

- Python unit tests (new): stored-failed → single request, no retry;
  flaky-503 → retries converge with one stable key; distinct events →
  distinct keys. Suite: 16/16.
- **Real dead channel** (production, the exact duplicate-generator): attempt 1
  → 200 `stored:true, push_status:"failed", retryable:false` (NotRegistered in
  `error`); attempt 2 (same key) → `idempotent_replay:true`, same `message_id`.
  **One row per logical event** — verified in D1: 3 test events → 3 rows
  (versus 8 rows for one event the night before).
- Healthy path unchanged: live-channel publishes still 200 `fcm_accepted`
  (device cards updated throughout the campaign).

## P0 — stale credential / dead channel UX

- **Detection**: delivery failure with FCM `NotRegistered`/`UNREGISTERED`
  detail — the device installation no longer exists, so the pairing cannot
  deliver regardless of retries.
- **CLI behavior**: the event is still saved (durability), the line reads
  `saved, delivery failed`, and stderr prints exact guidance:
  `This PC's pairing can no longer reach your phone. … Run: pigeonhub login`.
- Verified end-to-end on the dead channel (both `notify` and job events).
- Division of labor: worker reports delivery outcome as data; CLI renders the
  human explanation; Android keeps showing stored jobs (they are durable).

## P1 — stale RUNNING jobs (policy)

**Analysis**: the worker cannot distinguish "alive but silent" (plain
`pigeonhub run` emits no progress; coalesced progress updates health/`job_states`
but not the message row) from "process dead". Marking stale RUNNING jobs FAILED
server-side would fabricate terminal states and push users about jobs that may
still be running (a 5-hour render). Rejected.

**Chosen policy — soft degradation with the existing model**:

- Threshold: `STALE_RUNNING_MS = 2h` since the newest event of the job.
- Rendering: RUNNING cards older than the threshold show an italic
  "No updates for X" line (KO: "X 동안 업데이트 없음") below the elapsed line.
- No new job state, no server mutation, **no push** — attention policy is
  untouched; dismissal stays with the existing tombstone delete.
- Pure function `RelativeTime.staleNoUpdatesRes` (threshold/bucket/clock-skew
  unit-tested); card wiring in `JobCard`.

**Future option (documented, not built)**: a CLI keepalive ping for long silent
jobs would make server-side staleness real; requires worker + CLI + coalescing
churn and is not needed to de-risk the beta.

## P2 — copy audit

- Agent cards previously shared one merged health blob — Grok (never used)
  showed "Last event: just now" copied from CLI activity. Now each agent card
  shows only its own source's health; a never-used agent shows **no health
  line at all** (verified on device: Grok has none, Codex/Claude/ZCode show
  their real last events).
- Creative cards intentionally keep the shared CLI health line (their pathway
  IS the CLI); noted in ICON-SYSTEM/ANDROID-UI.
