# E2E-EVIDENCE — BETA-003

## Automated

- **Setup Center suite**: `tests/test_pigeonhub_onboard.py` — **16 passed**
  (binding, session gate, expiry, GET-is-read-only, cross-origin POST, unknown
  actions, confirm-required, engine delegation/no-op/remove, pairing flow
  pending→approved with QR PNG served and poll secret never serialized,
  expiry presentation, page KO/EN parity without embedding the token,
  FINISH shutdown, SET_LANG session preference).
- **Full regression**: `python -m pytest tests/ -q` → **90 passed**
  (CLI incl. login/QR/recipes/codex-compat/negative paths + onboard).
- **Android**: `assembleDebug`, `assembleRelease`, `testDebugUnitTest` —
  BUILD SUCCESSFUL (versionCode 3 / 0.3.0-beta003; agent-detail PC-setup
  guidance added, KO/EN).
- **Worker**: untouched this campaign (last typecheck clean in BETA-002).

## Packaging

- `packaging/windows/build.ps1` full rebuild at **0.20.0-beta.1**:
  `pigeonhub.exe` + `PigeonHub-Setup-0.20.0-beta.1.exe` + SHA256SUMS.
- Installer now compiles with: Start Menu "PigeonHub Setup" (onboard mode),
  optional desktop shortcut, post-install "Set up PigeonHub now" (fresh
  installs only; `skipifsilent`), updated Finish copy.
  (Two Inno scripting fixes were needed: `WizardIsUpgrade` is not available
  in this IS6 build → uninstall-key check; preprocessing of
  `SetupSetting("AppId")` inside strings → explicit `#define UninstallKey`.)
- Defender custom scan of `dist/`: 0 threats. `git diff --check` clean;
  secret scan clean.

## Packaged onboard smoke (real exe)

```
$ ./dist/pigeonhub.exe onboard          # process stays alive
$ netstat -ano | findstr <pid> | findstr LISTENING
  TCP    127.0.0.1:51283    0.0.0.0:0    LISTENING    <pid>
$ GET /setup (no session token)  → 404
$ terminate process              → server gone
```
Loopback-only, random port, session contract enforced inside the packaged
exe; ephemeral lifetime confirmed.

## Manual interactive pass (owner)

The full optical loop (installer Finish → browser → scan QR with a real
phone → trust Codex → phone shows the test notification) needs a physical
device and stays in the owner checklist — the BETA-001A Galaxy checklist
covers the pairing half; Setup Center adds only the in-page QR and dialogs.
