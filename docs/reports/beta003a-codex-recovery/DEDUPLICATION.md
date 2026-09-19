# DEDUPLICATION — ONE EXECUTION, ONE JOB CARD — BETA-003A

## The collision the campaign warned about

Two PigeonHub integration sources can see the same Codex execution:

1. the wrapper (`pigeonhub run codex exec …` / a recipe), which owns
   RUNNING → DONE lifecycle for its child; and
2. the native notify callback, which fires `agent-turn-complete` on the
   same machine.

Without a guard both would publish a terminal card for one execution.

## The guard (two layers)

**Layer 1 — child-local environment marker.** When `run_job` detects a
Codex command, the child environment receives
`PIGEONHUB_CODEX_BRIDGED=1`. The native notify fires from inside that
child's process tree and inherits the marker; `handle_codex_notify` returns
0 before parsing anything. The marker never leaves the machine.

**Layer 2 — replay guard.** Outside a wrapper (Codex Desktop / direct
`codex exec`), the notify handler records `thread:turn` in a local
state file (`~/.pigeonhub/codex-notify-state.json`, bounded to 200 entries)
**only after a successful publish**. A replayed or duplicate callback is
dropped; a publish that failed (network outage) records nothing, so the
retry later still notifies exactly once.

## Proof (automated, `CodexBridgeDedupTests`)

A real `.cmd` shim named `codex.cmd` runs as the wrapper child, dumps its
environment, and the test then invokes `handle_codex_notify` with the
`PIGEONHUB_CODEX_BRIDGED=1` environment the child actually inherited:

- published states are exactly `["RUNNING", "DONE"]` — one card;
- the child verifiably carried `PIGEONHUB_CODEX_BRIDGED=1`;
- the native notify under that environment publishes nothing;
- `PRIVATE_CODEX_PROMPT_MARKER_003A` appears in no published field.

## Non-Codex commands are untouched

The marker is only set for codex-like executables (`codex`, `codex-cli`,
`*-codex`). ZCode/Claude/Grok wrappers keep their BETA-001B
`PIGEONHUB_JOB_ID` attach-to-parent semantics (regression suite green).
