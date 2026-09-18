# E2E-EVIDENCE — MVP-019

## P0 stored-but-502

- Production worker deployed `a308d20c` (test env `f2f121a2`), commit `3017b6e`.
- **Real dead channel** (`ch_80983675f…`, the overnight duplicate-generator):
  - attempt 1 → HTTP 200 `{stored:true, push_status:"failed", delivery:{retryable:false}, error:"…NotRegistered"}`
  - attempt 2, same `Idempotency-Key` → HTTP 200 `{idempotent_replay:true, same message_id}`
  - D1: **3 test events → exactly 3 rows** (previously 1 event → 8 rows).
- Live channel unchanged: job/agent publishes `fcm_accepted` throughout.
- Unit tests: `test_publish_sends_stable_idempotency_key_and_stops_on_stored_failed`,
  `test_publish_retries_flaky_503_with_the_same_key` (16/16 total).

## P0 stale credential UX

- `pigeonhub notify` against the dead channel prints:
  `[pigeonhub] notification saved, delivery failed` + the four-line
  "pairing can no longer reach your phone … Run: pigeonhub login" guidance.
- Job events path identical (`publish_job_detailed`).

## P1 real ZCode session bonus

- `sess_0598fadb` — a genuine ZCode desktop session started after the MVP-017.5
  hook install — produced a full real lifecycle overnight:
  RUNNING (00:13:51Z) → PROGRESS ×9 → DONE (00:23:59Z), all `fcm_accepted`.
  This closes the "simulated payload" caveat from MVP-017.5: ZCode real-session
  E2E is now proven with a human-driven session. The Inbox card
  ("Zcode · Zcode session — 완료 · 업데이트 11회") is visible in
  `evidence/inbox-stale-marker.png`.

## P1 stale RUNNING marker

- Device evidence: `evidence/inbox-stale-marker.png` — the hard-killed
  "CLI · CtrlC-test" card (RUNNING) shows "7시간 53분 경과" and the new italic
  "7시간 54분 동안 업데이트 없음". No fabricated terminal state, no push.
- Unit test: `stale no-updates marker respects threshold and buckets`.

## Icons / UI

- `evidence/agent-cards-light.png`, `agent-cards-grok-zcode.png` — four brand
  tiles, per-agent honest health lines (Grok: none).
- `evidence/agent-cards-dark.png`, `agent-cards-dark2.png` — dark mode.
- `evidence/agent-cards-dark-ko.png` — KO + dark.
- `evidence/agent-cards-ko-font200.png` — KO + light + font 200%.
- `evidence/codex-detail.png` — detail screen with brand tile.

## Regression gate

- Python: 16/16 (`tests/`).
- Worker `tsc --noEmit`: pass.
- Android `:app:testDebugUnitTest` + `:app:assembleDebug` +
  `:app:assembleRelease`: BUILD SUCCESSFUL.
- `git diff --check`: clean.
- Secret scan (tracked files, credential-shaped patterns): clean.
- Worker job coalescing module: not modified this campaign (its integration
  script targets a live worker and remains the MVP-011 evidence).
