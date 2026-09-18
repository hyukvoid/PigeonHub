# GOAL — BETA-001B Local Recipes: Command + Optional Prompt → PigeonHub Job (2026-09-19)

Branch: `autonomous/beta001a-qr-pairing-locale-20260918` (continued; BETA-001A
landed first, its HEAD is the base — no fake/assumed 001A code).

Let a user save a command once and run it forever after with one line:

```
pigeonhub recipe add          # once: name, command, optional {prompt}/cwd
pigeonhub recipe run nightly-crawler
pigeonhub recipe run zcode-research --prompt "이번에는 Android 빌드 오류만 조사해줘"
```

PigeonHub stays a **Long-running Job Inbox**. Recipes are a thin LOCAL
convenience layer over the existing `pigeonhub run` lifecycle — not a new job
engine, not remote execution, not a scheduler.

## Scope (shipped)

- `pigeonhub recipe add|list|show|run|remove` (+ `--replace`, `--yes`,
  `--prompt`, `--prompt-file`, `--cwd`), interactive wizard when run bare.
- Local-only storage `%USERPROFILE%\.pigeonhub\recipes.v1.json` (atomic
  writes; corrupt file → clear error, never wiped).
- Single `{prompt}` placeholder, replaced inside argv tokens only — the
  prompt is always one argument value and can never start a second command.
- Recipe runs delegate to the existing run lifecycle (`run_job`), including
  MVP-018's Windows `.cmd`/`.bat` handling, RUNNING→DONE/FAILED semantics,
  child exit-code preservation, and `PIGEONHUB_JOB_ID` injection.
- One execution = one job card: official agents (codex/claude/grok/zcode)
  run under the same wrapper; their native hooks detect the inherited
  `PIGEONHUB_JOB_ID` and attach to that card instead of creating a second.
- Nothing about a recipe (command, argv, prompt, cwd) is ever sent to the
  worker: recipe jobs publish the display name, lifecycle state, and
  timestamps only.
- CLI version 0.18.0-beta.1 → 0.19.0-beta.1 (minor beta bump; single source
  `pigeonhub/__init__.py`).

## Non-goals (untouched by design)

Phone→PC run, remote prompt submission, scheduling/cron, daemons, tray,
marketplace/sync/sharing, multi-step workflows, variables beyond `{prompt}`,
secret vaults, desktop GUI. Worker/D1 and pairing protocol unchanged.
