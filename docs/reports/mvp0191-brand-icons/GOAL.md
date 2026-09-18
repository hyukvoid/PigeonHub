# GOAL — MVP-019.1 Brand Icon Alignment (2026-09-18)

Branch: `autonomous/mvp0191-brand-icon-alignment-20260918` (from `a502638`).

Bring the GitHub and Creative & Video cards up to the same brand-recognition
bar as the AI Agent cards, without touching the MVP-019 reliability work, the
container system, or any Worker/CLI behavior.

## Scope

- GitHub: official Invertocat, official monochrome (black on light / white on
  dark) — never theme-tinted.
- ComfyUI: official Comfy Org asset (Comfy Desktop shipped icon).
- FramePack: only the official upstream repo (`lllyasviel/FramePack`) counts;
  no standalone brand mark exists there → documented neutral fallback.
- Topaz: rename "Topaz Video AI" → "Topaz Video" (current product name) and
  use the vendor's official mark.
- Blender: official logomark from Blender's official branding assets —
  crop only, no recolor/redraw.

## Non-goals

No new services, no connector/backend changes, no container redesign, no
health-semantics changes.
