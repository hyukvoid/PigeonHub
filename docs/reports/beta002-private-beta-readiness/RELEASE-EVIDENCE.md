# RELEASE-EVIDENCE — BETA-002 (0.19.0-beta.2 / Android 0.2.0-beta001)

## Automated gates

| Gate | Command/Method | Result |
|---|---|---|
| Python test suite | `python -m pytest tests/ -q` | **63 passed** (unit + recipes E2E + codex compat + negative paths) |
| Worker typecheck | `npx tsc --noEmit` (worker/) | clean, exit 0 |
| Android unit tests | `testDebugUnitTest` (BETA-001A/B run; unchanged since) | PASS |
| assembleDebug | `gradlew assembleDebug` | BUILD SUCCESSFUL |
| assembleRelease | `gradlew assembleRelease` | BUILD SUCCESSFUL |
| Windows packaged CLI | `packaging/windows/build.ps1` | exe + installer built, exit 0 |
| Windows installer | Inno Setup (same script) | `PigeonHub-Setup-0.19.0-beta.2.exe` |
| git diff --check | staged diff | clean |
| Secret scan | diff-scoped credential-pattern scan | clean |
| Defender scan | `Start-MpScan -ScanType CustomScan` on `dist/beta-bundle` | 0 threats |
| SHA256 verification | `sha256sum -c SHA256SUMS.txt` (beta-bundle) | 5/5 OK |

## Artifacts (dist/beta-bundle/, checksummed)

- `pigeonhub.exe` (0.19.0-beta.2, onefile)
- `PigeonHub-Setup-0.19.0-beta.2.exe` (per-user installer)
- `PigeonHub-0.2.0-beta001-release.apk`
- `QUICKSTART.md`, `KNOWN-ISSUES.md`
- `SHA256SUMS.txt` (all five)

Version single source: `pigeonhub/__init__.py` = `0.19.0-beta.2`; Android
unchanged at `versionCode 2 / 0.2.0-beta001` (no Android code change this
campaign). Binaries are not committed to the repository — dist/ is the
distribution area per existing policy.

## FRESH_USER track (see FRESH-INSTALL-QA.md for the full table)

install → fresh no-Python shell → `where pigeonhub` → `--version` →
first-run banner → `status` → packaged `login` full flow → first job
(`ping -n 6 127.0.0.1`) RUNNING→DONE exit 0. Android: clean-install
onboarding in KO and EN (screenshots in this directory).

## Known regressions carried

`KNOWN-REGRESSIONS.md` — six items, none P0: unsigned binaries (SmartScreen),
Codex exec-mode hooks not executed (bridge provided; recipe wrapper card
still works), bridge needs repo checkout, Claude quota, Grok not installed,
real-Galaxy owner pass pending.
