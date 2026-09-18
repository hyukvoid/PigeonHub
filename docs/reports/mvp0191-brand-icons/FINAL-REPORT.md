# FINAL-REPORT — MVP-019.1 Brand Icon Alignment (2026-09-18)

Branch: `autonomous/mvp0191-brand-icon-alignment-20260918`.

## What changed

- `GitHubConnectionCard`: the Invertocat now renders in its official
  monochrome — `#0D1117` on light, white on dark — replacing the
  theme-tinted glyph; tagline updated to the workflow purpose.
- ComfyUI card: official Comfy Desktop tile (`Comfy-Org/desktop` shipped
  asset) replaces the sparkle glyph.
- Topaz card: renamed **Topaz Video AI → Topaz Video** everywhere (card
  title, detail text EN/KO, example command) and given the vendor's official
  mark; description updated to the current product purpose.
- Blender card: official logomark (cropped from `download.blender.org`
  official lockup — crop only) replaces the puzzle glyph.
- FramePack: official upstream repo audited — no standalone brand mark
  exists → neutral glyph retained, documented as the intended fallback.
- No Worker/CLI behavior, container system, or health-semantics changes.

## Scoreboard

| Gate | Status |
|---|---|
| ICON_GITHUB | VERIFIED — official Invertocat, official monochrome per theme |
| ICON_COMFYUI | VERIFIED — official Comfy Desktop asset (Comfy-Org repo) |
| ICON_FRAMEPACK | FALLBACK_DOCUMENTED — official repo has no brand asset; neutral glyph + visible title |
| ICON_TOPAZ_VIDEO | VERIFIED — vendor official mark from topazlabs.com CDN |
| ICON_BLENDER | VERIFIED — official logomark, crop-only, from blender.org branding kit |
| TOPAZ_CURRENT_NAMING | VERIFIED — "Topaz Video" everywhere; legacy "Topaz Video AI" removed (EN/KO/code) |
| BRAND_SOURCE_PROVENANCE | VERIFIED — per-asset source URL, exact file, rank, conversion, modification recorded (ICON-SOURCES.md) |
| VISUAL_CONSISTENCY | VERIFIED — one 40dp rounded container system; tiles vs monochrome mark used per brand type deliberately |
| LIGHT_DARK | VERIFIED — GitHub monochrome flip; tiles authored-contrast in both |
| KO_EN | VERIFIED — new taglines/copy render in both locales |
| FONT_200 | VERIFIED — KO + 200% capture, no clipping/overlap |
| ARTEMIS | VERIFIED — structured walk + before/after captures |
| ANDROID_REGRESSION | VERIFIED — unit tests + assembleDebug + assembleRelease BUILD SUCCESSFUL; `git diff --check` clean; secret scan clean |

## Final questions

- Connections 화면에서 사용자가 이름을 읽기 전에 GitHub/Claude/Codex/Grok/
  ZCode/ComfyUI/Topaz/Blender를 구분할 수 있는가? **YES** — 화면상 아이콘
  충돌은 하나도 없다(스파클 재사용 해소).
- 가짜 브랜드 아이콘보다 명확한 neutral fallback이 낫다는 원칙을 지켰는가?
  **YES** — FramePack은 공식 마크 부재 시 FALLBACK_DOCUMENTED로 처리했다.
