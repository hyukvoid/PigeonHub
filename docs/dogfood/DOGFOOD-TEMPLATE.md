# Dogfood Journal — PigeonHub Private Beta

Start it. Walk away. PigeonHub tells you when it matters.

How to use: copy the template below into a dated file (`2026-09-20.md`) under
`docs/dogfood/journal/` (or keep it anywhere private — the journal is for you,
not for telemetry). One entry per real job, 20+ jobs over 5–7 days. Only run
real work; do not fabricate jobs to fill categories.

Privacy: record safe task labels only — never prompts, source code, keys,
private paths, or personal data.

The questions we are answering (not telemetry numbers):

1. Did I actually leave the PC while the job ran?
2. Did the notification save me a manual check?
3. Was any push useless (noise)?
4. Was any important push missing?
5. Did the Job Card state match reality?
6. Did I reach for Recipes again on repeat work?
7. Was setup/re-login friction noticeable?

Template:

```
## <date> — <safe task label, e.g. "Codex refactor">

Started with:            run / recipe / native agent
Expected duration:       <e.g. ~3 min>
Did I leave the PC?      YES / NO

Notification received:   DONE / FAILED / NEEDS_ACTION / NONE
Arrived while away:      YES / NO
Was it useful?           YES / NO
Did I check the PC manually before it arrived?   YES / NO

Card state matched reality:   YES / NO / (what actually happened)
Reused a Recipe?              YES / NO / N/A
Any confusion or friction:    <one line, or "none">
Would I run this via PigeonHub again?   YES / NO
```

Weekly wrap-up (after day 5–7) — answer the 7 questions with counts and the
single most annoying thing you hit. If a P0/P1 bug appears, file it in the
repo immediately; do not wait for the wrap-up.
