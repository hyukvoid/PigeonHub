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
