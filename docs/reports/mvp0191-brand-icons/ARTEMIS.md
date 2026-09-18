# ARTEMIS — MVP-019.1 device audit

Release APK on emulator-5554 (`Medium_Phone_API_36.1`). Connections full-page
walk; screenshots in `before-after/`.

## Checks

| Check | Result |
|---|---|
| Brand recognizability (GitHub / ComfyUI / FramePack / Topaz / Blender distinguishable before reading names) | PASS — GitHub cat, Comfy blue/yellow C, Topaz steps tile, Blender orange mark; FramePack intentionally neutral + titled |
| Icon clipping | PASS — tiles fill the 40dp container without cut marks (Blender logomark square-padded, Topaz as-authored) |
| Optical centering | PASS — Blender mark center-padded after crop; others authored centered |
| Wrong background | PASS — GitHub transparent-mark on container; ComfyUI/Topaz full-bleed authored tiles; Blender transparent on container |
| Dark-mode visibility | PASS — GitHub flips to white Invertocat; ComfyUI/Topaz tiles keep authored contrast (`github-dark.png`, `creative-dark2.png`) |
| Title wrapping | PASS — font 200% KO: GitHub tagline wraps to two lines, no overlap (`creative-dark-ko-font200.png`) |
| Status/action alignment | PASS — pills and right CTAs aligned across all cards |

## Axes

- Light (EN, font 100%) — `github-light.png`, `creative-light.png`,
  `creative-light2.png`
- Dark (EN, font 100%) — `github-dark.png`, `creative-dark.png`,
  `creative-dark2.png`
- KO — `creative-dark-ko-font200.png`
- EN — default captures
- Font 100% / 200% — both captured

Settings restored after the walk: per-app locale en-US, font scale 1.0,
night mode no.
