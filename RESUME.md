# RESUME — BETA-003A Codex Zero-Command Auto-Connect + Recovery (2026-09-20)

Branch: `autonomous/beta003a-codex-recovery-20260920` (base: BETA-003
`ee6308f`). Lineage: BETA-002 (`9076e88`) → BETA-003 (`ee6308f`) →
BETA-003A (HEAD). Report: `docs/reports/beta003a-codex-recovery/`
(FINAL-REPORT.md has the full scoreboard, Q1–Q10, and verdict
**CONDITIONAL_PRIVATE_BETA_READY**).

## State (source of truth)

### What BETA-003A added on top of BETA-003

- **Codex auto-connect (zero commands)**: `pigeonhub/codex_integration.py` —
  installs one managed `notify = ["…\pigeonhub.exe", "internal-codex-notify"]`
  line into `~/.codex/config.toml` (text-level edit; comments/profiles/
  unknown keys preserved; backup + atomic + verify + rollback; idempotent;
  foreign notify preserved as NEEDS_ATTENTION). Triggers: Setup Center
  pairing approval (BETA-003 consent checkbox), `pigeonhub login` (Enter =
  yes / `--no-auto-connect`), and the tools screen's SETUP_CODEX button
  (now routed to the notify engine — the Setup Center can never write
  hooks.json). Hook-based full lifecycle stays an explicit
  `pigeonhub setup codex` CLI choice (needs Codex's own trust).
- **Privacy**: the callback allowlists only type/thread-id/turn-id;
  prompt (`input-messages`), assistant text, cwd, usage are structurally
  dropped at the parser. Marker `PRIVATE_CODEX_PROMPT_MARKER_003A` proven
  absent from every publish (unit + packaged E2E capture).
- **Dedup**: `pigeonhub run codex …` sets `PIGEONHUB_CODEX_BRIDGED=1` in
  the child env, suppressing the native notify (one execution = one card);
  notify replays are guarded by a bounded local state file
  (`codex-notify-state.json`, recorded only after successful publish).
- **Android pairing state**: `PcPairingState` (NOT_PAIRED/PAIRED/STALE)
  persisted in DataStore; PAIRED removes the primary [PC 연결] CTA; approval
  flips the card immediately; unknown stored values degrade safely
  (`PcPairingStateTest`).
- **Setup Center returning user**: `/api/status` now reports
  `already_paired`; the phone step shows "이미 연결돼 있어요 ✓ / 다음" instead
  of re-demanding a QR. Codex card state derives from the real integration
  source (notify plan first, trusted managed hooks fallback).
- **Honest CLI notes**: `_delivery_note` no longer prints OK for a failed
  publish (401 case); injection test no longer leaks to the real worker.
- **Evidence**: real 0.152.1 notify payload captured
  (`tools/agent-e2e-sandbox/notify_probe.log`); packaged-exe E2E
  (`packaged_codex_e2e.py`) = PASS end to end; silent upgrade
  0.19.0-beta.2 → 0.20.0-beta.2 + uninstall/reinstall with byte-identical
  credentials/recipes/hooks.

### Key upstream reality (documented, do not "fix" later)

`notify` is a single slot. On the owner's machine it holds Codex Desktop's
own computer-use bridge (`codex-computer-use.exe turn-ended`) — preserved;
Codex stays connected there via the user-trusted hooks (Setup Center shows
`연결됨 ✓ (hooks)`). Fresh machines get the notify path cleanly.

### Versions & artifacts

- CLI **0.20.0-beta.2**: `dist/pigeonhub.exe`,
  `dist/PigeonHub-Setup-0.20.0-beta.2.exe` (SHA256SUMS verified; Defender
  0 threats).
- Android **versionCode 4 / 0.3.0-beta003a**: assembleDebug + assembleRelease.
- Gates: Python 103/103, worker `tsc` clean, KO/EN 234=234, diff-check +
  secret scans clean.

## Owner actions / next steps

1. New APK on the Galaxy → Setup Center QR pairing → PC card shows 연결됨,
   no [PC 연결] CTA, no app restart.
2. Reopen Setup Center → "이미 연결됨" fast path (no QR re-scan).
3. Real Codex Desktop task → Galaxy notification.
4. Reboot Windows once → no re-pairing, Codex notifications intact.
5. Then the BETA-002 dogfood/beta plan (refresh `dist/beta-bundle/` with
   the beta.2 artifacts when distributing).

## Environment notes (carried + new)

- Inno switches must use `/FLAG` (a leading `-` is silently ignored →
  interactive wizard). From Git Bash, `/FLAG` gets path-mangled — run the
  installer via PowerShell `Start-Process -ArgumentList '/VERYSILENT',…`.
- `WizardIsUpgrade` unavailable in this IS6 build (uninstall-key check is
  used); `PigeonHub-E2E-API36` AVD hangs on snapshots (use
  `Medium_Phone_API_36.1 -no-snapshot`).
- This machine's Codex `notify` slot is occupied by Codex itself — never
  overwrite; auto-connect reports NEEDS_ATTENTION and hooks carry the load.
- `git stash@{0}` (old Galaxy ui.xml dump) still stashed.
- Local untracked leftovers kept intentionally:
  `emu-*.log`, `tmp_hookdump.py` (session probes).

---

### BETA-003 (previous night) — One-click onboarding (`docs/reports/beta003-onboarding-ux/`)

- Ephemeral Setup Center (`pigeonhub onboard`): loopback-only random-port
  server + browser page (Welcome → Connect phone → Connect tools → Test →
  Done), KO/EN, dies with the session; no daemon/tray/service.
- Security: session-token gate (invalid/expired = 404), POST-only
  mutations with same-origin + confirm, fixed action allowlist, secrets
  never serialized/logged (16-test suite, carried forward green).
- Installer: Finish-page "Set up PigeonHub now" (fresh only), Start Menu
  "PigeonHub Setup" shortcut; upgrades never auto-launch.
- RC 0.20.0-beta.1 / Android 0.3.0-beta003 — superseded by beta.2 /
  beta003a above.
