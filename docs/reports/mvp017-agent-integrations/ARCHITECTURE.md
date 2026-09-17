# MVP-017 Architecture

## Common path

```text
official agent hook
  → pigeonhub agent-event <agent>
  → allowlist normalizer
  → normalized Job Event
  → existing publish_job / Worker coalescing / attention policy / FCM
  → Android Job Inbox + Connection Health
```

The adapter boundary is `pigeonhub/agents.py`. It owns vendor event mapping,
safe local session metadata, setup plans, backups, and verification. The
existing `pigeonhub/core.py` remains the transport and Job Model boundary.

## Normalized event

Every published agent event uses the agent-specific `source` value (`codex`,
`claude`, `grok`, or `zcode`) and a stable `job_id`. The event may contain:

- `job_name`
- lifecycle `state`
- `started_at` / `finished_at`
- `attention_reason`
- safe `result_summary`
- sanitized `deep_link`
- optional integer progress

Local state stores only the job id, safe name, start time, progress count, and
last lifecycle state under the PigeonHub data directory. It never stores the
hook input.

## Failure and attention semantics

- `PostToolUseFailure` is `PROGRESS`/recovery information, not automatic
  `FAILED`.
- `PermissionRequest` is `NEEDS_ACTION` because official hook contracts fire
  it only for a real user-facing prompt.
- Generic `Notification` is ignored unless it explicitly indicates waiting,
  approval, permission, elicitation, or user input.
- `FAILED` requires a terminal failure signal, non-zero exit code, fatal event,
  `StopFailure`, or explicit `success: false`.
- `Stop` without a failure signal is `DONE`.

## Setup safety

Setup is an explicit user action. The CLI detects the binary/config, prints the
exact target and event list, explains the data minimization policy, asks
`Apply these changes? [Y/n]`, creates a timestamped backup for existing files,
writes atomically, and verifies the managed handler count. Re-running setup
removes and re-adds only the exact PigeonHub handler, so it is idempotent.

The setup targets are vendor-native:

- Codex: `%USERPROFILE%/.codex/hooks.json`
- Claude Code: `%USERPROFILE%/.claude/settings.json`
- Grok Build: `%USERPROFILE%/.grok/hooks/pigeonhub.json`
- ZCode: `%USERPROFILE%/.zcode/cli/config.json` user-level hooks

No project-level ZCode hook is written because the official ZCode runtime
ignores that scope.
