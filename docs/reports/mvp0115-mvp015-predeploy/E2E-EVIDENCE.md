# E2E-EVIDENCE — MVP-011.5 → MVP-015

Device: emulator-5554, AVD Medium_Phone_API_36.1 (the PigeonHub-E2E-API36 AVD
became unwedgeable this night — boots to "offline"; see FINDINGS).
App: debug builds for feature E2E; RELEASE build for the fresh-install gate.
Worker: Cloudflare `pigeonhub-push` — versions 957d83ca (MVP-011.5),
111909b5 (health), 06046c1d (quota bump), 5e11002f (beta cap 40).

## Protocol suites (against the deployed worker)

| Suite | Result |
|-------|--------|
| `worker/scripts/delete_test.mjs` (MVP-011.5 + 015) | **9/9 PASS** — SETUP, DELETE_AUTH(401s), DELETE_VALIDATION(400), DELETE_TOMBSTONE, NO_RESURRECTION, DELETE_IDEMPOTENT, DELETE_ALL, SYNC_REPORTS_TOMBSTONES, HEALTH_ENDPOINT |
| `worker/scripts/pairing_test.mjs` (MVP-013) | **6/6 PASS** — PAIR_SUCCESS, CONNECTOR_TOKEN_PUBLISH, ONE_TIME_REPLAY_REJECT(403), REVOKE(403), EXPIRY(403, own-row D1 expiry), REPAIR |
| Android unit tests | RelativeTimeTest 8/8 (buckets T+0…multi-day, singular labels, elapsed clamp) · prior suites green |

## Device E2E highlights

**MVP-011.5** (fresh install, real connector publishes)
- RELATIVE_TIME_LIVE: RUNNING card showed `just now` → `1 minutes ago` with zero
  interaction across the 60s tick; `21 minutes ago` later the same session.
- INDIVIDUAL_DELETE: swipe on Message and Job cards → snackbar (`Message deleted`
  / `메시지를 삭제했어요`); failed server delete snaps the row back (code path).
- DELETE_ALL: ⋮ → destructive dialog (EN + KO verified) → empty; D1 after:
  0 rows, 4 tombstones for the channel.
- NO_RESURRECTION: device Refresh after deletes stays empty; server re-pull
  protocol test proves tombstone filter.
- Process death (force-stop + relaunch): cards, cursor, delete state intact.

**MVP-012** (real ComfyUI 0.35.0, CPU, model-free blur workflow)
- submit: RUNNING `workflow queued (queued #3)` → PROGRESS `node 1/3`, `2/3` →
  DONE `complete · 1 output(s)` on the phone.
- Failure: `Failed` card with attention `SaveImage: [Errno 22] Invalid argument…`.
- watch: prompt queued from an external client (curl, client_id=webui-sim) →
  `Done · 1 output(s)` card; fast prompts caught via /history fallback.
- 429 quota burst → connector retried with backoff, terminal landed.
- evidence: mvp012_comfyui_cards.png, mvp012_comfyui_final.png

**MVP-013**
- Phone renders the pairing code as QR (evidence mvp013_qr_dialog.png).
- `connector pair --qr-image <phone screenshot>` decoded `PHC-N7WYQ-PQS92-SE2TC`
  and paired for real; the fresh token published a job that reached the phone.
- Replaying the same QR → HTTP 403 `pairing code already used`.

**MVP-014** (real Codex CLI sessions via public JSONL events)
- Session A (multi-step, tool use): RUNNING → 5 PROGRESS → DONE
  `5 step(s) · 마지막 프레임: frame 8`.
- Session B: real agent failure → FAILED card in real time.
- Session C: `repro-done · 3 step(s)` DONE, delivered live (FCM receipt logged).
- evidence: mvp014_agent_cards.png

**MVP-015**
- Health lines on GitHub / My Push / AI Agents / ComfyUI cards
  (`Last event: just now · Connected`); GitHub correctly line-less (never seen).
- Stale simulation (own-channel row aged 3 days) → amber dot +
  `Check connection · Last event: 3 days ago`; new connector publish →
  self-healed to Connected. evidence: mvp015_health_degraded*.png,
  mvp015_health_connected.png, mvp015_health_ko.png

**Pre-deployment hardening**
- Release-APK fresh install: purpose → invite → permission → inbox (blocked once
  by `beta capacity reached` at 20 — the gate works; cap raised to 40) → real
  test push delivered (`Test notification from your device.` in shade).
  evidence: predeploy_release_fresh_mypush.png
- Offline recovery: airplane-mode on → publish → off → refresh → card present.
- Notification spam: chatty demo (22 events) → PROGRESS inbox-only, 11 D1 rows
  (coalescing), no notification flood.
- Font 200%: Inbox + Connections render (predeploy_font200_*.png); known P4:
  bottom-nav EN labels wrap at ≥150%.
- Process death on release build: state intact.
