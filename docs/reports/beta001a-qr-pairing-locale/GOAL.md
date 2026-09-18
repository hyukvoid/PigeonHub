# GOAL — BETA-001A Real-device QR Pairing UX + Locale Parity (2026-09-19)

Branch: `autonomous/beta001a-qr-pairing-locale-20260918` (from `4a47d89`).

Make the very first PC pairing work without explanation:

- PC: `pigeonhub login` → a real raster QR image opens automatically.
- Phone: PigeonHub → Connections → Connect PC → scan inside a visible frame.
- KO/EN differ only in strings and formatting; product structure is identical.

## Scope

- PART A: Windows `pigeonhub login` renders a ≥512px pure black/white PNG QR
  (4-module quiet zone, square modules, integer scaling), opens it in the
  default image viewer, deletes it on success / expiry / cancel / Ctrl+C.
  Terminal ASCII QR is demoted to fallback. Payload stays the existing
  short-lived, single-use pairing request (`request_id` + `challenge`); no
  long-lived credential is ever encoded, and the poll secret never enters the
  QR.
- PART B: Android scanner gets a centered rounded-bracket scan frame with a
  subtle outside dim, localized title/hint, subtle haptic on recognition, a
  frozen preview (one scan = one approval), and localized invalid/expired
  outcomes (expired distinguished via the worker's HTTP 410).
- PART C: KO/EN parity audit — string keys must match 1:1, navigation must be
  identical across locales; Diagnostics stays gated by `BuildConfig.DEBUG`
  (build type), never by locale. User-facing hardcoded English moves into the
  string resources.
- PART D: real-Galaxy E2E remains a documented owner action (no physical
  device is attached to this run).
- PART E: the test-minted connector token is revoked by the owner AFTER the
  new pairing is verified (command already recorded in MVP-017.5 SECURITY.md).

## Non-goals

No desktop companion, no tray app, no background daemon, no camera-system
rewrite, no new pairing protocol, no NFC/Bluetooth, no account redesign.
Pairing protocol, Worker routes, and job semantics are untouched.
