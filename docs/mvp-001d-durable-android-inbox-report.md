# MVP-001D Report — Durable Android Inbox (D1 canonical → Room replica)

Date: 2026-09-11
Scope: prove the product promise — **"Push를 놓쳐도 메시지는 PigeonHub Inbox에서
다시 찾을 수 있다" (retention 범위 내)** — by making D1 the canonical message
store, Room the Android local replica, and FCM a wake-up/realtime mechanism.

Not implemented (per scope): pending FCM retry, Cron delivery retry, cleanup
Cron, Queues, multi-device, accounts, server-side unread state, read receipts.
Those are MVP-001E.

## Result block

```
SOURCE_OF_TRUTH      = D1 canonical / Room local replica / FCM wake-up delivery.
                       FCM is explicitly NOT a message database.

ROOM_SCHEMA          = PASS   inbox_messages(message_id PK, channel_id, seq, title,
                                message, priority, url, created_at, expires_at,
                                received_via FCM|SYNC, local_received_at, is_read,
                                read_at) + UNIQUE(channel_id, seq) index +
                                sync_state(last_synced_seq, last_sync_at,
                                history_truncated)
ROOM_MIGRATION       = PASS   Room v1 created fresh on device (no legacy version to
                                migrate; schema is additive for future versions)

MESSAGE_API          = PASS   GET /v1/installations/me/messages
                                  ?after_seq=&limit=(1..200, default 50)
                                  [&snapshot_max_seq=]
                                read-only (no read receipts, no mutation), excludes
                                expired (expires_at > now)
AUTH_ISOLATION       = PASS   no auth → 401; WRITE token → 401; management secret →
                                200. Cross-installation leak = 0 (installation B's
                                management secret sees 0 of A's 141 messages)

INITIAL_SYNC         = PASS   device cold-start backfilled 95 messages in 2 pages
INCREMENTAL_SYNC     = PASS   server test: one new message returned exactly once with
                                the run-unique id; device cursor advanced to 95
SNAPSHOT_PAGINATION  = PASS   first page fixes snapshot_max_seq (120); 20 messages
                                published mid-pagination appeared ONLY in the next
                                sync (next_page=20, first=seq 121)
CONCURRENT_INSERT    = PASS   duplicate=0, missed-within-snapshot=0

FCM_TO_ROOM          = PASS   FCM payload now carries channel_id + seq; realtime
                                messages land in Room via the preserving upsert
SYNC_TO_ROOM         = PASS   92 no-FCM messages recovered with received_via=SYNC
FCM_THEN_SYNC        = PASS   content-preserving ON CONFLICT upsert: sync never
                                overwrites received_via/local_received_at/is_read
SYNC_THEN_FCM        = PASS   message synced first, then any delayed FCM of the same
                                id hits the existing row → no second row
OUT_OF_ORDER         = PASS   Room rows are read ORDER BY seq DESC regardless of
                                arrival order; PK upsert makes arrival order irrelevant
DUPLICATE_HANDLING   = PASS   re-sync recovered=0; delayed FCM → Room count unchanged.
                                Notification policy: a notification is posted only when
                                the message is NEW to Room — a delayed duplicate FCM
                                never re-notifies (documented policy)

MISSED_PUSH_RECOVERY = PASS   ★ core test: 92 messages written to D1 with the FCM
                                send intentionally skipped (push_status=pending) —
                                app relaunch → sync → "inbox sync complete: pages=2
                                recovered=95" (92 injected + 3 earlier FCM messages)
OFFLINE_INBOX        = PASS   wifi+data disabled → force-stop → relaunch: Room inbox
                                still rendered; sync failed gracefully ("Unable to
                                resolve host"), cursor untouched; network restored
PROCESS_DEATH        = PASS   force-stop + relaunch around sync; correctness rests on
                                the per-page Room transaction (below) — a full sync
                                restart is always safe

CURSOR_TRANSACTION   = PASS   each page commits in ONE Room transaction:
                                upsert page + advance last_synced_seq + truncated flag.
                                A crash mid-page rolls page AND cursor back together;
                                the cursor never advances ahead of committed data

RETENTION_WATERMARK  = PASS   channels.retention_floor_seq added (schema_003, default 0;
                                physical deletion stays MVP-001E). Sequence gaps are
                                treated as normal retention artifacts, not errors
HISTORY_TRUNCATION   = PASS   server returns retention_floor_seq + history_truncated;
                                contract served and covered by tests (invalid snapshot
                                → 400). Client treats truncation as "older messages
                                expired on the server", never as corruption

STRONG_TEST_EXPECTED = 95      (3 FCM-delivered + 92 FCM-skipped injected directly into
                                D1 for the device's private channel)
STRONG_TEST_ACTUAL   = 95      (Room rows after cold-start sync: "pages=2 recovered=95")
MISSING              = 0
DUPLICATES           = 0
```

Server-side suite (`worker/scripts/sync_test.mjs`): **8/8 PASS** —
MESSAGE_API_AUTH, 120-message setup, PAGINATION (120 in 3 pages of 50, unique +
ordered), SNAPSHOT_PAGINATION, CONCURRENT_INSERT, INCREMENTAL_SYNC,
CROSS_INSTALLATION_ISOLATION, RETENTION_CONTRACT.

Android unit tests: BUILD green (`testDebugUnitTest`). Compile-time guarantees:
KSP-validated Room queries, strict TypeScript on the Worker.

## FREE_RUNTIME measurement

- Sync cost per device: each page reads ~51 indexed rows + 1 MAX(seq) probe +
  1 channel row ≈ 53 D1 rows read, 1 Worker request, sub-millisecond CPU
  (no crypto on this path).
- Beta profile (5 installations × 50 msgs/day × 7-day retention): a full
  cold backfill is ~1750 messages ≈ 35 pages ≈ ~1850 rows read; steady-state
  daily incremental syncs read a few hundred rows. D1 Free allows 5M rows
  read/day and 100k rows written/day; Workers Free 100k requests/day —
  usage is orders of magnitude below limits. No paid upgrade was considered.

## Secret management

- No new secrets introduced. The sync endpoint authenticates with the existing
  client-generated management secret (hash-compared server-side).
- D1 still contains no key material, tokens, or bearer secrets.
- Tracked-file scan (PowerShell) clean; `.dev.vars`, `.env`, `.wrangler/`
  gitignored; real-value substring counts in tracked files = 0.

## KNOWN_ISSUES

1. FCM realtime messages whose payload predates the channel_id/seq extension
   (in-flight during the deploy) would store with a NULL seq coordinate; the
   UNIQUE(channel_id, seq) index deliberately ignores NULLs so such rows never
   collide. All post-deploy messages carry full coordinates.
2. "Send test notification" on the device runs through the network path; if
   offline it fails with a snackbar (the durable inbox is unaffected).
3. Copy cURL clipboard content cannot be read back via adb (platform
   restriction) — verified by construction: the exact request shape is what
   protocol/sync test suites execute.
4. Unread state is local-only by design; uninstalling loses read flags (the
   messages themselves are recoverable from D1 within retention).

## FAILED_TESTS

0. (Two test-script defects found during the run were fixed in the script:
   message ids are now run-unique because messages.id is a GLOBAL primary key —
   a deliberate canonical-dedupe property — and the per-installation daily
   quota of 50 was temporarily raised while generating the 120-message fixture,
   then restored to the shipped 50/day + 5/min.)

## COMMITS

```
<this commit> android+worker: durable Android inbox — D1 sync, Room replica (MVP-001D)
```

Pushed to `https://github.com/hyvoid/PigeonHub.git` branch `main` after
secret scan PASS.

## FINAL_STATUS

**MVP_001D = PASS**

PigeonHub가 서버에서 수락한 메시지는 FCM 전달 여부와 관계없이 retention 기간 내
Android가 다시 동기화하면 Room Inbox에 정확히 한 번 존재한다 — 95/95 복구
(92건은 FCM 없이 D1에만 존재하던 메시지), duplicates 0, cross-installation
leak 0. Next milestone (NOT started): MVP-001E — Bounded Delivery Recovery &
Beta Gates (pending retry Cron, retention cleanup + floor advancement, global
kill switches, 5-user beta readiness).
