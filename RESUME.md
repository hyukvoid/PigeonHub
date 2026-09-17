# RESUME — MVP-016 CLI Core (2026-09-17)

Branch: `autonomous/mvp016-cli-core-codex-20260917`.
RC-001A Artemis UX was checkpointed at `eb2e65c` before this branch began.

## Current state

- `pigeonhub login/logout/status/notify/progress/needs-action/run` is implemented
  as the dependency-light `pigeonhub/` package with a `pyproject.toml` console
  entry point.
- PC-first login creates a short-lived request, displays a terminal QR, waits
  for Android approval, and atomically stores connector credentials.
- Android Connections has a generic PigeonHub CLI PC card, Camera2 + ZXing
  scanner, and explicit approval dialog. The old phone-issued `pair` path is
  retained for compatibility.
- `run` publishes RUNNING before process start, refuses to start on tracking
  failure, injects `PIGEONHUB_JOB_ID`, preserves child stdout/stderr/exit code,
  and reports terminal publish failures without masking that exit code.
- Worker PC-first pairing routes are typechecked; apply `worker/schema_013.sql`
  before using the flow against a deployed Worker.

## Verified in this session

- Python CLI tests: 5/5, including PC-first login polling and process lifecycle.
- Worker `npm run typecheck`: pass.
- Android debug assemble + unit tests: pass.
- Android release assemble: pass.
- `git diff --check`: pass; only CRLF normalization warnings remain.

## Next operational step

Apply/deploy `worker/schema_013.sql` to the intended Worker/D1 environment, then
run a physical Android camera scan against `pigeonhub login`. MVP-018 remains
responsible for a signed Windows single executable, installer, and PATH setup.

---

# RESUME — next session picks up here (2026-09-15, end of MVP-011.5→015 campaign)

Branch: `autonomous/mvp0115-mvp015-predeploy-overnight-20260914` (from `b88b335`).
`main` untouched. Working tree: clean after the final docs commit.
Latest commit: see `git log --oneline -8`.

## State (source of truth)

- **MVP-011.5, 012, 013, 014 (Codex), 015 — all VERIFIED.** Scoreboard + evidence:
  `docs/reports/mvp0115-mvp015-predeploy/` (FINAL-REPORT, PROGRESS, FINDINGS,
  ARCHITECTURE, E2E-EVIDENCE, PREDEPLOY-CHECKLIST).
- **Deployed worker** `pigeonhub-push`: version `5e11002f` (chain: 957d83ca →
  111909b5 → 06046c1d → 5e11002f). D1 migrations applied this campaign:
  `schema_011.sql` (deleted_messages tombstones), `schema_012.sql`
  (connector_health) — both additive. Config changes to revisit:
  `QUOTA_DAILY_LIMIT` 50→300, `BETA_MAX_INSTALLATIONS` 20→40.
- **Android**: release APK (`app/build/outputs/apk/release/app-release.apk`)
  installed on emulator-5554 (AVD `Medium_Phone_API_36.1`), onboarded fresh as
  installation channel `ch_b00fc82923b64b3399c4` (invite TEST_INVITE_CODE_18).
  Room version 5 (unchanged).
- **Pairing**: `~/.pigeonhub/credentials.json` holds a live connector token for
  the phone's channel. `~/.claude`/`~/.codex` configs untouched (Codex notify
  tested per-invocation via `-c`, Claude untouched).
- **ComfyUI**: real install at `tools/comfyui` (gitignored) + venv
  `tools/comfyui-venv` (system-site-packages, CPU torch reuse). Boot:
  `cd tools && ./comfyui-venv/Scripts/python.exe comfyui/main.py --cpu --listen
  127.0.0.1 --port 8188`. Connectors: `connectors/comfyui_connector.py`
  (submit/watch), `connectors/agent_bridge_codex.py` (real Codex sessions),
  `connectors/pigeonhub_connector.py pair --qr-image|--code`.

## Completed this campaign

- MVP-011.5: tombstone deletion (worker+client), live relative time, delete-all,
  plurals, elapsed clock-skew clamp, connector `args.cmd` fix. delete_test 9/9.
- MVP-012: real ComfyUI 0.35.0 E2E (submit + watch, DONE & FAILED on phone).
- MVP-013: QR pairing (zxing render; pair by decoding a phone screenshot;
  replay/expiry/revoke/repair proven).
- MVP-014: real Codex sessions as Job Cards (DONE + FAILED, HIGH pushes).
- MVP-015: connection health (worker-observed, /me/health, HealthLine UI,
  DEGRADED + self-heal) + predeploy hardening (fresh install release gate,
  offline recovery, process death, spam, font/theme walks, secret scan).

## Exact blockers / owner actions (also in FINAL-REPORT §4)

1. Claude Code real-session E2E — API 402 quota; rerun with connectors/agent_hook.py.
2. BETA_MAX_INSTALLATIONS + stale-install cleanup story (cap now 40 for tests).
3. QUOTA_DAILY_LIMIT back to production value (now 300).
4. Physical-device pass: real Galaxy + camera QR scan (emulator can't).
5. Play signing/listing (out of scope by campaign rules).

## Exact next action for the next session

1. `git log --oneline -5` + read FINAL-REPORT.md (2 min) — everything else is here.
2. If owner granted Claude quota: wire hooks per connectors/agent_hook.py in a
   scratch project dir and run a real `claude -p` session; verify the Agent card
   lifecycle (same gates as MVP-014 Codex).
3. If continuing product work: server-side job timeout (mark stale RUNNING
   cards, FINDINGS #11) and WorkManager periodic sync (FINDINGS #5) are the two
   highest-value reliability extensions; both are small and don't touch the
   verified FCM pipeline.
4. Provision fresh test invites via `INVITE_TEST_HASHES` (pattern in
   `worker/scripts/*.mjs` + RESUME history); codes are single-use and burned
   through CODE_18.

## Watch-outs

- AVD `PigeonHub-E2E-API36` is wedged (boots "offline") — use
  `Medium_Phone_API_36.1` (cold-boots ~45s).
- Release build: bottom nav has 3 items (no Diagnostics in release) — nav
  centers ≈ x 172 / 540 / 907; don't reuse debug-build tap coordinates.
- Release build logs are quiet — verify via UI/dumpsys, not logcat.
- Emulator clock ran ~40-60s behind the server — elapsed/relative code already
  clamps, but keep it in mind when comparing timestamps.
- uiautomator taps: one tap per adb command; re-dump before each tap.
