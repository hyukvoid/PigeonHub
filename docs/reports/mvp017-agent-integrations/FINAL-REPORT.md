# MVP-017 Final Report

## Result

**IMPLEMENTED — Codex/Claude integration paths and the common four-agent
engine are implemented; Grok/ZCode real-agent E2E is deferred to owner action
because those tools are not installed on this PC.**

## What changed

- Added the allowlist normalizer and fail-open `pigeonhub agent-event` hook
  entry point.
- Added safe, preview-first setup/remove for Codex, Claude Code, Grok Build,
  and ZCode · GLM with backups, atomic writes, format refusal, verification,
  and idempotency.
- Reused the real Codex JSONL bridge and removed raw assistant text from
  published summaries.
- Replaced the Android primary AI grid with exactly the four requested cards;
  Custom Agent remains only in Advanced.
- Added MVP-017 contract, privacy, vendor, architecture, and evidence docs.

## Verification

Focused Python suite: **10 passed**. Existing MVP-016 CLI/coalescing suite:
**passed**. Android `:app:assembleDebug` and `:app:testDebugUnitTest` both
finished with `BUILD SUCCESSFUL`; only pre-existing Kotlin deprecation
warnings remain. `git diff --check` passed and the scoped secret scan found no
credential-shaped values.

## Remaining owner actions

1. Apply `worker/schema_013.sql` in the intended production environment before
   relying on PC-first login in production.
2. Run `pigeonhub setup claude` and a real Claude task after quota is restored.
3. Install/authenticate Grok Build and ZCode, run setup with confirmation, and
   capture real start/attention/done/failure → worker → Android evidence.
