# MVP-017 Privacy Contract

## Sent allowlist

- agent type (`codex`, `claude`, `grok`, `zcode`)
- stable job/session id
- safe job name
- lifecycle state
- start and finish timestamps
- integer progress when available
- generic attention reason when actually waiting
- short safe completion/failure summary
- sanitized HTTPS path deep link, without query or fragment

## Never sent

- user prompts and full conversations
- transcripts or last assistant messages
- source files or patches
- full tool input/output and raw terminal logs
- environment variables, working-directory dumps, API keys, tokens, cookies
- vendor configuration files
- raw hook JSON

The normalizer is allowlist-based rather than redaction-based. Unknown fields
are ignored, not forwarded. Local state contains only the job id, safe name,
timestamps, progress count, and lifecycle state.
