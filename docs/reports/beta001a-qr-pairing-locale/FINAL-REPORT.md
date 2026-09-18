# FINAL REPORT — BETA-001A Real-device QR Pairing UX + Locale Parity

Branch: `autonomous/beta001a-qr-pairing-locale-20260918` (from `4a47d89`).

## What shipped

**PART A — Windows QR presentation (CLI 0.19.0-beta.1 tree)**
- `pigeonhub login` now renders a real raster QR PNG (pure black/white,
  square modules, integer pixel scaling, 4-module quiet zone, ≥512px side)
  and opens it in the default image viewer via `os.startfile`.
- New UX text: `Connect this PC` → viewer opens → `On your phone: PigeonHub →
  Connections → Connect PC` → live `Waiting for approval... Expires in MM:SS`
  (single-line refresh on a TTY; throttled to once a minute when piped, capped
  at 99:59 against bogus server clocks).
- Temporary PNG lives in `%TEMP%` and is deleted on success, poll failure,
  expiry, and Ctrl+C (`finally` + unit tests for all three paths).
- Terminal ASCII QR is kept strictly as fallback (PNG render failure or
  viewer-open failure); it is no longer the primary UI.
- The QR payload is unchanged and stays short-lived/single-use: the existing
  pairing `request_id` + `challenge` only. The poll secret stays in the PC
  process and never enters the QR; no long-lived credential is ever encoded;
  the payload is never printed to logs or reports.
- `pyproject.toml` moves to `qrcode[pil]`; `pigeonhub_entry.py` imports
  `qrcode.image.pil` statically so PyInstaller bundles Pillow (the library's
  PIL factory is imported lazily and would otherwise be missed onefile).
- `PIGEONHUB_NO_OPEN=1` suppresses the viewer for headless/CI QA; the run
  still proves the PNG was rendered. Used by the packaged-exe smoke test.

**PART B — Android scanner**
- New `ScanFrameView` (View subclass): centered responsive square
  (74% of the shorter side, clamped 240–280dp), rounded white corner brackets,
  subtle translucent dark scrim outside the frame (EVEN_ODD path), crisp
  camera preview inside. Reads in both light and dark.
- New hint panel with localized strings: EN "Scan the QR code / Place the
  PigeonHub login QR inside the frame." KO "QR 코드 스캔 / PC에 표시된
  PigeonHub QR 코드를 네모 안에 맞춰주세요." The old hardcoded English
  "Point the camera at the PigeonHub login QR" is gone.
- Recognition: one scan = one approval — the camera stops immediately, a
  subtle 40 ms haptic fires (`VIBRATE` normal permission added), and the
  approval dialog opens. Re-scanning the same frame is impossible.
- Invalid QR: localized snackbar "This is not a PigeonHub login QR." /
  "PigeonHub QR 코드가 아닙니다."
- Expired QR: `PairingApi.approveLoginRequest` now returns `ApprovalResult(ok,
  expired)`; the worker's HTTP 410 (or an "expired" error body) maps to
  "The QR code has expired. Run `pigeonhub login` on your PC again." /
  "QR 코드가 만료되었습니다. PC에서 `pigeonhub login`을 다시 실행해주세요."
- Success/failure snackbars are localized: "PC connected"/"PC가 연결됐어요",
  "Unable to connect this PC"/"PC를 연결하지 못했어요".
- Fix found during device QA: Kotlin operator precedence silently coerced the
  `0.74f` constant instead of the computed side (`minOf(w,h) * 0.74f
  .coerceAtMost(...)` parses as `minOf(w,h) * (0.74f.coerceAtMost(...))`),
  pushing the frame off-screen; verified fixed on emulator captures.
- `versionCode 2`, `versionName 0.2.0-beta001` so a real Galaxy can be checked
  for the up-to-date APK (Settings → App info) — the KO-device-vs-EN-emulator
  mismatch must first be a version mismatch check.
- Debug-only manifest overlay exports `QrScannerActivity` for adb-driven QA;
  the release manifest keeps it non-exported.

**PART C — KO/EN parity**
- String keys: EN 230 = KO 230 (224 before + 6 new scanner/pairing keys on
  both sides, verified by script; no EN-only or KO-only keys).
- Navigation structure is locale-independent by construction: `Section` enum
  + `RELEASE_SECTIONS`/`DEBUG_SECTIONS` in PigeonHubApp.kt depend only on
  `BuildConfig.DEBUG`. Diagnostics ("진단"/"Diagnostics") is hidden in release
  for BOTH locales and present in debug for BOTH locales. No
  locale-conditional navigation code exists (audited: no
  `Locale`/`getLanguage`/`"ko"` branches in app code).
- Release APK: KO and EN are structurally identical; only strings differ.
- The observed Galaxy-vs-emulator difference is consistent with an older APK
  on the phone (the Diagnostics tab predates its release-hiding in MVP-002A
  and the phone last ran an older install); `versionCode/Name` bump enables
  the owner to confirm this on the device in one glance.

## Scoreboard

| Gate | Status |
|---|---|
| QR_RASTER_RENDER | VERIFIED — ≥512px pure black/white PNG, 4-module quiet zone, integer scaling (unit test asserts size/colors; rendered inside the packaged exe too) |
| QR_AUTO_OPEN | IMPLEMENTED — `os.startfile` opens the default viewer; headless QA used `PIGEONHUB_NO_OPEN`, so the literal viewer-open was not observed in this run |
| QR_TEMP_CLEANUP | VERIFIED — temp PNG deleted on success, poll error, and Ctrl+C (unit tests) |
| QR_SHORT_LIVED_TOKEN | VERIFIED — QR carries only `request_id`+`challenge`; poll secret/credentials never encoded or printed |
| SCANNER_GUIDE | VERIFIED — emulator captures EN + KO: centered square, rounded brackets, hint panel |
| SCANNER_DIM_OVERLAY | VERIFIED — same captures: scrim outside, clear preview inside |
| INVALID_QR_UX | IMPLEMENTED — localized invalid-QR snackbar wired; needs a physical scan to observe |
| EXPIRED_QR_UX | IMPLEMENTED — worker HTTP 410 distinguished, localized message wired; needs a real expired scan to observe |
| KO_EN_STRING_PARITY | VERIFIED — 230 = 230 keys EN/KO (scripted diff), new keys present in both |
| KO_EN_NAV_PARITY | VERIFIED — nav built from locale-independent enum; DEBUG/RELEASE split only |
| RELEASE_DIAGNOSTICS_HIDDEN | VERIFIED — `BuildConfig.DEBUG` gate in PigeonHubApp.kt; release builds exclude Device for both locales |
| EMULATOR_QR_SCAN | DEFERRED_OWNER_ACTION — emulator's virtual-scene camera contains no QR; the decode stack itself is pinned by a zxing round-trip unit test (`QrScannerDecodeTest`), and the scanner UI/UX was verified optically on the emulator |
| GALAXY_QR_SCAN | DEFERRED_OWNER_ACTION |
| GALAXY_APPROVAL | DEFERRED_OWNER_ACTION |
| PACKAGED_CLI_CONNECTED | IMPLEMENTED — packaged `pigeonhub.exe` completed the full flow (create request → render PNG → poll → approved → credentials saved) against a local test worker; production-worker approval requires the phone |
| REAL_JOB_AFTER_PAIRING | DEFERRED_OWNER_ACTION |
| TEST_TOKEN_REVOKED | DEFERRED_OWNER_ACTION — exact command in MVP-017.5 SECURITY.md; run ONLY after the new pairing is confirmed working |

## Regression

- Python: 56/56 (13 CLI incl. new QR tests, 33 recipe unit/E2E, agent events).
- Android: unit tests + `assembleDebug` + `assembleRelease` BUILD SUCCESSFUL.
- Packaged CLI: PyInstaller onefile + Inno Setup installer rebuilt;
  full login flow smoke-tested in the exe.
- `git diff --check` clean; secret scan clean.
- Pairing protocol and Worker routes untouched (QR payload format identical;
  Android approve route consumption unchanged apart from surfacing 410).

## Owner checklist (real Galaxy)

1. Install `dist/PigeonHub-Setup-*.exe` / updated APK (Android `versionCode 2`
   / `0.2.0-beta001`) on the Galaxy; confirm the version in App info.
2. Fresh PowerShell: `pigeonhub login` → the QR image should open by itself.
3. Galaxy: PigeonHub → 연결 → PC 연결 → scan the QR **inside the frame**;
   feel the subtle buzz.
4. Confirm the PC card shows the right machine → 승인.
5. PC prints `Logged in to channel ...`; `pigeonhub status` shows reachable.
6. `pigeonhub run --name "Galaxy pairing test" -- python -c "import time; time.sleep(5)"` →
   RUNNING → DONE on the phone.
7. Only after step 6 works: revoke the old test-minted connector token —
   `wrangler d1 execute pigeonhub-messages --remote --command "DELETE FROM connector_tokens WHERE channel_id='ch_d7c527c2ab5e43cfa6d1' AND created_at='2026-09-17T16:45:00.000Z'"`
   (from MVP-017.5 SECURITY.md — never earlier, never in reverse order).
