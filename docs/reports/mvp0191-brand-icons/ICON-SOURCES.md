# ICON-SOURCES — MVP-019.1

Per-card provenance. Source policy rank: vendor official brand/assets page →
vendor official repository → vendor installed desktop application's shipped
icon → verified official package asset → neutral PigeonHub fallback.

| Product | Display name | Source URL | Exact source asset | Official / fallback | Conversion | Modification |
|---|---|---|---|---|---|---|
| GitHub | GitHub | (mark path pre-existing in repo; geometry is GitHub's official Octicon "mark-github" 16×16 path) | `ic_github_mark.xml` — official Invertocat path | Official | None (vector reused); rendering changed from theme tint to official monochrome (`#0D1117` light / `#FFFFFF` dark) | Recolor per official monochrome guidance only |
| ComfyUI | ComfyUI | https://github.com/Comfy-Org/desktop (`assets/UI/`) | `Comfy_Logo_x256.png` — the shipped Comfy Desktop app icon | Official (vendor official repo, rank 2) | PIL: LANCZOS downscale 256→192, RGBA kept | None (as-authored tile) |
| FramePack | FramePack | https://github.com/lllyasviel/FramePack (git tree searched for image assets) | none found | **Neutral PigeonHub fallback** (rank 5) — retained `Icons.Outlined.Movie` glyph in the shared container | n/a | None. "No verified standalone official FramePack mark found. Neutral product glyph intentionally used." |
| Topaz | Topaz Video | https://www.topazlabs.com/topaz-video (vendor page → vendor CDN) | `tnb-favicon-256.png` (white mark on charcoal `#212020` tile) | Official (vendor official assets, rank 1) | PNG used as-is (256→256), full-bleed tile | None |
| Blender | Blender | https://download.blender.org/branding/ (blender.org official branding kit page) | `blender_logo_socket.png` — official lockup; logomark cropped out | Official (vendor official brand assets page, rank 1) | PIL: crop to logomark, alpha-trim, square-pad, 192px | Crop only — no recolor, no redraw, no compositing |

## Rendering rules applied

- GitHub renders as a **monochrome mark** (rank: section 11 first choice —
  dark mark on light, white mark on dark), not as a tile.
- ComfyUI / Topaz render as **full-bleed brand tiles** (their source assets
  carry their own backgrounds), same as the MVP-019 agent tiles.
- Blender's logomark is transparent-background artwork rendered at full tile
  size; the shared container supplies the backdrop in both themes.
- FramePack keeps the neutral video glyph — the visible "FramePack" title
  carries the identification, per the fallback rule.
