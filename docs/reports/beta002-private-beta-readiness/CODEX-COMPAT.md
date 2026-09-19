# CODEX-COMPAT — Codex CLI compatibility (BETA-002 PART A)

## What BETA-001B actually hit

The BETA-001B report flagged "the latest Codex hook payload fails adapter
mapping". BETA-002 root-caused it by experiment (codex-cli **0.152.1**):

1. Installed a temporary marker command in place of the pigeonhub Stop hook
   (`cmd /c echo …>>file`) and ran `codex exec` — **the hook never executed**
   (no file, twice, two different hook payloads including a plain logger).
2. Conclusion: `codex exec` (0.152.1) does **not run hooks.json hooks at all**
   in exec mode. "hook: Stop Completed" in Codex's own output is status text,
   not hook execution. So there is no "new payload shape" to adapt to in exec
   mode — there is no payload. Hooks remain the interactive-session path
   (Codex may require one-time trust via `/hooks`).
3. `~/.codex/hooks.json` was restored to its exact pre-experiment content
   afterwards (pigeonhub agent-event codex entries intact).

## The official exec-mode path: `codex exec --json` bridge

`connectors/agent_bridge_codex.py` wraps `codex exec --json` and maps the
**public** JSONL stream to the adapter. Captured on 0.152.1 (shapes only,
contents redacted in fixtures):

| Event | Public fields | Bridge → adapter |
|---|---|---|
| `thread.started` | `type`, `thread_id` | RUNNING (job id `agent-codex-<thread>`) |
| `turn.started` | `type` | ignored |
| `item.completed` | `type`, `item{id,text,type}` | PROGRESS (turn counter) |
| `turn.completed` | `type`, `usage{…token counters}` | PROGRESS (turn counter) |
| `error` | `type` | FAILED, bridge exits 3 |
| (no terminal event) | — | bridge publishes `stop` from process exit code → DONE/FAILED |

## Hardening (this campaign)

- The bridge's stream handling was refactored into
  `lifecycle_from_line(line) -> dict | None`: it extracts **only** the event
  type (plus `thread_id`) and drops `item` payloads (assistant text) and
  `usage` counters at the parse boundary — the allowlist privacy boundary is
  now structural, not a matter of downstream discipline.
- Unknown/future event types are ignored, never guessed.
- `tests/test_pigeonhub_codex_compat.py` pins the 0.152.1 shapes as sanitized
  fixtures (assistant text replaced with placeholders): stream → lifecycle
  mapping, noise tolerance, unknown-event tolerance, adapter normalization
  (RUNNING/PROGRESS/DONE/FAILED), and the interactive hook path mapping.

## Real E2E (this campaign)

- `pigeonhub recipe run codex-research` (recipe → bridge → real Codex):
  `RUNNING OK`, `PROGRESS 1`, `PROGRESS 2`, `DONE OK`, exit 0; the bridge's
  native events converged on the **single** recipe job card
  (`agent-state.json` holds one `recipe-…` entry, state DONE, name
  "Codex research" preserved). Two cards would have been a FAIL.
- Residual limitation (documented in KNOWN-REGRESSIONS): the bridge is a repo
  script, so the packaged exe cannot launch it by path; installer users get a
  normal wrapper card for Codex sessions instead.
