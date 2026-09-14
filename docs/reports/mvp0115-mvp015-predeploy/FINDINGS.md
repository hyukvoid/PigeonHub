# FINDINGS — MVP-011.5 → MVP-015

Severity-ranked. Bugs found by the campaign's own verification are marked FIXED.

## P1 — found & fixed this campaign
1. **Connector START message corrupted since MVP-008** (FIXED, `5833274`):
   `cmd_run` published `" ".join(args.cmd)` — `args.cmd` is argparse's
   subcommand name (`"run"`), so the Job Card's detail showed `r u n`
   (per-character join) and D1 stored it. Was `args.command`.
2. **Durable-delete endpoint 500** (FIXED, `42dfba6`): D1 rejects nested-array
   binds as VALUES rows AND `INSERT…SELECT…ON CONFLICT` without a WHERE —
   replaced with `INSERT OR IGNORE … SELECT … FROM json_each` (verified directly
   against D1).
3. **Elapsed time rendered the FUTURE form** (FIXED, `5833274`): the running
   card showed "Elapsed In 40 sec." whenever the device clock lagged the
   connector's clock. Replaced with clamped-at-zero duration buckets.
4. **Snackbar mislabeled Job deletes** (FIXED, `5833274`): deleting one Job
   Card (2+ event rows) said "All messages deleted". Now labels by intent.

## P2 — observations to carry forward
5. **One incident of delayed event delivery (~10 min)**: the first Codex run's
   FCM data messages were `fcm_accepted` at the worker yet only materialized on
   the device ~10 min later; the immediately following run arrived in real time.
   Suspected FCM-side deferral/throttling under burst; the sync path converges
   independently (cards ended up identical). Mitigation candidates if it
   recurs: periodic WorkManager sync, or per-event receipt reconciliation.
6. **Test-invite pool shares the beta install cap**: tonight's automated tests
   bootstrap real installations; the pool hit `BETA_MAX_INSTALLATIONS=20` and a
   release fresh-install gate was correctly REJECTED ("beta capacity reached").
   Cap raised to 40 (`5e11002f`). Before launch the owner needs a real cap plus
   a story for pruning stale test installations (without breaking
   (channel, seq) semantics — tombstone-style, not renumbering).
7. **QUOTA_DAILY_LIMIT 50 → 300** (`06046c1d`): nightly E2E legitimately hit the
   free-tier daily cap (the 429 + connector backoff path was exercised — good).
   Owner may re-tighten.

## P3 — known limitations (documented, accepted for beta)
8. **Diagnostics screen is EN-only** (hardcoded strings, by design a developer
   surface). Listed by the i18n audit; not localized.
9. **Empty-state CTA label mismatch**: "Send your first test notification"
   actually navigates to the My Push card (where the real send button lives).
   Rename candidate ("Set up your first push").
10. **Multi-device convergence is channel-scoped by design**: each installation
    owns a channel; `deleted_ids` converges any client holding that channel's
    rows. A shared-inbox (multiple devices, one inbox) model would need a
    channel-membership layer — out of scope, mechanism is ready.
11. **Stuck RUNNING cards**: if a connector dies mid-job, the card stays RUNNING
    until the user deletes it. A server-side job timeout (e.g., mark STALE after
    N hours without events) is the natural MVP-016 health extension.
12. **Claude Code real-session E2E** — DEFERRED_OWNER_ACTION (402 quota).

## Environment notes
13. The `PigeonHub-E2E-API36` AVD became unrecoverable this night (boots to adb
    "offline" even cold); `Medium_Phone_API_36.1` used instead (boots in ~45s).
14. ComfyUI lives at `tools/comfyui` (gitignored) with `tools/comfyui-venv`
    (system-site-packages reuses the machine's torch CPU build — zero large
    downloads). Boot: `tools/comfyui-venv/Scripts/python.exe comfyui/main.py
    --cpu --listen 127.0.0.1 --port 8188`.
15. ComfyUI ≥0.3.x targets WS lifecycle frames at the SUBMITTING client_id only
    — observer sockets see nothing. `comfyui_connector.py watch` therefore polls
    /queue + /history (client-agnostic, time-filtered to avoid replays).
16. zxingcpp python API: `read_bars_from_file` is gone; use
    `zxingcpp.read_barcodes(PIL.Image)`.
