# GOAL — MVP-011.5 → MVP-015 Pre-Deployment Campaign (overnight 2026-09-14/15)

Branch: `autonomous/mvp0115-mvp015-predeploy-overnight-20260914` (from `b88b335`, the
MVP-011 VERIFIED commit). `main` untouched.

Mission: on top of the VERIFIED MVP-001…011 base, take PigeonHub to PRE-DEPLOYMENT
READY by implementing and verifying, in order:

| # | MVP | Theme |
|---|-----|-------|
| 011.5 | Inbox Correctness & Message Management | live relative time, durable delete (tombstones), delete-all |
| 012 | Real ComfyUI Integration | real workflow → RUNNING/PROGRESS/DONE/FAILED through the existing job pipeline |
| 013 | QR Pairing | one-time short-lived code rendered as QR; server semantics (expiry/replay/revoke) |
| 014 | Real Agent Long-session E2E | real agent (Claude Code / Codex) lifecycle → Android card |
| 015 | Connection Health & Reliability UX | per-integration health, stale detection, recovery actions |

North star: **Start it. Walk away. PigeonHub tells you when it matters.**
Every feature must let the user leave the computer while long jobs run.

Rules: no main merge, no force push, no production destructive deletes, no secret
commits, no big dependency upgrades, no FCM-pipeline rewrite, no GitHub-connector
delivery rewrite. Human-dependent steps become `DEFERRED_OWNER_ACTION` with exact
owner steps, and independent work continues. Statuses: NOT_STARTED / IMPLEMENTED /
VERIFIED / BLOCKED / DEFERRED_OWNER_ACTION — IMPLEMENTED and VERIFIED are never mixed up.
