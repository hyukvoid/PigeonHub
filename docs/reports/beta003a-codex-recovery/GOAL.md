# GOAL — BETA-003A Codex Zero-Command Auto-Connect + Recovery Hardening

Campaign: BETA-003A · 2026-09-20 · branch `autonomous/beta003a-codex-recovery-20260920`
(base: BETA-003 `ee6308f`).

## The one-sentence goal

After tonight, a Windows user installs PigeonHub, pairs their phone once, and
Codex/ZCode completion notifications reach their Galaxy — with **zero
commands typed, zero config files opened, and no re-pairing after a reboot**.

```
Install → Setup Center → Galaxy QR → Codex detected → Codex auto-connected
→ use Codex normally → walk away → Galaxy notification
… next day: PC boot → no re-pairing → Codex still connected → notifications work
```

## Non-goals (explicit)

No new agents, remote PC control, desktop daemon, tray, scheduler, recipe
sync, marketplace, web dashboard, analytics, cloud account redesign, new job
states, or new notification categories. This is a hardening + connection
completeness campaign on the BETA-003 foundation.

## What was allowed to change

- Codex integration: the upstream-supported `notify` config route only.
- Setup Center: state accuracy + returning-user fast path.
- Android: the PC connection card's state machine.
- Tests/QA/docs.

## What was never allowed

Trust bypass, trust-DB edits, binary patching, prompt/assistant/cwd
transmission, fake lifecycle states, double job cards, or a failed Codex
setup breaking phone pairing.
