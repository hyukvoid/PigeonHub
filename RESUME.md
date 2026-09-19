# RESUME — BETA-002 Private Beta Readiness (2026-09-19)

Branch: `autonomous/beta002-private-beta-readiness-20260919` (base `9b053d9`).
Lineage: MVP-019.1 → BETA-001A (`a0c0cf3`) → BETA-001B (`9b053d9`) → BETA-002
(HEAD commit).

## State (source of truth)

- **RC**: Windows CLI **0.19.0-beta.2** (`pigeonhub/__init__.py`), Android
  unchanged at `versionCode 2 / 0.2.0-beta001`. Verdict:
  **CONDITIONAL_PRIVATE_BETA_READY** — the one condition is the owner's
  real-Galaxy pairing pass.
- **Codex regression root-caused and closed** (`CODEX-COMPAT.md`): exec mode
  on codex-cli 0.152.1 does NOT execute hooks.json hooks (marker experiment,
  twice); hooks remain the interactive path. The official exec-mode route is
  the `codex exec --json` bridge — hardened (`lifecycle_from_line` drops item
  text/usage structurally), pinned by sanitized fixtures
  (`tests/test_pigeonhub_codex_compat.py`), and verified live: recipe →
  bridge → real Codex produced RUNNING→PROGRESS×2→DONE on ONE card.
- **Fresh-user track** (`FRESH-INSTALL-QA.md`): clean no-Python PATH →
  install → `where/version` → first-run → `status` (copy now "Connected /
  Server: reachable") → packaged `login` full flow → first job
  (`ping -n 6 127.0.0.1`) RUNNING→DONE. Android: clean-install onboarding
  captured in KO and EN.
- **Negative paths**: two new tests (terminal-publish connection drop with
  same-key retry ★, dead-pairing recovery guidance ★) on top of the existing
  outage/corruption/missing-executable/idempotency coverage. Uninstall →
  reinstall on the real install: binary+PATH removed, `~/.pigeonhub` fully
  preserved, reinstall restores everything.
- **Beta bundle**: `dist/beta-bundle/` — exe, installer, release APK,
  QUICKSTART.md, KNOWN-ISSUES.md, SHA256SUMS.txt (verified 5/5; Defender 0
  threats). QUICKSTART uses OS-native commands only; Recipe is optional.
- **Dogfood harness**: `docs/dogfood/DOGFOOD-TEMPLATE.md` + DOGFOOD-PLAN.md —
  5–7 days, 20+ real jobs, privacy rules (safe labels only), no analytics.
- Regression: Python **63/63**, worker typecheck clean, Android unit +
  assembleDebug + assembleRelease successful, `git diff --check` and secret
  scan clean.

## Owner actions (in order — max 5)

1. Galaxy real pairing (6 steps): APK 0.2.0-beta001 + installer →
   `pigeonhub login` → 연결 → PC 연결 → scan → 승인 → test job. Checklist:
   `docs/reports/beta002-private-beta-readiness/PRIVATE-BETA-CHECKLIST.md`.
2. AFTER the test job succeeds: revoke the old test-minted connector token
   (exact command in `docs/reports/mvp0175-mvp018-overnight/SECURITY.md`).
3. Run the 5–7 day dogfood with the journal; file P0/P1 immediately.
4. Optional, pre-beta: trust Codex hooks via `/hooks` (interactive only);
   Claude quota if Claude E2E wanted.
5. Code signing before broad distribution (post-beta).

## Environment notes

- `PigeonHub-E2E-API36` AVD hangs on snapshot load — use
  `Medium_Phone_API_36.1` (cold boot `-no-snapshot`).
- Windows Sandbox unavailable on this machine (non-admin, feature off) —
  fresh-user testing used a Python-free PATH + fresh shell + installed exe.
- `git stash@{0}` still holds the old Galaxy `ui.xml` dump (pop or drop).
- Untracked leftovers from QA: `dist/beta-bundle/` (distribution area, not
  committed), `fr-login-*.txt` in the repo root (delete freely).
