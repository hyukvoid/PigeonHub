# AGENT-VALIDATION — MVP-017.5

Per-agent gates. VERIFIED = real evidence captured this campaign.
IMPLEMENTED = code path exists + non-real verification (unit/synthetic-boundary).
Real-agent evidence and synthetic evidence are labeled separately per row.

Legend: V = VERIFIED, I = IMPLEMENTED, D = DEFERRED_OWNER_ACTION, F = FAIL, — = not applicable by design.

## ZCode · GLM

| Gate | Status | Evidence |
|---|---|---|
| SETUP_PREVIEW | V | `pigeonhub setup zcode` preview printed (target `~/.zcode/cli/config.json`, events listed) |
| CONFIRM | V | `--yes` required; applied only after explicit confirm |
| APPLY | V | Backup `.pigeonhub.bak.20260917155324Z` created; atomic write verified; plugins key preserved |
| IDEMPOTENT_REAPPLY | V | Second apply: "Nothing to change; setup is already in the requested state." |
| REMOVE | V | 5 managed hooks removed; `plugins.enabledPlugins` preserved; `hooks.enabled` handling intact |
| REINSTALL | V | Clean re-apply, 1 handler per event, strict-schema fields only |
| REAL_START | V* | SessionStart payload via real hook entry `pigeonhub agent-event zcode` → RUNNING stored + `fcm_accepted` |
| REAL_DONE | V* | Stop payload → DONE stored + `fcm_accepted` + Android card "Done" |
| REAL_FAILED | — | ZCode's official hook contract has no terminal-failure event (7 events only; PostToolUseFailure is recoverable PROGRESS by design) |
| REAL_NEEDS_ACTION | V* | PermissionRequest payload → NEEDS_ACTION; system shade showed "Agent is waiting for your input" |
| ANDROID_CARD | V | `evidence/zcode-jobcard-done.png` — "Zcode · Zcode session", Done, 4 updates, just now |
| SYSTEM_PUSH | V | `evidence/zcode-push-shade.png` — DONE + NEEDS_ACTION notifications in shade; D1 `push_status=fcm_accepted` ×4 |
| HEALTH | I | Health rides the MVP-015 per-source health pipeline (source=zcode), not re-verified this campaign |

`V*` = the hook entry point, normalizer, worker publish, FCM, and Android legs
are all real production legs; the only synthetic element is the vendor payload
fed to the hook, because **no standalone ZCode CLI session could run on this
machine overnight** (see blocker below). This is stronger than unit-level
synthetic but is not a full "human typed a prompt in ZCode" session; the gap is
named, not papered over.

### Bugs found and fixed (real E2E value)

1. **Invalid hook schema field (fixed, regression-tested).** `pigeonhub setup
   zcode` wrote `{"type":"process","command":"pigeonhub","args":[…],"enabled":true,"timeoutMs":10000}`.
   ZCode validates hook entries with a **strict** schema that allows only
   `type/command/args/timeoutMs` on a process hook; the extra `enabled` key gets
   the hook dropped. Fixed in `pigeonhub/agents.py`; regression test
   `test_zcode_handler_matches_strict_process_hook_schema` asserts the exact
   field set. Old configs written by MVP-017 are still recognized and cleaned by
   `setup --remove` (managed-hook detection only checks type/command/args).
2. **Root-caused "hooks never fired" for pre-existing sessions.** ZCode reads
   hook config once per process start; sessions started before `setup` never see
   the hooks (verified: 0 hook records across all desktop processes started
   before setup; trivial valid hooks also don't retro-fire). Setup must be
   followed by a NEW ZCode session. Documented in INSTALLER-QA/SECURITY notes.

### ZCode real-session blocker (owner action)

- The standalone ZCode CLI (`zcode.cjs -p`, v0.16.5) fails at
  `turnPhase: model_creation` — the desktop app injects the model adapter per
  session; the standalone CLI has never been logged in on this machine.
  Reproduced 4×; `zcode login` requires interactive Z.AI OAuth (browser).
- **Owner action (1 step):** run `zcode login` in any terminal, complete the
  Z.AI OAuth in the browser. After that, `zcode -p "…"` sessions fire the
  PigeonHub hooks for real. Alternatively, restarting the ZCode desktop app
  (or opening a new chat) loads the hooks for desktop sessions immediately —
  no login needed; the hooks were already validated against the exact payloads
  the desktop runner emits (`hookEventName`, `sessionId`, `mode`, `toolName`,
  `toolInput`, `toolResponse`, `responseText`, …).

## Codex

| Gate | Status | Evidence |
|---|---|---|
| SETUP_PREVIEW | V | Preview named `~/.codex/hooks.json` + events; codex 0.152.1 detected |
| CONFIRM | V | `--yes` gate enforced |
| APPLY | V | File created (no backup needed for new file), atomic write |
| IDEMPOTENT_REAPPLY | V | "Nothing to change" after converged apply; user hooks preserved on re-apply |
| REMOVE | V | Remove + backup created; reinstall restored exactly the managed set |
| REINSTALL | V | Verified after an interleaved debug hook was manually removed |
| REAL_START | V | **Real** `codex exec` session (trust-bypassed): SessionStart hook → RUNNING published, `fcm_accepted` (D1 16:44:49Z, job `agent-codex-01a0b041-…`) |
| REAL_DONE | V | Stop hook of the same real session → DONE, `fcm_accepted` (16:44:53Z); Android card "Codex · Codex session — Done, 2 updates" |
| REAL_FAILED | — | Codex hook contract has no terminal-failure event; FAILED path remains covered by unit tests |
| REAL_NEEDS_ACTION | I | PermissionRequest hook registered; not reproducible headlessly (no interactive permission prompt in `exec`) |
| ANDROID_CARD | V | `evidence/codex-zcode-inbox.png` |
| SYSTEM_PUSH | V | RUNNING+DONE both `push_status=fcm_accepted` to the live device channel |
| HEALTH | I | Rides MVP-015 per-source health (source=codex) |

### Codex findings

1. **Codex requires one-time hook trust.** Hooks in `~/.codex/hooks.json` do
   not run until the user trusts them via `/hooks` in the Codex TUI (or a
   per-invocation `--dangerously-bypass-hook-trust`). Real sessions without
   trust show "hook: Stop" lines but never execute the handlers — this silently
   explains any "setup succeeded but nothing arrived" report. Owner one-time
   action: run `/hooks` in Codex and approve the PigeonHub handlers. The E2E
   above used the documented per-invocation bypass flag; no trust state was
   modified.
2. **Payload privacy verified against the real wire format.** Captured a real
   Stop payload: `{cwd, hook_event_name, last_assistant_message, model,
   permission_mode, session_id, stop_hook_active, transcript_path, turn_id}`.
   `last_assistant_message` and `transcript_path` are present on the wire and
   confirmed NOT forwarded by the normalizer (D1 `result_summary` stayed
   "Agent session completed").
3. Codex passes the payload on stdin, Claude-compatible field names
   (`hook_event_name`, `session_id`) — matches the MVP-017 adapter assumptions.


## Claude Code

| Gate | Status | Evidence |
|---|---|---|
| (filled below after setup preview / task attempt) | | |

## Grok Build

| Gate | Status | Evidence |
|---|---|---|
| (filled below) | | |
