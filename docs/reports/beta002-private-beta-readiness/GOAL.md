# GOAL — BETA-002 Private Beta Readiness + Dogfood Harness (2026-09-19)

Branch: `autonomous/beta002-private-beta-readiness-20260919` (base `9b053d9`,
the BETA-001B HEAD; BETA-001A is `a0c0cf3` on the same lineage).

No new features. This campaign proves the product works from a **first-time
user's perspective** and prepares the 5–7 day dogfood + small private beta:

```
Fresh Windows → Install → pigeonhub login → Phone pairing
             → pigeonhub run → RUNNING → DONE
Repeat work → pigeonhub recipe run <name>
```

## Scope

- **PART A (P0)**: the Codex compatibility regression found in BETA-001B —
  root-caused (exec mode does not execute hooks.json hooks on codex-cli
  0.152.1; captured experimentally), the public `exec --json` stream captured
  and pinned as sanitized fixtures, the bridge hardened/tested, real E2E
  re-verified (bridge events converge on the recipe card).
- **PART B**: RC version `0.19.0-beta.2`; Windows exe + installer + Android
  release APK rebuilt.
- **PART C/D**: fresh-user verification — Windows clean PATH (no Python),
  fresh shell, install/first-run/login/run; Android clean install with
  onboarding in KO and EN.
- **PART F/G/H**: dogfood journal template, QUICKSTART (OS-native first job,
  Recipe as optional), beta bundle (`dist/beta-bundle/`) with SHA256SUMS and
  a single KNOWN-ISSUES page.
- **PART I/J/K/L**: negative-path QA (network before/after RUNNING, dead
  pairing, recipe corruption, missing executable, installer upgrade,
  uninstall/reinstall), Android/agent/recipe regressions.
- **PART M**: minimal `pigeonhub status` copy improvement only (no doctor
  command).

## Non-goals

No new agents/connectors, no remote launch, no scheduler/daemon/tray, no
analytics backend, no recipe sync/marketplace, no dashboard, no account
system, no auto-updater.
