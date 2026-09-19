# CODEX PRIVACY BOUNDARY — BETA-003A

## The rule

Codex's notify payload contains private material (measured on this machine:
`input-messages`, `last-assistant-message`, `cwd`). Those fields are
**structurally dropped at the parser boundary** — there is no
store-then-redact step anywhere in the pipeline.

```
RAW EVENT (may contain prompt / assistant text / cwd / usage)
   ↓  json.loads
LOCAL PARSER  (codex_integration.handle_codex_notify)
   ↓  allowlist: type, thread-id, turn-id — nothing else is read
NORMALIZED EVENT (opaque ids + fixed safe strings only)
   ↓  publish_job_detailed(source=codex, state=DONE, …)
WORKER → Android
```

## What crosses the boundary

| Field | Value |
| --- | --- |
| `job.source` | `codex` |
| `job.job_id` | `agent-codex-<sanitized thread>-<sanitized turn>` (ids sanitized to `[A-Za-z0-9_.:-]`, ≤96 chars each, ≤120 total) |
| `job.state` | `DONE` |
| `title` | `Codex: done` (fixed) |
| `message` | `Codex turn completed` (fixed) |
| `job_name` | `Codex session` (fixed) |
| `finished_at` | local timestamp |

Recipe runs additionally carry the recipe's own display name — the label the
user chose locally, already BETA-001B-approved. Card titles never contain
the raw prompt.

## Unique marker proof

Fixture payloads carry `PRIVATE_CODEX_PROMPT_MARKER_003A` in
`input-messages`, `last-assistant-message`, and `cwd`. The test suite
asserts the marker appears in **none** of: published kwargs, the serialized
publish calls, the packaged E2E's captured worker request, or job names.
The wrapper path (`pigeonhub run codex …`) separately proves the Codex argv
never becomes a card title or message.

## Fail-closed negative matrix (automated)

malformed JSON · non-dict JSON · oversized (>128 KiB, dropped wholesale) ·
unknown event type · missing thread/turn ids · hostile identifier text
(path traversal / shell metacharacters sanitized out of job ids) · failed
publish keeps replay possible · replay after success deduped.

Every failure mode exits 0 without publishing; nothing about the failure —
including exception text, which could carry payload fragments — is printed
or stored.
