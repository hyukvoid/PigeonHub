# MVP-005→010 — PROGRESS

- [x] MVP-005 Structured Job Model — VERIFIED (commit e55a783 + deploy 2a7ad2dc)
- [ ] MVP-006 Job Inbox UX
- [ ] MVP-007 ComfyUI Connector POC
- [ ] MVP-008 Python/CLI Connector
- [ ] MVP-009 AI Agent Connector
- [ ] MVP-010 Attention Policy v1
- [ ] FINAL-REPORT + RESUME.md

## Milestone log

| MVP | Changes | Tests | Result | Next |
|-----|---------|-------|--------|------|
| — | Branch created; GOAL.md written; env check (no ComfyUI installed → POC strategy; claude CLI present → real agent E2E possible; Python 3.13 OK) | — | SETUP | MVP-005 |
| 005 | Worker: schema_008 additive job_* columns (D1 applied), jobs.ts validator, publish/FCM/sync carry job fields, GitHub webhook maps workflow_run→job. Android: Room v5 migration, payload+sync parse, JobAttentionPolicy wired into PushPipeline, debug Job-event card. Deploys: worker 2a7ad2dc | 27/27 unit tests; REAL E2E on emulator+prod worker: RUNNING/PROGRESS inbox-only, DONE→notify, DONE-dup suppressed (DEDUPE), FAILED→notify, NEEDS_ACTION→notify, plain publish unaffected (OLD_COMPAT), real GitHub webhook mapped to job (D1: source=github job_id=run-34770449543 ×3 fan-out channels; FCM delivered), sync round-trip keeps job rows, 0 crashes | VERIFIED | MVP-006 |

## BLOCKED

(none)
