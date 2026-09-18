# WINDOWS-QA — BETA-001B

## Distribution artifacts (rebuilt this run)

- `dist/pigeonhub.exe` — PyInstaller onefile, 0.19.0-beta.1
- `dist/PigeonHub-Setup-0.19.0-beta.1.exe` — Inno Setup per-user installer
- `dist/SHA256SUMS.txt` — regenerated (now covers both new artifacts)

## Packaging specifics

- Version single source: `pigeonhub/__init__.py` (0.19.0-beta.1); build script
  reads it; `--version` and `version` print it (covered by unit test).
- User-Agent now derives from `__version__` (was hardcoded `0.18`).
- `pigeonhub_entry.py` statically imports `qrcode.image.pil` so PyInstaller
  bundles Pillow for the 001A login QR (lazy import would be missed onefile).
- Recipes are stdlib-only — no new runtime dependencies for 001B.

## Verified in the packaged exe (not just source Python)

| Check | Result |
|---|---|
| `pigeonhub --version` → `PigeonHub CLI 0.19.0-beta.1` | PASS |
| `recipe add` (flags) → id printed, file written | PASS |
| `recipe list` / `recipe show` | PASS |
| `recipe run` against local worker → RUNNING OK / DONE OK, exit 0 | PASS |
| `recipe remove --yes` → removed; subsequent list = empty-state hint | PASS |
| 001A `login` inside exe renders QR PNG (`PIGEONHUB_NO_OPEN` path) | PASS |

## Installer upgrade (real machine, real install)

Executed `PigeonHub-Setup-0.19.0-beta.1.exe /VERYSILENT` over the installed
0.18.0-beta.1:

- Registry `DisplayVersion`: 0.18.0-beta.1 → **0.19.0-beta.1**
- `pigeonhub --version` (installed exe): **0.19.0-beta.1**
- User PATH: still exactly **one** `…\Programs\PigeonHub` segment
- `~/.pigeonhub/credentials.json`: sha256 **unchanged** (survives upgrade)
- `~/.pigeonhub/recipes.v1.json`: **preserved**; installed exe listed and then
  removed the probe recipe (also verifies remove on the upgraded install)
- `agent-integrations/`, `agent-state.json`: untouched

## Windows-specific behaviors re-verified

- `.cmd` batch shim: recipe running a `.cmd` by name goes through the existing
  `cmd /c` resolution (`test_windows_cmd_shim_is_routed_through_cmd`).
- Spaces in paths: `--cwd "work dir with spaces"` end-to-end.
- Korean text: recipe display name "한국어 리서치" (id auto-generated), Korean
  prompts pass through as argv data.
- Ctrl+C: separate process group; wrapper exits promptly (no 120 s hang),
  child stops, DONE is not published.
- EOF inside the interactive wizard (piped stdin): fields skip/cancel safely,
  no traceback (found and fixed during QA).

## Environment notes

- Emulator (`Medium_Phone_API_36.1`, headless cold boot) was used for BETA-001A
  scanner QA; the first AVD hung (pre-existing snapshot issue, cold-booted as
  in earlier campaigns). `PigeonHub-E2E-API36` remains flaky to boot —
  environment note, not a product issue.
