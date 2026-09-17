# Agent Contract v1

## Normalized payload

The adapter publishes the existing PigeonHub envelope with this allowlisted
`job` shape:

```json
{
  "source": "codex|claude|grok|zcode",
  "job_id": "agent-codex-<stable-session-id>",
  "job_name": "safe user-provided setup name",
  "state": "RUNNING|PROGRESS|DONE|FAILED|NEEDS_ACTION",
  "started_at": "RFC3339 timestamp",
  "finished_at": "RFC3339 timestamp or omitted",
  "attention_reason": "short safe reason or omitted",
  "result_summary": "short safe summary or omitted",
  "deep_link": "https://host/path or omitted"
}
```

`deep_link` drops query strings and fragments. Names, reasons, and summaries
are whitespace-normalized and truncated. The adapter does not copy a vendor
message into a summary.

## Mapping table

| Vendor event | Normalized state | Rule |
| --- | --- | --- |
| `SessionStart`, `thread.started` | `RUNNING` | Stable session id anchors the job |
| `PostToolUse`, `item.completed`, `turn.completed` | `PROGRESS` | Count/metadata only |
| `PostToolUseFailure` | `PROGRESS` | Agent may recover; never direct `FAILED` |
| `PermissionRequest` | `NEEDS_ACTION` | Official event means a user-facing prompt |
| `Notification` | `NEEDS_ACTION` | Only explicit waiting/approval/input reason |
| `Stop` | `DONE` | Unless an explicit terminal failure signal exists |
| `StopFailure`, fatal error, non-zero exit | `FAILED` | Real session termination failure |

Unknown events are ignored. Hook execution is fail-open so a PigeonHub network
or login problem cannot interrupt the vendor agent.

## Hook command

The setup tool installs `pigeonhub agent-event <agent>` as the hook process.
That command reads one JSON event from stdin and publishes only the normalized
allowlist. Users do not need to copy hook JSON by hand.
