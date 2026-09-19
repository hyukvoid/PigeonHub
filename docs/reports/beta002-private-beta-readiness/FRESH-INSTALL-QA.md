# FRESH-INSTALL-QA — BETA-002

Two evidence tracks are kept deliberately separate:

- **DEV machine**: the developer's working machine (has Python, project venv,
  repo checkout). Used for automated suites.
- **FRESH_USER**: the installer-built product in a shell stripped of Python
  and run outside the repo — the closest reproducible approximation of a
  first-time user on this machine (Windows Sandbox/clean VM unavailable:
  non-admin shell, feature not enabled).

## FRESH_USER evidence (all on 0.19.0-beta.2)

| Step | Command/Action | Result |
|---|---|---|
| Install | `PigeonHub-Setup-0.19.0-beta.2.exe /VERYSILENT` over beta.1 | registry DisplayVersion `0.19.0-beta.2` |
| Fresh shell PATH | PowerShell with every `*Python*` segment removed; cwd `Downloads` | — |
| `where python` | WindowsApps **Store stub only** (no real interpreter) | NO_PYTHON satisfied |
| `where pigeonhub` | `C:\Users\user\AppData\Local\Programs\PigeonHub\pigeonhub.exe` | PATH registration works |
| `pigeonhub --version` | `PigeonHub CLI 0.19.0-beta.2` | runs with no Python |
| bare `pigeonhub` | banner: not-connected/connected guidance → `pigeonhub login`, `pigeonhub run`, one-line Recipe hint | FIRST_RUN clear |
| `pigeonhub status` | `Connected - channel …` / `Server: reachable` | M copy improvement live |
| `pigeonhub login` | full flow against local worker: request → QR PNG rendered → poll → approved → credentials saved (exit 0) | PACKAGED_LOGIN |
| First job | `pigeonhub run --name "Fresh user first job" -- ping -n 6 127.0.0.1` | `RUNNING OK` → `DONE OK`, exit 0 — the exact QUICKSTART command |

Note (pipeline artifact, not a user issue): when stdout is a pipe and the
process is force-killed, buffered early output is lost; interactive terminals
(TTY) are line-buffered and show everything. The full-flow smoke above ends
normally and prints, proving the flow.

## FRESH_ANDROID evidence

Release APK (`versionCode 2`, `0.2.0-beta001`) installed on a clean emulator
image (cold-booted, previous app data removed via uninstall):

- First launch → onboarding purpose screen renders **Korean** (system
  locale ko-KR): `fresh-onboarding-ko.png`.
- App locale switched to English (per-app locale) → same screen fully
  English, identical structure: `fresh-onboarding-en.png`.
- Onboarding (invite code → notifications) is the entry for a brand-new
  user; no prior device state is required to reach it.
- Registration (and therefore a live Inbox screenshot) still requires an
  invite code — the inbox-level fresh-user walkthrough remains with the
  owner; see PRIVATE-BETA-CHECKLIST.

## Installer upgrade (repeat, DEV machine but real install)

beta.1 → beta.2 silent upgrade on the live install: PATH single segment,
`pigeonhub status` Connected (credentials preserved), recipes file intact.
Full matrix from BETA-001B (credentials sha256 identical, recipes preserved)
recorded in `docs/reports/beta001b-local-recipes/WINDOWS-QA.md`.

## Uninstall / reinstall (real install)

- `unins000.exe /VERYSILENT`: install directory removed; **User PATH no
  longer contains a PigeonHub segment**; `%USERPROFILE%\.pigeonhub\`
  untouched (credentials.json sha256 identical, recipes.v1.json intact,
  agent state intact) — matching the documented installer policy of never
  touching user data.
- Reinstall beta.2: PATH segment restored (exactly one), `--version` and
  `status` work, both recipes still listed and runnable.
