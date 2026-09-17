# RELEASE-CHECKLIST — PigeonHub Windows beta.1 (0.18.0-beta.1)

## Artifacts (gitignored; rebuild with `packaging/windows/build.ps1`)

- [x] `dist/pigeonhub.exe` — PyInstaller onefile, 8.9 MB, Python-free
- [x] `dist/PigeonHub-Setup-0.18.0-beta.1.exe` — Inno Setup per-user installer, 10.8 MB
- [x] `dist/SHA256SUMS.txt` — matches both binaries

## Gates

- [x] Python suite 14/14 (incl. version single-source, first-run banner,
      `.cmd` shim regression, zcode strict-schema regression)
- [x] Worker `npm run typecheck`
- [x] Android `:app:testDebugUnitTest` + `:app:assembleDebug` +
      `:app:assembleRelease` all BUILD SUCCESSFUL (no Android code changed)
- [x] `git diff --check` clean
- [x] Scoped secret scan: no credential-shaped values in production files
- [x] Windows Defender scan of `dist/`: no threats
- [x] Upgrade / uninstall / reinstall matrix (INSTALLER-QA #18–20)
- [x] PATH add/remove exact-segment safe; user PATH restored byte-wise on
      uninstall (17 entries before = 17 after)

## Release readiness verdict

**CONDITIONAL_WINDOWS_BETA_READY** — see FINAL-REPORT §Verdict for the exact
conditions (owner actions are operational, not build blockers).

## Owner actions before wide distribution (max 5, in priority order)

1. **Physical Galaxy QR E2E** — pair the PC properly (replaces the overnight
   test-minted connector token, see SECURITY.md) and confirm the login
   approval UX on a real phone.
2. **Claude quota** — top up, then run one `claude -p` task; hooks are already
   installed; expected RUNNING→DONE to Android (same pipeline as verified
   Codex).
3. **ZCode standalone login** — run `zcode login` once (browser OAuth) so
   `zcode -p` sessions fire hooks; or simply restart the ZCode desktop app to
   load the hooks for desktop sessions (no login needed).
4. **Codex hook trust** — open Codex TUI → `/hooks` → trust the PigeonHub
   handlers once (until then hooks silently no-op in normal sessions).
5. **Code-signing certificate** — optional pre-wide-beta; without it
   SmartScreen warns on first run.
