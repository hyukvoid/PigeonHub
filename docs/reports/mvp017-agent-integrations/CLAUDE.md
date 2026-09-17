# Claude Code Integration

## PigeonHub implementation

`pigeonhub setup claude` writes only PigeonHub-managed hook entries into the
user-level Claude settings file `%USERPROFILE%/.claude/settings.json`. Existing
settings and third-party hooks remain untouched. Setup previews the change,
asks for confirmation, creates a backup, applies atomically, and verifies the
managed handler count.

Configured events are `SessionStart`, `PermissionRequest`, `Notification`,
`PostToolUse`, `PostToolUseFailure`, and `Stop`.

Claude `Notification` is not automatically attention. It becomes
`NEEDS_ACTION` only when the event explicitly means permission, approval,
elicitation, or user input. Tool failures remain recoverable `PROGRESS`.

## User action

```text
pigeonhub setup claude
```

Remove only PigeonHub entries with:

```text
pigeonhub setup claude --remove
```

## E2E status

Claude Code `2.1.88` is installed on the development PC. The previous campaign
recorded a real Claude quota `402 insufficient_quota` blocker, so a fresh
terminal Claude task is recorded as `E2E=DEFERRED_OWNER_ACTION` until quota is
available. The adapter/setup implementation and synthetic contract tests are
implemented; synthetic hook input is not claimed as real-agent E2E.
