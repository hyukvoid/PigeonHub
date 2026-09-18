# ICON-SYSTEM — MVP-019 agent card icons

## Mapping (source → reason)

| Card | Asset | Source | Why |
|---|---|---|---|
| Claude Code | `drawable/ic_agent_claude.xml` | Official Claude symbol SVG (Wikimedia `Claude_AI_symbol.svg`, single path, Anthropic orange `#D97757`) on the Claude ivory tile `#F0EEE6` | The orange starburst is the instantly recognizable Claude mark; matches the requested "orange Claude Code" reference. The previous `Icons.Outlined.Psychology` (generic) is retired. |
| OpenAI Codex | `drawable/ic_agent_codex.xml` | Official OpenAI blossom path extracted from the OpenAI logo SVG (Wikimedia), white on `#0F0F0F` | Reads as "OpenAI" at a glance; per rules there is exactly ONE OpenAI coding-agent card (Codex) — no separate ChatGPT card. The generic `Icons.Outlined.Code` is retired. |
| Grok Build | `drawable/ic_agent_grok.xml` | Official Grok tile geometry from the xAI Grok logo SVG (Wikimedia): white slash on `#0A0A0A` | The slash tile is Grok's app-icon mark; pairs with the "Grok Build" title for instant recognition. The generic sparkle (`AutoAwesome`) is retired. |
| ZCode · GLM | `drawable-nodpi/ic_agent_zcode.png` | The actual ZCode desktop app icon (local install `resources/icon.png`), trimmed and downscaled to 192px | It IS the product's real mark; the campaign forbids replacing it with an arbitrary generic icon. Kept. |

User-provided reference images were not available in this environment, so the
fallback policy below applies: official vector marks in official colors, which
satisfy the same recognition goal.

## Sizing / padding rules (uniform system)

- One shared container for every card (agent + creative): 40dp,
  `RoundedCornerShape(10.dp)` — app-icon-like; the circle was changed to a
  rounded square so brand tiles sit naturally.
- Brand tiles: rendered **full-bleed** (`ContentScale.Crop`, `fillMaxSize`) —
  the tile IS the icon background; no double-frame, no per-card padding tweaks.
- Material glyphs (Creative & Video cards, My Push): unchanged treatment —
  24dp glyph, `onPrimaryContainer` tint, centered in the same rounded
  container — so both icon languages share one visual system.
- No aspect distortion: vectors keep official proportions; the ZCode PNG was
  center-cropped from its transparent bbox (86,96)-(938,948) before downscale.

## Fallback policy

- If a brand asset cannot be sourced: use the official mark geometry in
  official colors (what was done), never an unrelated generic glyph.
- Material `icon` field is retained per `ToolSpec` as the fallback and for the
  Creative section; `brandIconRes` wins when present.
- Dark/light: tiles carry their own brand backgrounds (black tiles are
  outline-separated by the container shape on dark cards — verified in both
  modes). No theme-dependent recoloring of brand marks.

## Generation

`packaging/icons/generate_brand_tiles.py` (committed) converts the official
SVG paths to explicit-absolute Android `pathData` — reproducible; the raw SVG
sources are Wikimedia Commons files fetched at generation time.
