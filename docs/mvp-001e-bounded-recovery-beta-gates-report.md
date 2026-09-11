# MVP-001E Report — Bounded Delivery Recovery & Beta Gates

Date: 2026-09-11
Scope: bounded pending-FCM retry (Cron), retention cleanup with watermark
advancement, global free-tier kill switch, beta capacity gate, and beta-ready
data hygiene. No multi-device, no accounts, no new channels, no dashboard.

## Result block

```
PENDING_RETRY            = PASS   bounded: max 5 attempts, age-based backoff
                                  (attempt_count × 30 min), 100 retries/day ceiling,
                                  10-message batch per run, enabled installations only
RETRY_DELIVERY           = PASS   a pending message with the REAL device token was retried
                                  by maintenance and DELIVERED to the device
                                  (logcat: [fcm] delivered push mvp1e-p1 (priority=HIGH),
                                  attempt_count 1 → 2, status → fcm_accepted)
SCENARIO_D_SELF_HEAL     = PASS   pending + fcm_message_id already set (MVP-001B scenario D:
                                  FCM accepted, state update crashed) is self-healed to
                                  fcm_accepted WITHOUT a second FCM send (no duplicate
                                  notification; logcat silent for that id)
ATTEMPT_CAP              = PASS   a pending message at attempt_count = 5 is left untouched
RETRY_CEILING            = PASS   cron|retry daily counter (100/day) enforced via the same
                                  guarded UPSERT pattern; reported in the summary

RETENTION_CLEANUP        = PASS   5 expired messages deleted in a bounded batch (200/run);
                                  physical deletion now exists (MVP-001E scope) while
                                  retention semantics stay: gaps are normal artifacts
RETENTION_FLOOR_ADVANCE  = PASS   channels.retention_floor_seq advanced to the highest
                                  deleted seq (103) for the affected channel
HISTORY_TRUNCATION_LIVE  = PASS   after floor=103: GET messages with after_seq < floor
                                  returns history_truncated=true and only surviving rows;
                                  the device's next sync picked up the 4 post-floor
                                  messages (96,97,98,104) — deleted range skipped cleanly

KILL_SWITCH              = PASS   PUBLISH_KILL_SWITCH=on → BOTH publish paths (legacy /push
                                  and private /v1/channels/{id}/messages) return
                                  503 {stored:false} BEFORE any D1 write or FCM call;
                                  removed → publishing restored immediately (secret set/
                                  delete requires no code deploy)
BETA_CAPACITY_GATE       = PASS   fresh bootstrap with enabled installations ≥ cap (20) →
                                  403 "beta capacity reached" (invite not consumed);
                                  after cleanup, gate reopens (201)

BETA_READINESS           = PASS   D1 reduced to beta-ready state: 1 installation
                                  (the emulator device), 1 private channel, 99 messages;
                                  5 fresh single-use invite codes issued (hashes in the
                                  INVITE_HASHES secret, raw codes in local .dev.vars only)

PAID_RESOURCE_CREATED    = NO     (Workers Free + D1 Free; Cron Triggers are free)
FREE_RUNTIME             = PASS   maintenance run cost: a handful of D1 queries
                                  (~250 rows scanned/written worst case) per 15-min tick;
                                  cron failures during development were caught by
                                  wrangler tail and fixed before ship
```

## How the bounded recovery works

```
Cron (*/15 * * * *)  ──or──  POST /v1/maintenance/run  (bearer: dev secret)
        ↓
1a. SELF-HEAL: pending + fcm_message_id IS NOT NULL  → fcm_accepted
    (MVP-001B scenario D: FCM was accepted, the state update crashed;
     no second send is ever made for these rows)
1b. CEILING: cron|retry daily counter guarded UPSERT (100/day)
1c. RETRY BATCH (≤10): pending AND 1 ≤ attempt_count < 5 AND fcm_message_id IS NULL
    AND installation enabled AND updated_at older than attempt_count × 30 min
    → FCM send → fcm_accepted | pending(transient) | failed(permanent 4xx)
2. CLEANUP (≤200): expires_at < now → DELETE → retention_floor_seq = max deleted seq
```

Bugs found & fixed while shipping this milestone (caught by `wrangler tail`):
the maintenance query referenced three columns that don't exist in D1
(`sent_at`, `schema_version`, `updated_at`) — the messages table never
persisted those transport-only fields. `updated_at` was added as a real column
(schema_004) with a backfill because the retry backoff needs it; the other two
were replaced with existing fields/constants.

## MVP-001B failure-injection scenarios — post-MVP-001E status

| Scenario | MVP-001B | Now |
| --- | --- | --- |
| D — FCM ok, update crashed | pending, duplicate-on-retry risk documented | **self-heals to fcm_accepted on the next maintenance run, no duplicate send** |
| C — FCM transient failure | stays pending | retried by Cron within bounds, then exactly as designed |
| permanent FCM 4xx | failed | retried ≤ 5 attempts then `failed` (bounded) |

## KNOWN_ISSUES

1. Cron retry uses the device token captured at registration; if the token
   rotated after the message was stored, the retry may hit UNREGISTERED → the
   row lands in `failed` (bounded, visible via last_error). MVP-001F candidate:
   store token version with messages.
2. The kill switch is config/secret-controlled; setting it via `wrangler.jsonc`
   requires a deploy, via secret it is instant. Operational runbook should
   prefer the secret.
3. Retention floor advances only for channels that had expiring rows in the
   cleaned batch; long-idle channels keep old floors (harmless — clients with
   cursors above the floor are unaffected).
4. `wrangler secret delete` requires interactive confirmation on this CLI;
   deletion was performed through the Workers secrets API.

## SECRET_CHECK

- Tracked-file scan (PowerShell `git ls-files | Select-String …`): clean.
- Real-value substring scans (bearer / device token / invite code): 0 matches.
- A stray `android/.wrangler/cache/wrangler-account.json` (account id + name,
  machine-local cache) was untracked in this milestone and `.gitignore` was
  widened to any `.wrangler/` directory.
- D1 contains no secrets: message rows, quota counters, installation hashes,
  encrypted FCM tokens only.

## PAID_RESOURCE_CREATED

NO. (Workers Free + D1 Free + free Cron Triggers.)

## FAILED_TESTS

0 — one real defect was found and fixed during testing (three nonexistent
columns referenced by the maintenance query; symptom: Worker exception 1101,
diagnosed via `wrangler tail`).

## COMMITS

```
<this commit> worker: bounded delivery recovery + beta gates (MVP-001E)
```

Pushed to `https://github.com/hyukvoid/PigeonHub.git` branch `main` after
secret scan PASS.

## FINAL_STATUS

**MVP_001E = PASS**

Delivery recovery is bounded and live on the free plan (self-healing scenario
D, ≤5 attempts, 100/day ceiling, 15-min Cron), retention cleanup advances the
watermark and drives the history_truncated contract, a global kill switch and
beta capacity gate protect the free tier, and D1 is in beta-ready state
(1 device installation, 5 fresh single-use invites). The 5-user private beta
can start when the owner distributes the invite codes stored in
`worker/.dev.vars` (INVITE_CODE_1..5).
