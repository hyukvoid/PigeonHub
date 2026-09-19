FRESH-USER QA — BETA-003A (packaged exe, Python-free, no phone)

## Environment assumptions honored

- No Python on PATH is required for any Codex path: the notify target is
  `pigeonhub.exe` itself (`internal-codex-notify`), never `python notify.py`.
  The packaged run proves the frozen exe resolves its own argv.
- The auto-connect consent defaults are the BETA-003 ones: interactive
  terminal → Enter = yes; non-interactive → yes; `--no-auto-connect` → no.

## Packaged end-to-end run (the real path, local fake worker)

`tools/agent-e2e-sandbox/packaged_codex_e2e.py` against
`dist/pigeonhub.exe` 0.20.0-beta.2:

| Step | Result |
| --- | --- |
| `pigeonhub.exe login --worker-url <local>` renders QR | rc 0 (PIGEONHUB_NO_OPEN) |
| pairing request created | exactly 1 |
| phone approval (simulated by the fake worker) | approved payload → credentials saved to sandbox home |
| auto-connect consent (Enter) | accepted |
| Codex detected | `Detected: yes (codex-cli 0.152.1)` |
| Codex auto-connect | `Codex: connected (completion notifications)`; sandbox `~/.codex/config.toml` created with the managed notify line |
| notify callback with a real-shape 0.152.1 payload | exactly **1** publish: `codex / DONE / "Codex: done" / "Codex session"` |
| replayed callback | 0 publishes |
| `PRIVATE_CODEX_PROMPT_MARKER_003A` / cwd in the worker request | absent |
| Verdict | **PACKAGED_E2E: PASS** |

This is the exact fresh-user journey minus the physical phone: install →
pair → Codex detected → auto-connected → completion event reaches the
worker with zero typed commands and zero config edits by the user.

## Install/upgrade tracks (real installer runs tonight)

| Track | Result |
| --- | --- |
| Upgrade install over the previous installer (0.19.0-beta.2 → 0.20.0-beta.2, silent) | OK; credentials/recipes/hooks.json byte-identical; foreign Codex notify untouched; no auto-launch on upgrade |
| Uninstall (silent) | install dir, uninstall key, PATH entry removed; `~/.pigeonhub` untouched |
| Reinstall (silent) | binary 0.20.0-beta.2; PATH restored; credentials byte-identical; new process logged in + worker reachable |
