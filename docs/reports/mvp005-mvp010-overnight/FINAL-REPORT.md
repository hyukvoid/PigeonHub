# MVP-005→010 Overnight Campaign — FINAL REPORT

Date: 2026-09-14 (overnight)
Branch: `autonomous/mvp005-mvp010-overnight-20260914` (main untouched)

## Scoreboard

| MVP | Status | Implementation | Tests | Real E2E | Commit | Blocker |
|-----|--------|----------------|-------|----------|--------|---------|
| 005 Structured Job Model | **VERIFIED** | worker schema_008 (additive job_* columns) + jobs.ts validator + publish/FCM/sync carry job fields + GitHub webhook mapping; Android Room v5 + payload/sync parse + JobAttentionPolicy | 27/27 unit tests; worker tsc | OLD_MESSAGE_COMPAT, GITHUB_JOB_MAPPING (run 34770449543, D1 ×3 fan-out channels), STATE_TRANSITION (RUNNING→PROGRESS→DONE one card), DEDUPE (dup DONE suppressed) — all on device+prod worker | e55a783, edab9ec | — |
| 006 Job Inbox UX | **VERIFIED** | collapseInboxItems (pure grouping) + JobCard (state icon/color hierarchy, progress n/m·%, result/attention, elapsed, deep link) KO/EN | InboxItemsTest | Device walk: EN/KO, Light/Dark, font 200% — no clipping; mixed job+unstructured list renders | 74b4078-era→e41eac8 (see commits) | — |
| 007 ComfyUI Connector POC | **VERIFIED** (pipeline) / ComfyUI-app integration **DEFERRED** | pairing protocol (10-min one-time revocable codes, hashed; connector tokens hashed — never rotates device tokens) + phone UI (Connect → code dialog, copy/revoke) + stdlib-only PC connector with ComfyUI *contract simulator* | real redeem + publish E2E | Phone-issued code redeemed by PC connector; RUNNING/PROGRESS/DONE streamed → FCM → Job Cards on device | f0b5772 | ComfyUI not installed on this machine (app-level integration deferred, explicitly recorded) |
| 008 Python/CLI Connector | **VERIFIED** | `pigeonhub_connector.py run/progress/attention` (process wrapper: start→RUNNING, exit 0→DONE, nonzero→FAILED) | real local jobs | crawler_ok.py → DONE card; crawler_fail.py → FAILED card (HTTP 429 attention line); long-running shell → PROGRESS 300/1000 → NEEDS_ACTION push — all on device | f6519ce | — |
| 009 AI Agent Connector | **VERIFIED** (connector contract) | connectors/agent_hook.py (SessionStart→RUNNING, Notification→NEEDS_ACTION first-class, Stop→DONE, failure→FAILED); AiAgentCard how-to with copyable hook commands | real lifecycle publishes | REAL `claude -p` session fired UserPromptSubmit hook → agent RUNNING event landed in D1; full start/attention/done lifecycle delivered via worker+FCM manually | 09acc9b | claude account quota (402) aborted the model call mid-session, so the automated Stop-hook firing wasn't captured (contract verified manually) |
| 010 Attention Policy v1 | **VERIFIED** | JobAttentionPolicy (RUNNING/PROGRESS inbox-only; DONE normal push; FAILED/NEEDS_ACTION high push; per-(job,state) memory persists across restarts; re-run re-arms) + JobAlertPrefs (3 toggles, defaults on) + Settings card | JobAttentionPolicyTest | Spam test: same DONE ×3 + process restart → suppressed each time; toggle OFF → "alert disabled by user"; ON → delivered | 4473f6a | — |

## Final gates

- COMPILE: worker `tsc --noEmit` clean; Android `assembleDebug` clean
- UNIT TEST: Android 30/30 green (validator, job payload, policy, grouping)
- BUILD: final `assembleDebug` PASS on clean tree
- ANDROID TEST: `testDebugUnitTest` PASS
- MY_PUSH REGRESSION: in-app test send → `delivered push … priority=HIGH` (19:09:58)
- GITHUB REGRESSION: real `workflow_dispatch` (run 34776882129) → webhook → `gh-run-…-ch_5d0a…` delivered
- JOB MODEL: same source+job_id collapses; unstructured compat proven
- ARTEMIS: real touch/scroll/navigation across Inbox/Connections/Diagnostics/Settings, KO/EN, Light/Dark, 200%
- `git diff --check`: clean (EOF whitespace fixed)
- secret scan: no invite/pairing-token/credential material committed (pairing codes are one-time and already burned; connector token lives only in local `~/.pigeonhub/credentials.json`, gitignored path outside repo)

## Bugs found by the campaign's own QA (fixed)

1. **P0** `NetworkOnMainThreadException` in the first pairing UI (fixed: IO dispatcher).
2. **P0** Main-thread Room access from the new local job-test button (fixed: IO wrapper).
3. **P1** CLI connector `run` subcommand never dispatched (positional arg shadowed `args.cmd`).
4. **P2** Terminal-state publish lost to per-minute quota 429 → connector now retries DONE/FAILED/NEEDS_ACTION with backoff.
5. **P2** GitHub webhook duplicated the summary line on the Job Card → summary dropped.

## Not done (honest list)

- Real ComfyUI application integration (DEFERRED — not installed; contract+simulator verified instead).
- QR rendering of pairing codes (code text only tonight; short-lived one-time property carries the security).
- Live process-restart test of agent Stop-hook inside a real long claude session (quota-blocked).
- Job progress coalescing server-side (client collapse covers UX; server still stores each event — future work to save D1 writes).

## What became meaningfully better for the user? (≤5 lines)

A long task you start and walk away from now shows up as ONE living card — Running with
real progress, then Done, Failed, or Needs-action — instead of a pile of anonymous pushes.
Failure and "waiting on you" moments reach the lock screen instantly; silent progress never
buzzes. Connecting a new machine is a 10-minute one-time code, not tokens and endpoints.
And none of it cost the old guarantees: plain pushes, GitHub alerts and multi-device
delivery still pass the same tests as before.
