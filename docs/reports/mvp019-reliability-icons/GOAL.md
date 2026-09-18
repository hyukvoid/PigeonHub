# GOAL — MVP-019 Beta Reliability Hardening + AI Agent Icon Accuracy (2026-09-18)

Branch: `autonomous/mvp019-reliability-icon-hardening-20260918`
(from `e40a202`, the MVP-017.5+018 overnight end state).

Not a feature campaign. Two goals:

1. **Reliability hardening** before Private Beta.
2. **AI Agent icon accuracy** — cards must be instantly recognizable.

## Priority order (fixed)

- **P0** — worker stored-but-502 duplicate rows; stale credential / dead
  channel UX.
- **P1** — stale RUNNING job policy (design first, no new states unless
  unavoidable); AI agent icon replacement + card visual consistency.
- **P2** — copy/label polish (Supported/Set up/Connected truthfulness, "Last
  event" health semantics).

## Non-goals

No new agents, no new creative tools, no daemon/tray/remote control, no
analytics dashboard, no color-play, no unrelated refactors.

## Key design principle (P0)

Durability and delivery are different outcomes. Once a message is in D1, the
transport response must say "stored" — delivery results ride in the body
(`push_status`, `delivery.retryable`), never as a 5xx that teaches clients to
re-publish the same event.

## Success definition

Beta users hit fewer confusing failures: stored events are never re-sent as
duplicates, dead pairings tell the user exactly what to do, no job is abandoned
as RUNNING forever, and the four agent cards are identifiable at a glance in
light/dark, KO/EN, font 200%.
