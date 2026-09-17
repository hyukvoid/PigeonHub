# ZCode · GLM Integration

## Official surface audited

ZCode documents a local subprocess hook protocol and the events
`SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PermissionRequest`,
`PostToolUse`, `PostToolUseFailure`, and `Stop`:
<https://zcode.z.ai/en/docs/hooks>

The same documentation states that user hooks run from
`%USERPROFILE%/.zcode/cli/config.json` when `hooks.enabled` is true, while
project-level `.zcode` hook configuration is ignored. Plugins are an alternate
supported distribution mechanism.

## PigeonHub implementation

`pigeonhub setup zcode` uses the supported user-level config and a direct
`process` handler (`pigeonhub agent-event zcode`). It enables hooks only after
confirmation, preserves the prior enabled flag in setup metadata, and restores
that flag on removal. It never writes only a repository `.zcode` hook and never
calls that a successful integration.

`PermissionRequest` maps to `NEEDS_ACTION`; `Stop` maps to `DONE`; a
`PostToolUseFailure` remains recoverable progress.

## Detection and E2E status

No `zcode` executable was found on the development PC. A generic ZCode user
config file exists, but it does not expose enabled lifecycle events. A real
ZCode task and official hook-to-worker-to-Android proof therefore remain
`DEFERRED_OWNER_ACTION`. The implementation must be rerun with a real ZCode
desktop installation before the card can be called operational.
