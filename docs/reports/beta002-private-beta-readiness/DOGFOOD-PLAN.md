# DOGFOOD-PLAN — BETA-002

Purpose: set up and record the 5–7 day dogfood — not to claim it happened.
The dogfood answers product questions, not telemetry: no analytics backend,
no synthetic jobs, real work only.

## Setup

1. Journal: copy `docs/dogfood/DOGFOOD-TEMPLATE.md` per real job into dated
   files (private is fine). Privacy rules are in the template: safe task
   labels only — no prompts, code, keys, private paths, personal data.
2. Device pair: this PC + the registered phone (after the owner checklist in
   PRIVATE-BETA-CHECKLIST.md).
3. Duration: 5–7 calendar days, target **20+ real jobs**, no category
   padding. A natural failure is valuable; a manufactured one is noise.

## Suggested real-life mix (use what you actually do)

- Codex / ZCode sessions (native integration or bridge)
- Python scripts (crawlers, data jobs)
- npm/build commands (Windows `.cmd` shims)
- FFmpeg/render jobs
- Recipes for anything repeated twice

## Questions the journal answers (score at wrap-up)

1. Did I actually leave the PC while jobs ran?
2. Did notifications save manual PC checks?
3. Any useless pushes (noise)?
4. Any missing pushes (silence where something mattered)?
5. Did card states match reality (trust)?
6. Did I reach for Recipes on repeat work?
7. Any setup/re-login friction?

## Wrap-up

After day 5–7: counts per question + the single most annoying thing. File
P0/P1 bugs in the repo immediately when hit (reproduce → root cause → minimal
fix → regression test → commit); everything else waits for the wrap-up.
The wrap-up verdict gates the 3–5 person private beta.
