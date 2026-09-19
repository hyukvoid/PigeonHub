# CODEX CAPABILITY AUDIT — BETA-003A

Audited on the real machine. Only presence/version/shape facts were recorded
(never user content).

## Installed Codex

| Item | Measured value |
| --- | --- |
| Executable | `C:\Users\user\AppData\Local\Programs\OpenAI\Codex\bin\codex.exe` (on PATH) |
| Version | `codex-cli 0.152.1` |
| Codex home | `~/.codex` exists; `config.toml` exists (176 lines) |
| `hooks.json` | exists; contains 5 PigeonHub-managed handlers (mvp0175 era, user-trusted) |

## Upstream capability matrix (what 0.152.1 actually offers PigeonHub)

| Capability | Mechanism | Trust required? | Verified? |
| --- | --- | --- | --- |
| COMPLETION_ONLY | top-level `notify = [<argv>]` in `config.toml` — one program invoked per `agent-turn-complete` | **No** (plain user config) | YES — real payload captured on this machine (`tools/agent-e2e-sandbox/notify_probe.log`): argv[1] is a JSON object with `type:"agent-turn-complete"`, `thread-id`, `turn-id`, `client:"codex_exec"`, plus `cwd`, `input-messages`, `last-assistant-message` |
| FULL_LIFECYCLE | `hooks.json` SessionStart/PermissionRequest/PostToolUse/PostToolUseFailure/Stop | **Yes — Codex's own in-app review/trust flow** (never bypassed) | YES — live on this machine since mvp0175 (real RUNNING→DONE sessions); exec mode ignores hooks.json (BETA-002 root cause) |
| Exec bridge | `pigeonhub run codex exec --json …` wrapper parses JSONL | No (PigeonHub-side only) | YES — BETA-002 fixtures + `tests/test_pigeonhub_codex_compat.py` (5 tests, green) |

## The single-slot reality (this machine's critical finding)

`notify` is **one program slot**. On the owner's machine it is occupied by
Codex Desktop's own computer-use bridge:

```
notify = ["…\OpenAI\Codex\runtimes\cua_node\…\codex-computer-use.exe", "turn-ended"]
```

Therefore on THIS machine the notify slot is **not installable** — PigeonHub
preserves it and reports an honest NEEDS_ATTENTION for the notify path. The
Codex→Galaxy path on this machine continues to work through the trusted
hooks (interactive) and the wrapper (recipes/`pigeonhub run`). Fresh beta
users typically have no `notify` key at all, and the packaged E2E proves the
auto-connect installs cleanly there (see FRESH-USER-QA.md).

Classification per the campaign vocabulary:

- Codex on this machine: **CONNECTED (hooks source)** — Setup Center shows
  `연결됨 ✓ (hooks)`.
- Notify baseline on this machine: **NEEDS_ATTENTION (existing notify
  preserved)** — never overwritten.
- Notify baseline on a fresh Codex install: **CONNECTED_BASIC** — proven with
  the packaged exe end to end.

## What PigeonHub deliberately does NOT do

- No `/hooks` prerequisite (the command is not part of the user journey).
- No trust edits, no `--dangerously*` flags, no metadata tampering.
- No chaining into Codex's internal `notify` argv (it points into a
  versioned runtime directory that Codex rewrites on updates).
