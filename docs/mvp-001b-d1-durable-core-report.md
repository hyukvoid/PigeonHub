# MVP-001B Report — D1 Durable Message Core

Date: 2026-09-11
Scope: introduce Cloudflare D1 (Free) as the durable acceptance layer in front
of the already-verified Worker → FCM transport. The single most important
contract: **a message is stored in D1 (`pending`) BEFORE any FCM call; once
`stored: true` is returned the message survives in D1 no matter what FCM did.**

Not implemented (per scope): Queues, Cron retry, Firebase Auth, user accounts,
installation registry, device registry, channel UI, Room sync, web dashboard.

## Result block

```
D1_PLAN                   = FREE    (D1 free tier on the same Free account; no upgrade)
PAID_RESOURCE_CREATED     = NO

D1_BINDING                = PASS    (worker/pigeonhub-push + pigeonhub-push-test both
                                    bound to D1 "pigeonhub-messages",
                                    database_id 5e44a7f0-a4de-4f8e-8929-1979a59cbadf)
SCHEMA                    = PASS    (2 tables: messages, quota_buckets; indexes:
                                    UNIQUE(channel_id, seq),
                                    UNIQUE(channel_id, idempotency_key) WHERE key IS NOT NULL,
                                    INDEX(channel_id, push_status) for pending-recovery scans)

MESSAGE_INSERT            = PASS    (first durable push returned seq 1 + real FCM id;
                                    row verified via d1 execute)
STORE_BEFORE_FCM          = PASS    (publish order enforced in code: idempotency →
                                    quota → INSERT pending → FCM; D1 failure ⇒ FCM
                                    never called)
FCM_SUCCESS_STATE         = PASS    (push_status: pending → fcm_accepted,
                                    attempt_count 0 → 1, fcm_message_id stored)
FCM_FAILURE_PERSISTENCE   = PASS    (injected + real failures: message remains,
                                    last_error recorded, attempt_count incremented;
                                    permanent 4xx → 'failed', transient 5xx/network
                                    → stays 'pending')

IDEMPOTENCY               = PASS    (same key + same payload → 200 idempotent_replay,
                                    no new row, no quota consumption; same key +
                                    different payload → 409; original immutable;
                                    no key → independent rows)
IDEMPOTENCY_CONCURRENCY   = PASS    (20 concurrent same-key requests → exactly
                                    1 row, all 20 responses converged to the same
                                    message identity, net quota consumption = 1
                                    (losers refund their slot))
IDEMPOTENCY_CONFLICT      = PASS    (concurrent/different payload keeps the original
                                    row; challengers get 409)

QUOTA                     = PASS    (limits 3/day + 2/minute on the test worker:
                                    3rd request → 429 {stored:false}; no message row,
                                    no FCM call; production worker runs 50/day + 5/min)
QUOTA_CONCURRENCY         = PASS    (8 concurrent with a 2/minute limit → exactly
                                    2 accepted, 6× 429; accepted_count = 2 — the
                                    guarded atomic UPSERT cannot over-accept)

D1_FAILURE_NO_FCM         = PASS    (injection "d1_pre" → 500 {stored:false}; zero
                                    message rows, zero FCM sends, quota untouched;
                                    a genuine D1 error takes the same path)
PENDING_RECOVERY_STATE    = PASS    (pending rows are reliably identifiable via
                                    the indexed (channel_id, push_status) scan —
                                    demonstrated with live pending rows; this is the
                                    exact query a future Cron/Queue retry would use)

TEST_COUNT                = 6 strong-pass tests + failure-injection suite A–D +
                                    idempotency replay/conflict + quota sequential +
                                    20-way and 8-way concurrency runs (~160 requests)
FAILED_TESTS              = 0       (one bug was found and fixed during Test 2 — see
                                    KNOWN_ISSUES #1)
SECRET_CHECK              = PASS    (no private key / OAuth token / bearer secret /
                                    FCM token in D1, in the repo, or in logs;
                                    git tracked-file scan clean)
```

## Publish flow (as implemented)

1. Bearer auth (`PUSH_BEARER_SECRET`, dev gate).
2. Payload validation — same contract as the Android `PushPipeline`
   (schema_version 1, https-only url, priority normal|high).
3. Idempotency: `Idempotency-Key` header (optional, 1..200 chars).
   - exists + same canonical hash → return stored message (`idempotent_replay: true`)
   - exists + different hash → `409`
4. Quota: two atomic guarded UPSERTs (`... DO UPDATE SET accepted_count =
   accepted_count + 1 WHERE accepted_count < ?limit ... RETURNING`) on
   `<channel>|minute` and `<channel>|daily` buckets. Exhausted → `429`,
   nothing else happens.
5. D1 INSERT as `pending`. `seq` is allocated inside the INSERT statement:
   `COALESCE((SELECT MAX(seq) FROM messages WHERE channel_id = ?), 0) + 1`.
   D1 serializes writes, so allocation is race-free; the UNIQUE(channel_id, seq)
   index is the backstop (with a one-shot retry). No quota-rejection can create
   a gap because rejected requests never reach the INSERT.
6. Stored-OK check (row re-read).
7. FCM HTTP v1 (existing OAuth adapter, unchanged).
8. State update:
   - FCM 2xx → `fcm_accepted` (+ fcm_message_id, attempt_count+1)
   - FCM 4xx → `failed` + last_error (permanent)
   - FCM 5xx / network → stays `pending` + last_error (retryable)
   - `attempt_count` counts FCM attempts.

`stored: true` responses after a failed FCM return `push_status: "pending"` (or
`"failed"`), exactly per the milestone contract.

## Sequence design (for future Inbox sync)

`seq` is a per-channel dense counter allocated in the same INSERT statement
that writes the row, under a UNIQUE(channel_id, seq) index. Verified live:
after all tests the dev-test channel held `n=5, uniq(seq)=5, min=1, max=5` —
no gaps, no duplicates, including through the 8-way concurrency run and the
quota rejections. Cursor-based sync (`WHERE seq > cursor ORDER BY seq`) is
therefore sound. One future caveat, recorded here: an eventual TTL/GC that
DELETES old rows will introduce gaps for long-lived channels; when inbox sync
ships, deletion policy must switch to a watermark or archive table instead.

## Failure injection (dev/test only)

Gated by `FAILURE_INJECTION=on`, which is set ONLY on the separate test worker
(`pigeonhub-push-test`, channel `dev-test`); the production worker ignores the
injection header entirely. Header: `X-PigeonHub-Fail-At`.

| Point | Observed |
| --- | --- |
| A `d1_pre` | 500 {stored:false}; no message row, no FCM, quota untouched |
| B `d1_post` | 500 {stored:true, push_status:pending}; row exists (attempt_count 0, never touched FCM) |
| C `fcm` | 502 {stored:true, push_status:pending}; last_error="injected fcm failure", attempt_count 1 |
| D `status_update` | 500 {stored:true, push_status:pending}; **FCM actually accepted** (device received it) — demonstrates that a naive retry after this crash duplicates the push; exactly-once delivery is explicitly out of scope |

## Concurrency findings

- 20 concurrent same-key requests (after the fix below): 20/20 responses,
  all converged to one message_id, 19 `idempotent_replay`, exactly 1 row,
  daily quota delta exactly +1 (losers refund their consumed slot).
- 8 concurrent quota-limited requests: 2 accepted / 6×429 — the guarded
  UPSERT makes over-acceptance structurally impossible.

## KNOWN_ISSUES

1. **Found & fixed during Test 2:** D1 reports UNIQUE violations by column list
   ("UNIQUE constraint failed: messages.channel_id, messages.idempotency_key")
   rather than by index name; the race classifier originally matched only index
   names, so 2 of 20 racing losers answered 500 and skipped their quota refund.
   After the fix (match both shapes): 20/20 converge, quota delta = 1.
2. A request that loses the insert race consumes a quota slot momentarily and
   refunds it after losing; between the two steps the bucket can briefly look
   fuller than it is. No correctness impact.
3. Exactly-once FCM delivery is NOT solved (out of scope): scenario D keeps a
   `pending` row whose FCM was already accepted; a future Cron/Queue retry must
   use `attempt_count`/`fcm_message_id` to bound duplicates.
4. Minute/daily buckets are UTC-based; quota resets align to UTC boundaries.
5. The dev bearer secret is a single shared value until write tokens (MVP-001C+).

## SECRET_CHECK

- D1 contains: message rows + quota counters only. No keys, tokens, bearer
  secrets, or service-account material (verified against the schema).
- Worker Secrets unchanged from MVP-001A; the same values were also set on the
  separate test worker via `wrangler secret put` (stdin, never echoed).
- Git tracked-file scan (PowerShell `git ls-files | Select-String …`): only the
  intentional empty `server/.env.example` matches; `.dev.vars`, `worker/.wrangler/`
  gitignored; no real bearer/token/key values in tracked files (substring
  counts = 0).

## COMMITS

```
<this commit> worker: D1 durable message core (MVP-001B) — store-before-FCM contract PASS
```

Pushed to `https://github.com/hyukvoid/PigeonHub.git` branch `main` after
secret scan PASS.

## FINAL_STATUS

**MVP_001B = PASS**

1. D1 stores every accepted message before FCM is called. 2. D1 failure ⇒ zero
FCM calls. 3. FCM failure ⇒ message stays in D1. 4. Concurrent idempotency
converges to exactly one message. 5. Quota never over-accepts under
concurrency. 6. Real Worker → D1 → FCM → Android flow works end to end
(device logcat verified). 7. No secrets exposed. 8. No paid resources.

Next milestone (NOT started): `MVP-001C — Installation + Private Channel
Bootstrap`.
