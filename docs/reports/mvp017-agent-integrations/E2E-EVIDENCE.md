# MVP-017 Verification Evidence

## Environment audit

| Agent | Detection result | Version / note |
| --- | --- | --- |
| OpenAI Codex | `TOOL_DETECTED=PASS` | `codex-cli 0.152.1` |
| Claude Code | `TOOL_DETECTED=PASS` | `2.1.88` |
| Grok Build | `DEFERRED_OWNER_ACTION` | `grok` not found on PATH |
| ZCode · GLM | `DEFERRED_OWNER_ACTION` | `zcode` not found; generic config exists but no enabled hook events |

## Automated evidence

| Check | Result |
| --- | --- |
| Normalizer lifecycle and privacy tests | PASS — 10 tests in focused suite |
| PostToolUseFailure does not become FAILED | PASS |
| PermissionRequest attention mapping | PASS |
| Codex setup preview/apply/verify | PASS in isolated temp home |
| Setup idempotency / no duplicate managed hooks | PASS in isolated temp home |
| Setup remove preserves user hook | PASS in isolated temp home |
| ZCode enabled-flag restore | PASS in isolated temp home |
| Existing MVP-016 CLI/coalescing tests | PASS |
| Android `:app:assembleDebug` | PASS — `BUILD SUCCESSFUL` |
| Android `:app:testDebugUnitTest` | PASS — `BUILD SUCCESSFUL` |
| `git diff --check` | PASS — only line-ending normalization warnings |

## Real-agent status matrix

| Gate | Codex | Claude | Grok | ZCode |
| --- | --- | --- | --- | --- |
| `TOOL_DETECTED` | PASS | PASS | DEFERRED | DEFERRED |
| `SETUP_PREVIEW` | PASS | PASS | implemented/deferred | implemented/deferred |
| `USER_CONFIRMATION` | CLI implemented | CLI implemented | owner run | owner run |
| `SETUP_APPLIED` | temp-home verified | temp-home verified | deferred | deferred |
| `IDEMPOTENT` | PASS | covered by common engine | covered by common engine | covered by common engine |
| `REMOVE` / `REINSTALL` | common engine | common engine | owner run | owner run |
| `REAL_SESSION_START` → terminal | prior MVP-014 real E2E | DEFERRED quota | DEFERRED install | DEFERRED install |
| `RUNNING` / `NEEDS_ACTION` / `DONE` / `FAILED` | normalized + prior real bridge | normalized; real run deferred | normalized; real run deferred | normalized; real run deferred |
| Android card | PASS by source/UI audit | PASS by source/UI audit | PASS by source/UI audit | PASS by source/UI audit |
| system push / health / restart | existing pipeline reused | owner real run | owner real run | owner real run |
| `SECRET_SCAN` | PASS — no test secret/config payloads added | PASS | PASS | PASS |

Synthetic hook invocations are explicitly not counted as REAL_AGENT_E2E.
