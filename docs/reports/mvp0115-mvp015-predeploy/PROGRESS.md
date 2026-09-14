# PROGRESS — MVP-011.5 → MVP-015 Pre-Deployment Campaign

Updated: 2026-09-15 (overnight, rolling)

| MVP | Status | Notes |
|-----|--------|-------|
| MVP-011.5 Inbox Correctness | **VERIFIED** | worker protocol 8/8 + full device E2E; commits `42dfba6`, `5833274` |
| MVP-012 Real ComfyUI | IN_PROGRESS | ComfyUI installed (tools/comfyui, venv, CPU torch reuse); adapter next |
| MVP-013 QR Pairing | NOT_STARTED | |
| MVP-014 Real Agent E2E | NOT_STARTED | Claude Code 2.1.88 + Codex CLI present on machine |
| MVP-015 Connection Health | NOT_STARTED | |

## MVP-011.5 gate log (all PASS)

| Gate | Evidence |
|------|----------|
| RELATIVE_TIME_LIVE | card ticked `just now` → `1 minutes ago` untouched on emulator; RelativeTimeTest 8/8 (T+0…multi-day buckets) |
| FOREGROUND_REFRESH | 60s `repeatOnLifecycle(RESUMED)` tick + instant recompute on fg return |
| INDIVIDUAL_DELETE | swipe-to-dismiss on Message + Job Card → snackbar `Message deleted` |
| DELETE_ALL | ⋮ menu → destructive confirm dialog (KO/EN) → inbox empty |
| NO_RESURRECTION | device Refresh after delete stays empty; server delete_test 8/8 (DELETE_TOMBSTONE, NO_RESURRECTION, DELETE_IDEMPOTENT) |
| MULTI_DEVICE_CONVERGENCE | channel-scoped `deleted_ids` in every sync page; any client holding rows converges (protocol-verified). Architecture note: per-device channels — see FINDINGS |
| KO_EN | KO: 방금 전/1분 전/메시지를 삭제했어요/모든 메시지를 삭제할까요?/모두 삭제 + EN equivalents |
| LIGHT_DARK | dark Inbox + card screenshot |
| FONT_200 | font 2.0 Inbox + card screenshot (no clipping) |
| ARTEMIS | full manual UI walk performed during E2E (onboarding → inbox → connections → settings) |
| NO_CRASH | no crash across onboarding, deletes, locale/theme switches, process death |

Bugs found & fixed during verification (commit `5833274`):
1. connector `cmd_run` published START message `args.cmd` ("r u n" — chars of the
   subcommand name joined). Since MVP-008. Fixed to `args.command`.
2. JobCard elapsed rendered the DateUtils FUTURE form ("In 40 sec.") when the device
   clock lagged the server. Replaced with clamped-at-zero duration buckets.
3. Snackbar said "All messages deleted" when deleting one Job Card (2+ event rows).
   Now labels by intent.
4. Worker delete endpoint: D1 cannot bind nested arrays as VALUES rows nor parse
   `INSERT…SELECT…ON CONFLICT` without WHERE — 500 on delete. Fixed with
   `INSERT OR IGNORE … SELECT json_each` (commit `42dfba6`).

Worker deployed: version `957d83ca` (schema_011 applied to D1 — additive tombstones table).

---

## MVP-012 Real ComfyUI (2026-09-15) — VERIFIED (real-app E2E on-device)

**Environment**: ComfyUI 0.35.0 installed at `tools/comfyui` (shallow clone, NOT tracked
by git) with venv `tools/comfyui-venv` (`--system-site-packages` reuses the machine's
torch 2.8.0+cpu — no large downloads). Server: `tools/comfyui-venv/Scripts/python.exe
comfyui/main.py --cpu --listen 127.0.0.1 --port 8188`. Install did NOT require owner
action (no multi-GB model download; workflows are model-free).

**Integration** (`connectors/comfyui_connector.py`, aiohttp — ships with ComfyUI's own
deps; no new requirements for real users):
- `submit workflow.json --name X` — WS opens BEFORE POST /prompt (a workflow that fails
  in ms otherwise loses its terminal frame), then maps: queued→RUNNING(queue #),
  execution_start→RUNNING, progress→PROGRESS(step k/N), executing→PROGRESS(node k/N),
  execution_success→DONE(output count · elapsed), execution_error→FAILED(node: msg),
  interrupted→FAILED. /history poll is the safety net for every race.
- `watch` — polls /queue + /history (ComfyUI targets WS frames at the submitting
  client_id only, so an observer socket never sees other clients' prompts). Reports
  every prompt queued from ANY client (web UI included); only entries started after
  watcher start are reported (no replay on restart).

**E2E (real workflow → Android Job Card, no ComfyUI-specific server logic):**
| Gate | Result |
|------|--------|
| workflow start → RUNNING | `workflow queued (queued #3)` card, then `execution started` |
| execution progress → PROGRESS | node k/N cards during multi-node workflow |
| workflow complete → DONE | `complete · 1 output(s)`; blur workflow produced a real PNG |
| execution failure → FAILED | `SaveImage: [Errno 22] Invalid argument…` on the card |
| normalization | queue position, node count, sampler steps, output count, elapsed, job name |
| pipeline | all events via connector → worker → coalescing → attention → FCM (429 backoff observed working) |
| Android card | source renders `ComfyUI ·` (displaySource fix); FAILED attention reason readable |

Evidence: `evidence/mvp012_comfyui_cards.png`, `evidence/mvp012_comfyui_final.png`.

---

## MVP-013 QR Pairing (2026-09-15) — VERIFIED

**Design (platform-appropriate direction)**: phone displays the QR (pairing happens on
the PC — the PC is the machine that redeems). QR carries ONLY the one-time PHC code —
never long-term credentials; a leaked screenshot expires within the 10-minute TTL and
is single-use anyway.

- Android: `QrCode.kt` renders the code via zxing core (pure-Java, no transitive
  deps — the only new app dependency); shown in the existing pairing dialog.
- PC: `connector pair --qr-image <screenshot/photo>` decodes with zxing-cpp
  (pip, optional; typed code + `--code` remain first-class fallbacks). Regex
  extracts PHC-… so QR payload formatting can evolve.

**Gates:**
| Gate | Evidence |
|------|----------|
| QR_RENDER | dialog screenshot `evidence/mvp013_qr_dialog.png` |
| PAIR_SUCCESS (from QR!) | `connector pair --qr-image` decoded the screenshot → "Paired with channel ch_80983…" |
| ONE_TIME / REPLAY_REJECT | same QR re-paired → HTTP 403 "pairing code already used"; protocol test PASS |
| EXPIRY | protocol test (own row expired via D1, test data only) → 403 "pairing code expired" |
| REVOKE | phone "Revoke code" + protocol test → 403 "pairing code revoked" |
| REPAIR | fresh code after replay/revoke/expiry pairs cleanly + token publishes; protocol test PASS |
| CONNECTOR_TOKEN_PUBLISH | minted pct_ token published real jobs (QR pair check → phone card) |

Protocol suite: `worker/scripts/pairing_test.mjs` — 6/6 PASS against the deployed worker.

---

## MVP-014 Real Agent E2E (2026-09-15) — VERIFIED (Codex) / Claude DEFERRED

See commit `4acb11d`. `connectors/agent_bridge_codex.py` wraps REAL `codex exec --json`
sessions (public JSONL event stream only) into the job contract: thread.started→RUNNING
(job id = real thread id), item/turn→PROGRESS, exit 0→DONE, error/exit≠0→FAILED.

- REAL_AGENT_START / REAL_AGENT_JOB_EVENT / REAL_AGENT_TERMINAL / ANDROID_CARD /
  ATTENTION_POLICY (DONE/FAILED on the HIGH channel, RUNNING/PROGRESS inbox-only) — all
  observed on-device with real multi-step Codex sessions (tool use, 3-5 steps).
- Claude Code real session: **DEFERRED_OWNER_ACTION** — API 402 insufficient_quota.
  Owner action: add credits, then wire hooks per connectors/agent_hook.py.

## MVP-015 Connection Health (2026-09-15) — VERIFIED

Server (worker `06046c1d`, schema_012 additive `connector_health`):
- Worker-observed truth only: accepted publishes (incl. coalesced PROGRESS), GitHub
  webhook fan-out, device syncs record last_seen/last_event/last_success/last_failure
  per (channel, source).
- GET /v1/installations/me/health returns rows + derived state. States deliberately
  coarse: CONNECTED ≤24h, DEGRADED ≤72h ("확인 필요"), UNKNOWN (never seen — rare-event
  sources never read as broken), DISCONNECTED reserved.

Android:
- HealthLine (dot + "Last event: X ago") on GitHub / My Push / AI Agents / ComfyUI
  cards; GitHub correctly shows NO line when never seen (no false "broken").
- Merged worst-state per card; degraded lines surface the STALE source's age:
  "Check connection · Last event: 3 days ago" (KO: "확인 필요 · 마지막 활동: 3일 전").
- Self-heal verified: stale cli row → DEGRADED; new connector publish → CONNECTED.
- Recovery actions per card already exist (Connect / Manage on GitHub / Send test
  notification / Revoke code); no remote-control surface added.
- Internal terms (webhook/token/FCM/HTTP) never appear in the status copy.

Protocol: delete_test.mjs extended → 9/9 PASS (HEALTH_ENDPOINT: 401 unauth, states,
last_event_at). DEGRADED/heal E2E screenshots in evidence/. KO/EN verified on device.
Also: QUOTA_DAILY_LIMIT raised 50→300 in wrangler.jsonc — nightly E2E hit the daily
cap; owner may re-tighten before production.
