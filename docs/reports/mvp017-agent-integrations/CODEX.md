# OpenAI Codex Integration

## Official surface audited

OpenAI’s official Codex Hooks documentation defines user-level hook sources at
`~/.codex/hooks.json` or inline `config.toml`, supports lifecycle events such as
`SessionStart`, `PermissionRequest`, `PostToolUse`, `PostToolUseFailure`, and
`Stop`, and requires trust review for non-managed hooks:
<https://learn.chatgpt.com/docs/hooks>

## PigeonHub implementation

- Existing real bridge `connectors/agent_bridge_codex.py` is reused, not
  replaced by a synthetic runner.
- Public `codex exec --json` events map to the common adapter.
- Agent response text is no longer sent as a result summary; only a count of
  lifecycle updates is retained.
- `pigeonhub setup codex` targets `%USERPROFILE%/.codex/hooks.json` and adds
  user-level handlers for start, permission, tool progress/recovery, and stop.
- The existing `codex` executable is detected before setup; unsupported or
  malformed hook JSON is rejected without mutation.

## Expected real lifecycle

`thread.started → RUNNING → item/turn progress → exit 0 → DONE`.
An actual terminal error or non-zero process exit becomes `FAILED`. A Codex
permission hook becomes `NEEDS_ACTION` only when Codex invokes the official
permission event.

## User action

```text
pigeonhub setup codex
```

Review the preview, confirm once, then review/trust the new hook in Codex with
`/hooks` when Codex asks for trust.
