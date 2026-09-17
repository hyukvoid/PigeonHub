# GOAL — MVP-017.5 + MVP-018 Overnight Campaign (2026-09-18)

Branch: `autonomous/mvp0175-mvp018-zcode-overnight-20260918` (from `36e2cd5`).

## The two goals

**A. MVP-017.5 — Production validation of the MVP-017 AI agent integrations.**
Real lifecycle evidence (agent → hook → worker → Android), synthetic evidence
kept separate.

**B. MVP-018 — Windows distribution of the pigeonhub CLI.**
"개발자 PC에서만 돌아가는 CLI" → "설치 가능한 PigeonHub Windows Release
Candidate": download → install → terminal reopen → `pigeonhub works` → login QR
→ first job.

## Non-goals (explicitly banned)

- No background daemon, tray app, startup process, auto-updater, tool detector,
  desktop dashboard, Windows service, process monitor, or remote PC control.
- No new agent-specific FCM pipelines; everything rides the common Job Model →
  coalescing → attention policy → FCM pipeline.
- No new-language CLI rewrite; packaging only.

## Success definition

Not "every MVP marked VERIFIED". Success is: PigeonHub moves one step from
in-repo dev tool to installable Windows product, and every remaining
unverified item is precisely named with evidence. Environment-impossible items
are honestly `DEFERRED_OWNER_ACTION`.

## Ground rules

- `main` untouched; `ui.xml` unstaged user artifact preserved (UIAutomator
  dump, verified non-source).
- No secret values read, printed, or stored in reports; existence checks only.
- Checkpoint commits per logical unit (`mvp0175: …`, `mvp018: …`).
- Final gates: Python tests, worker typecheck, Android unit tests +
  assembleDebug/Release, `git diff --check`, secret scan.
