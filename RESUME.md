# RESUME — BETA-001A QR Pairing UX + BETA-001B Local Recipes (2026-09-19)

Branch: `autonomous/beta001a-qr-pairing-locale-20260918` (from `4a47d89`).
Commits: `a0c0cf3` (beta001a) + the beta001b commit at HEAD.
Previous campaign (MVP-019/019.1) state lives on in its reports
(`docs/reports/mvp019-reliability-icons/`, `docs/reports/mvp0191-brand-icons/`).

## State (source of truth)

### BETA-001A — QR pairing UX + locale parity (report:
### `docs/reports/beta001a-qr-pairing-locale/FINAL-REPORT.md`)

- **CLI login**: `pigeonhub login` renders a real raster QR PNG (≥512px, pure
  black/white, 4-module quiet zone) and auto-opens it in the default viewer;
  temp PNG deleted on success/error/Ctrl+C; terminal ASCII QR is fallback
  only; `PIGEONHUB_NO_OPEN=1` is the headless QA hook. `qrcode[pil]` +
  static `qrcode.image.pil` import so PyInstaller bundles Pillow.
- **Android scanner**: `ScanFrameView` centered bracket frame + dim overlay
  (240–280dp responsive square), localized title/hint, 40 ms haptic + camera
  freeze on scan, localized invalid/expired snackbars; worker HTTP 410 →
  "QR 코드가 만료되었습니다…" message. Precedence-bug fix (coerce applied to
  the `0.74f` constant) verified on emulator captures (EN + KO screenshots in
  the report dir). versionCode 2 / versionName 0.2.0-beta001 (a Galaxy can
  verify it has the new APK). Debug-only manifest export of QrScannerActivity
  for adb QA; release stays non-exported.
- **Parity**: EN/KO string keys 230 = 230; nav is locale-independent;
  Diagnostics stays `BuildConfig.DEBUG`-gated (release hides it for BOTH
  locales). The old Galaxy observation is consistent with an outdated APK.
- Android regression: unit tests (incl. new zxing decode round-trip),
  assembleDebug + assembleRelease successful.

### BETA-001B — Local recipes (report: `docs/reports/beta001b-local-recipes/`)

- **CLI 0.19.0-beta.1**: `recipe add|list|show|run|remove` (+`--replace`,
  `--yes`, `--prompt`, `--prompt-file`, `--cwd`); interactive wizard;
  duplicates protected; credential-shaped content warned (y/N on TTY).
- **Storage**: `%USERPROFILE%\.pigeonhub\recipes.v1.json`, atomic writes,
  corrupt file never wiped; non-Latin names get generated ids.
- **Safety**: argv template with single `{prompt}` placeholder replaced inside
  tokens; spawn is argv-based; injection probes proven inert; recipes never
  send command/prompt/cwd to the worker (tests deserialize every event).
- **Lifecycle reuse**: `run_recipe` → existing `run_job` (shim handling,
  RUNNING-first policy, exit codes, PIGEONHUB_JOB_ID injection). Official
  agent hooks now attach to an inherited `PIGEONHUB_JOB_ID` (one execution =
  one card) and skip job_name so the card keeps the recipe name.
- **Verified on Windows**: packaged exe runs all five verbs; silent installer
  upgrade 0.18→0.19 on the real install (PATH single segment, credentials
  sha256 unchanged, recipes preserved); real `codex exec` via recipe (exit 0);
  68.7 s long job with PROGRESS×3; Ctrl+C clean; Python 56/56.
- Honest findings: installed Codex CLI's Stop-hook payload no longer maps to
  the MVP-017 adapter (pre-existing; owner follow-up). `PigeonHub-E2E-API36`
  emulator AVD hangs on snapshot load (use `Medium_Phone_API_36.1`).

## Owner actions / next steps

1. **Galaxy E2E (BETA-001A)**: install updated APK (versionCode 2 /
   `0.2.0-beta001`) + new installer → fresh `pigeonhub login` → QR image
   opens → Galaxy 연결 → PC 연결 → scan inside the frame → approve →
   `pigeonhub status` → real job RUNNING→DONE. Checklist:
   `docs/reports/beta001a-qr-pairing-locale/FINAL-REPORT.md`.
2. **Token cleanup (AFTER step 1 works — never before)**: revoke the
   test-minted connector token with the exact command in
   `docs/reports/mvp0175-mvp018-overnight/SECURITY.md`.
3. Confirm the Galaxy's app version — its old APK explains the
   diagnostics-menu observation (release builds hide 진단 for both locales).
4. Codex hook payload re-audit vs `pigeonhub/agents.py` event mapping.
5. Optional: screenshot a recipe job card on a registered Android device.

## Working-tree notes

- `git stash@{0}` holds a re-dump of `ui.xml` (Galaxy UI hierarchy capture,
  onboarding screen, en-US). Not needed by either report; pop or drop at will.
- Leftover `C:\PigeonHub\emu-*.log` files are emulator stdout/stderr captures
  (untracked); delete once the emulator is closed (files lock while running).
