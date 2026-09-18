# ANDROID-UI — MVP-019 card structure changes

## Shared card anatomy (unchanged order)

```
[icon 40dp rounded container]
title (titleMedium, semibold)
one-line description (bodyMedium, onSurfaceVariant)
health line (dot + relative time)   ← optional, now per-source
status pill (Supported/Connected/…)      right CTA (Set up / Manage / →)
```

## Changes

1. **`CardHeader` container**: `CircleShape` → `RoundedCornerShape(10.dp)`,
   40dp — applies to AI Agents, Creative & Video, My PC cards alike, so both
   icon languages (brand tiles and tinted glyphs) share one frame.
2. **`ToolSpec.brandIconRes: Int?`** — when set, the tile renders full-bleed
   (`ContentScale.Crop`) in the container with its own brand colors; otherwise
   the previous tinted-glyph treatment applies. Used by the Connections list
   and the ToolDetail screen header.
3. **`JobCard` stale marker** — RUNNING cards whose newest event is ≥2h old
   render an italic "No updates for X" line (`job_stale_no_updates`,
   `RelativeTime.staleNoUpdatesRes`, threshold `STALE_RUNNING_MS`).
4. **Per-agent health** — `AiAgentCards` passes `merge(health, tool.id, "agent")`
   instead of one merged blob; `HealthLine` renders nothing when a source has
   no record, which is the honest "never used" state.

## Strings added

- `job_stale_no_updates` (EN/KO), `duration_day` (EN/KO).
