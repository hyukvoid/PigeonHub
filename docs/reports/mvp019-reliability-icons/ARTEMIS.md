# ARTEMIS / ANDROID-UI — MVP-019 device audit

Release APK (`:app:assembleRelease`) installed on emulator-5554
(`Medium_Phone_API_36.1`, channel `ch_d7c527c2…`). Structured walk per the
Artemis protocol: entry → sections → cards → CTA → cross-section consistency,
across the axes below. Evidence PNGs in `evidence/`.

## Scenarios

1. **Connections entry** — bottom nav "연결/Connections", header + jump chips
   render; no layout regressions from the header change.
2. **AI Agents section** — exactly four cards; all four brand tiles render:
   Codex (black blossom), Claude (ivory starburst), Grok (slash tile),
   ZCode (Z mark). Instantly distinguishable from each other and from the
   Creative cards.
3. **Card anatomy** — icon / title / one-line description / health line /
   status pill / right CTA all aligned; brand tile never overlaps text at
   font 200%.
4. **Health honesty** — Codex "8 hours ago" (real session), Claude
   "8 hours ago" (real hook event), ZCode "minutes ago" (real session),
   Grok **no health line** (never used). My Push keeps its real activity line.
5. **Detail screen** — Codex detail renders the brand tile in the header,
   setup steps + `pigeonhub setup codex` command intact.
6. **Inbox regression** — job cards render (real ZCode session card
   "완료 · 업데이트 11회"), stale RUNNING card shows the new italic
   "7시간 54분 동안 업데이트 없음" marker; relative time intact.
7. **Creative & Video consistency** — same rounded icon container, same card
   anatomy; the section reads as the same design language as AI Agents.

## Axes verified

| Axis | Result |
|---|---|
| Light | PASS — `agent-cards-light.png`, `agent-cards-grok-zcode.png` |
| Dark | PASS — `agent-cards-dark.png`, `agent-cards-dark2.png` |
| KO | PASS — `agent-cards-dark-ko.png`, `agent-cards-ko-font200.png` (per-app locale ko-KR) |
| EN | PASS — prior screenshots (per-app locale en-US default) |
| Font 200% | PASS — `agent-cards-ko-font200.png` (no truncation/overlap; icon container fixed at 40dp) |

Settings restored after the walk: per-app locale en-US, font scale 1.0,
night mode no.

## Issues found and fixed during the walk

- Grok card falsely showing CLI activity as its own "Last event" → replaced
  the merged health blob with per-source health (P2 fix, see RELIABILITY.md).
