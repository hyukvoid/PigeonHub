# SECURITY — BETA-003A

## Trust boundary verdict

**No Codex trust was bypassed.** The integration uses only:

- a plain user-config assignment (`notify`) that upstream Codex documents
  for third-party notification programs — no hooks.json auto-install, no
  trust-DB access, no Codex metadata reads/writes, no bypass flags, no
  binary patching, no process injection;
- Codex's own trust flow remains the only path to hook-based lifecycle
  (`pigeonhub setup codex` stays an explicit, separate user choice).

## Callback surface hardening

`internal-codex-notify` is a **payload parser, not a command engine**:

- no subcommands, no argv execution, no shell, no path parameters;
- allowlist-only field access (type + thread-id + turn-id);
- fail-closed: any malformed, oversized, or unknown input exits 0 silently;
- output contains only fixed safe strings and sanitized opaque ids;
- exception text is never printed (it could carry payload fragments).

## Setup Center (carried from BETA-003, re-verified)

loopback-only random port · session-token gate (404 on invalid/expired) ·
POST-only mutations with same-origin check + confirm · fixed action
allowlist (no command execution) · secrets never serialized · new in
BETA-003A: `already_paired` is the only addition to `/api/status` (a
boolean derived from credential existence — no new secret surface), and
`SETUP_CODEX` now routes to the notify engine (fewer write surfaces: the
Setup Center can never write `hooks.json`).

## Privacy boundary

See CODEX-PRIVACY.md — structural drop at the parser, unique-marker proof
in unit tests and in the packaged E2E's captured network request.

## Local data written by the integration

| File | Content |
| --- | --- |
| `~/.codex/config.toml` | one managed `notify` assignment (+ timestamped backups during install) |
| `~/.pigeonhub/codex-notify-state.json` | sanitized `thread:turn` keys → timestamps (dedup), bounded 200 |

Neither contains prompt/assistant content by construction.

## Test-suite hygiene fix (this campaign)

`RealSubprocessInjectionTests` was publishing its malicious-prompt fixture
to whatever worker credentials the developer machine had — a real isolation
defect from BETA-001B exposed tonight by a worker-side rejection. It now
sandboxes publishing; the child remains a real subprocess.
