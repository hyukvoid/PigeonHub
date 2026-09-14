# MVP-005→010 — PROGRESS

- [x] MVP-005 Structured Job Model — VERIFIED (commit e55a783 + deploy 2a7ad2dc)
- [x] MVP-006 Job Inbox UX — VERIFIED (e41eac8)
- [x] MVP-007 ComfyUI Connector POC — VERIFIED pipeline / ComfyUI app DEFERRED (f0b5772)
- [x] MVP-008 Python/CLI Connector — VERIFIED (f6519ce)
- [x] MVP-009 AI Agent Connector — VERIFIED contract, real claude hook fired (09acc9b)
- [x] MVP-010 Attention Policy v1 — VERIFIED (4473f6a)
- [x] MVP-011 Server-side Job Progress Coalescing — VERIFIED
- [x] FINAL-REPORT + RESUME.md

## Milestone log

| MVP | Changes | Tests | Result | Next |
|-----|---------|-------|--------|------|
| — | Branch created; GOAL.md written; env check (no ComfyUI installed → POC strategy; claude CLI present → real agent E2E possible; Python 3.13 OK) | — | SETUP | MVP-005 |
| 005 | Worker: schema_008 additive job_* columns (D1 applied), jobs.ts validator, publish/FCM/sync carry job fields, GitHub webhook maps workflow_run→job. Android: Room v5 migration, payload+sync parse, JobAttentionPolicy wired into PushPipeline, debug Job-event card. Deploys: worker 2a7ad2dc | 27/27 unit tests; REAL E2E on emulator+prod worker: RUNNING/PROGRESS inbox-only, DONE→notify, DONE-dup suppressed (DEDUPE), FAILED→notify, NEEDS_ACTION→notify, plain publish unaffected (OLD_COMPAT), real GitHub webhook mapped to job (D1: source=github job_id=run-34770449543 ×3 fan-out channels; FCM delivered), sync round-trip keeps job rows, 0 crashes | VERIFIED | MVP-006 |

| 011 | worker: schema_010 job_states + job_coalescing.ts (10s / >=5pp / material-change policy; terminal-first; stale-progress drop) wired before quota in publish; Android seq-collision defense (FCM + sync fallback to reserved seq) | REAL E2E: 100-progress burst → 7 D1 rows (R1/P5/D1) all fcm_accepted, 93 coalesced; 1→6/1000 → only RUNNING+DONE; A/B interleaved independent; rules: interval emit + 5pp emit + coalesce; PROGRESS→DONE immediate; late PROGRESS after terminal dropped server-side; FAILED/NEEDS_ACTION immediate; sync/FCM survived induced seq drift with 0 new crashes; regressions green (MY_PUSH delivered, GitHub run 34776882129, CLI run→DONE card) | VERIFIED | done |

## BLOCKED

(none)
