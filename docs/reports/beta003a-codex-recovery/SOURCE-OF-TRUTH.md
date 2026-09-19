# SOURCE OF TRUTH — BETA-003A

Everything below is measured from the code and the machine, not from intent.

## Lineage

| Milestone | Commit |
| --- | --- |
| BETA-002 | `9076e88` |
| BETA-003 (base of this campaign) | `ee6308f` |
| BETA-003A codex auto-connect + privacy + dedup | `45e2bd5` |
| BETA-003A Setup Center state + returning user | `35e6078` |
| BETA-003A Android pairing-state UX | `ceda445` |
| BETA-003A negative-path + dedup chain tests | `6ed652d` |
| BETA-003A packaged E2E + QA evidence tools | `077888c` |
| BETA-003A test isolation + honest delivery notes | `6f2660d` |

## Versions shipped by this campaign

- Windows CLI: **0.20.0-beta.2** (`pigeonhub/__init__.py`, single source for
  PyInstaller + Inno Setup)
- Android: **versionCode 4 / 0.3.0-beta003a** (`android/app/build.gradle.kts`)

## Release artifacts (built and verified on this machine)

| Artifact | SHA-256 (prefix) |
| --- | --- |
| `dist/pigeonhub.exe` | `b499bb70070ef1d2…` |
| `dist/PigeonHub-Setup-0.20.0-beta.2.exe` | `2048acb7c16d0f1d…` |

Full digests in `dist/SHA256SUMS.txt` (verified with `sha256sum -c`; all OK).

## Test inventory after this campaign

- Python: **103 tests** (was 90 at BETA-003). Net +13: 15 codex
  auto-connect/negative/dedup tests (8 existed pre-campaign in the working
  tree, +7 added), +9 onboard tests (state accuracy, returning user, codex
  routing), +1 Android-parity-side PcPairingState is Kotlin, +1 delivery-note
  regression. Full suite green (one unrelated flake observed once in
  `test_cross_origin_post_rejected` under full-suite socket churn; passed in
  isolation, in its file, and in the final full run).
- Android: unit tests green (incl. new `PcPairingStateTest` 3 tests);
  `assembleDebug` + `assembleRelease` OK.
- Worker: `tsc --noEmit` clean.
