# Grok Build Integration

## Official surface audited

The official SpaceXAI docs define personal hooks under
`%USERPROFILE%/.grok/hooks/*.json`, with project hooks requiring trust. They
support `SessionStart`, `Notification`, `PermissionDenied`, `PostToolUse`,
`PostToolUseFailure`, `Stop`, and `StopFailure` among other events:
<https://docs.x.ai/build/features/hooks>

Grok Build also provides `grok inspect` for checking discovered config, hooks,
plugins, and MCP sources:
<https://docs.x.ai/build/overview>

## PigeonHub implementation

`pigeonhub setup grok` writes a native user hook file at
`%USERPROFILE%/.grok/hooks/pigeonhub.json`; it does not assume Claude hook
semantics or use a project-only `.grok` file. The adapter listens for native
Grok lifecycle events and keeps `PostToolUseFailure` recoverable.

## Detection and E2E status

The development PC has no `grok` executable on PATH. Therefore this task does
not claim a Grok real session, real attention event, or Android delivery. The
scoreboard records `TOOL_DETECTED=DEFERRED_OWNER_ACTION` and leaves the setup
path ready for a later owner-installed Grok Build run.
