# MVP-005 → MVP-010 Overnight Campaign — GOAL

Date: 2026-09-14 (overnight)
Branch: `autonomous/mvp005-mvp010-overnight-20260914` (main untouched; no force push; no destructive D1 migration)

## North Star

> Start it. Walk away. PigeonHub tells you when it matters.

Transform PigeonHub from a generic push app into a **mobile Job Inbox for long-running jobs**:
every long task (AI agents, GitHub, ComfyUI, Python/data jobs, renders) is shown with one
unified state model — **RUNNING / DONE / FAILED / NEEDS_ACTION**.

## Milestones

| MVP | Focus | Gate words |
|-----|-------|-----------|
| 005 | Structured Job Model (optional layer over messages; GitHub as first structured source; same source+job_id collapses into one job) | OLD_MESSAGE_COMPAT, GITHUB_JOB_MAPPING, STATE_TRANSITION, DEDUPE |
| 006 | Job Inbox UX (Job Cards: state hierarchy, progress, elapsed; KO/EN, Light/Dark, 200%) | Artemis walk |
| 007 | ComfyUI Connector POC (pairing: short-lived/one-time/revocable; connector contract + simulator; real ComfyUI likely DEFERRED — not installed on this machine) | pairing E2E |
| 008 | Python/CLI Connector (`pigeonhub pair`, `pigeonhub run -- …`, progress API) with REAL local-job E2E → FCM → Android | real E2E |
| 009 | AI Agent Connector (instructions/hooks/wrapper for Claude Code/Codex/etc.; NEEDS_ACTION first-class; no vendor APIs) | ≥1 real agent E2E attempt |
| 010 | Attention Policy v1 (RUNNING/progress = inbox only; DONE = push; FAILED/NEEDS_ACTION = high push; dedupe; 3 settings toggles; no rule builder) | spam test |

## Cross-MVP architecture (contract)

```
Connector (GitHub webhook / CLI / agent hook / ComfyUI ext)
    ↓  normalized Job Event (source, job_id, state, progress, …)
Common Job Store (messages table + optional job columns; additive only)
    ↓
Common Delivery Policy (MVP-010)
    ↓
Existing FCM / durable Inbox / fan-out pipeline   ← NEVER rewritten
    ↓
Android (Job Card rendering; per-job state coalescing)
```

Connectors never build their own FCM logic or delivery rules.

## Evidence rules

- `IMPLEMENTED` = code exists and compiles, no real E2E.
- `VERIFIED` = real E2E passed on the emulator/backend chain in this session.
- `expected/should work/likely` are never PASS evidence.
- Blocked >20–30 min by environment → record BLOCKED, continue independent work, never end the campaign.
