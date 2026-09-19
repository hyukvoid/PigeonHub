# RESUME — BETA-003 Onboarding UX + Kaggle draw variants (2026-09-19)

Branch: `autonomous/beta003-onboarding-ux-20260919` (base: BETA-002 HEAD
`9076e88`). Lineage: MVP-019.1 → BETA-001A (`a0c0cf3`) → BETA-001B
(`9b053d9`) → BETA-002 (`9076e88`) → BETA-003 (HEAD commit).

## State (source of truth)

### BETA-003 — One-click onboarding (`docs/reports/beta003-onboarding-ux/`)

- **Ephemeral Setup Center**: `pigeonhub onboard` → `127.0.0.1` random port +
  browser page (Welcome → Connect phone → Connect tools → Test → Done),
  KO/EN, dies with the session. No daemon/tray/service.
- **Security**: session-token gate (invalid/expired = 404), POST-only
  mutations with same-origin + `confirm`, fixed action allowlist (no command
  execution path), poll secret/token/config never serialized or logged.
  `tests/test_pigeonhub_onboard.py` — 16 tests.
- **Engine reuse**: QR pairing = MVP-016 protocol (in-page QR PNG, poller
  state machine); agent connect/remove = the existing `pigeonhub setup`
  engine (preview/backup/atomic/verify); test notification = `publish_message`.
- **Installer**: Finish page "Set up PigeonHub now" (fresh installs only —
  uninstall-key check; upgrades never auto-launch), Start Menu "PigeonHub
  Setup" shortcut, optional desktop icon. Console window minimized, closing
  it = cancel.
- **Android**: agent details now guide "이 도구는 PC에서 설정해요…" (KO/EN);
  no remote execution, no config mutation.
- **RC**: CLI **0.20.0-beta.1**; Android `versionCode 3 / 0.3.0-beta003`.
  Regression: Python 90/90, Android unit + assembleDebug/Release OK,
  Defender 0 threats, secret scan/diff-check clean. Packaged onboard smoke:
  `127.0.0.1:51283 LISTENING`, tokenless `/setup` → 404, process kill = server
  gone.

### Kaggle — final two submissions (approved, both used; NO more)

Same validated S44 pipeline (architecture/features/hyperparameters/blend
untouched), only the PU draw changed; cached artifacts reused; frozen S44
file untouched.

| Submission | ref | Public | Δ vs 0.73649 |
|---|---|---|---|
| S44_PRESSURE_run01 (S33 draw) | 56347081 | **0.74147** | **+0.00498** — new champion |
| S44_PRESSURE_run02 (S32 draw) | 56347895 | 0.73893 | +0.00244 |

- run01 was already submitted by the earlier session before this one started;
  it was recorded, not re-submitted. run02 was built
  (`src/s50_pressure_runs.py`), validated 9/9
  (`src/validate_submission.py`), diffed against S44/run01 (risk deltas,
  300+ behavior-diff rows, ~100k evidence-diff cells, distinct hashes), then
  submitted. Note: the leaderboard also shows an extra run02 entry at
  04:03 UTC with the identical file/message (duplicate quota burn, same
  score) — cause unknown (likely an earlier session's retry); 2 submissions
  remain today. **No further submissions.**
- Draw-stability verdict: S44 + pressure holds on all three PU draws
  (0.74147 / 0.73893 / 0.73649) — the gain is not a draw artifact.
  Frozen S44 artifacts were never modified.

## Owner actions / next steps

1. Real-Galaxy pass (BETA-002 checklist): new APK 0.3.0-beta003 + installer
   → Setup Center or `pigeonhub login` → scan → approve → test job; then
   revoke the old test token (exact command in MVP-017.5 SECURITY.md).
2. Spot-check the Setup Center at 125/150% Windows scaling and, with a real
   phone, time the full install→pair→connect→test loop (~3 min target).
3. 5–7 day dogfood with `docs/dogfood/DOGFOOD-TEMPLATE.md` (20+ real jobs).
4. Then 3–5 person private beta using `dist/beta-bundle/` (refresh the bundle
   with the 0.20.0-beta.1 artifacts).

## Environment notes

- Inno Setup: `WizardIsUpgrade` unavailable in this IS6 build (use the
  uninstall-key check); `{#SetupSetting("AppId")}` inside string literals
  does not preprocess — use an explicit `#define` (done in installer.iss).
- `PigeonHub-E2E-API36` AVD still hangs on snapshots; use
  `Medium_Phone_API_36.1` with `-no-snapshot`.
- `git stash@{0}` (old Galaxy ui.xml dump) still stashed.
