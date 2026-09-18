"""Generate MVP-019 agent brand tiles as Android vector drawables.

Reads the official brand SVGs (Wikimedia Commons) in /tmp/brand-svgs and emits
explicit-absolute-path vector drawables (Android's PathParser is safest with
explicit M/L/C/Z), each on a full-bleed brand tile inside the shared 40dp
rounded card container.
"""
import os
import re
import tempfile
from pathlib import Path

SRC = Path(os.environ.get("TEMP", tempfile.gettempdir())) / "brand-svgs"

ARGS = {"M": 2, "m": 2, "L": 2, "l": 2, "C": 6, "c": 6, "H": 1, "h": 1, "V": 1, "v": 1, "Z": 0, "z": 0}


def to_absolute(d: str) -> str:
    """Convert an SVG path to explicit absolute M/L/C/Z commands."""
    toks = re.findall(r"[MmLlCcHhVvZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    out = []
    k = 0
    cx = cy = sx = sy = 0.0
    while k < len(toks):
        t = toks[k]
        if t not in ARGS:
            k += 1
            continue
        need = ARGS[t]
        k += 1
        args = []
        while k < len(toks) and toks[k] not in ARGS:
            args.append(float(toks[k]))
            k += 1
        j = 0
        first = True
        while j < len(args):
            a = args[j : j + need]
            if len(a) < need:
                break
            cmd = t
            if not first and cmd in "Mm":  # implicit repeats are linetos
                cmd = "L" if cmd == "M" else "l"
            if cmd in "Mm":
                if cmd == "m":
                    cx += a[0]; cy += a[1]
                else:
                    cx, cy = a[0], a[1]
                sx, sy = cx, cy
                out.append(f"M{cx:.2f} {cy:.2f}")
            elif cmd in "Ll":
                if cmd == "l":
                    cx += a[0]; cy += a[1]
                else:
                    cx, cy = a[0], a[1]
                out.append(f"L{cx:.2f} {cy:.2f}")
            elif cmd in "Cc":
                if cmd == "c":
                    p = [(cx + a[0], cy + a[1]), (cx + a[2], cy + a[3])]
                    cx += a[4]; cy += a[5]
                else:
                    p = [(a[0], a[1]), (a[2], a[3])]
                    cx, cy = a[4], a[5]
                out.append(f"C{p[0][0]:.2f} {p[0][1]:.2f} {p[1][0]:.2f} {p[1][1]:.2f} {cx:.2f} {cy:.2f}")
            elif cmd in "Hh":
                cx = a[0] if cmd == "H" else cx + a[0]
                out.append(f"L{cx:.2f} {cy:.2f}")
            elif cmd in "Vv":
                cy = a[0] if cmd == "V" else cy + a[0]
                out.append(f"L{cx:.2f} {cy:.2f}")
            elif cmd in "Zz":
                out.append("Z")
                cx, cy = sx, sy
            first = False
            j += need
    return " ".join(out)


def rounded_tile(size: float, radius: float) -> str:
    return (
        f"M{radius} 0 L{size - radius} 0 C{size - radius * 0.45} 0 {size} {radius * 0.45} {size} {radius} "
        f"L{size} {size - radius} C{size} {size - radius * 0.45} {size - radius * 0.45} {size} {size - radius} {size} "
        f"L{radius} {size} C{radius * 0.45} {size} 0 {size - radius * 0.45} 0 {size - radius} "
        f"L0 {radius} C0 {radius * 0.45} {radius * 0.45} 0 {radius} 0 Z"
    )


def vector(name: str, body: str, size: float = 100.0) -> str:
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size / 2.5:.0f}dp"\n    android:height="{size / 2.5:.0f}dp"\n'
        f'    android:viewportWidth="{size:.0f}"\n    android:viewportHeight="{size:.0f}">\n'
        f"{body}\n</vector>\n"
    )


def group(scale: float, tx: float, ty: float, inner: str) -> str:
    return (
        f'  <group\n      android:scaleX="{scale:.5f}"\n      android:scaleY="{scale:.5f}"\n'
        f'      android:translateX="{tx:.2f}"\n      android:translateY="{ty:.2f}">\n'
        f"{inner}\n  </group>"
    )


def path(d: str, fill: str) -> str:
    return f'    <path\n        android:pathData="{d}"\n        android:fillColor="{fill}"/>'


out_dir = Path("C:/PigeonHub/android/app/src/main/res/drawable")
out_dir.mkdir(parents=True, exist_ok=True)

# ---- Claude: official starburst (100x100 grid) on the Claude ivory tile ----
claude_d = to_absolute((SRC / "claude_path.txt").read_text())
claude_scale = 62.0 / 100.0
claude = vector(
    "ic_agent_claude",
    path(rounded_tile(100, 22), "#F0EEE6")
    + "\n"
    + group(claude_scale, 50 - 50 * claude_scale, 50 - 50 * claude_scale, path(claude_d, "#D97757")),
)
(out_dir / "ic_agent_claude.xml").write_text(claude, encoding="utf-8")

# ---- Codex: official OpenAI blossom (320-grid centered at 160,160) on black ----
openai_d = to_absolute((SRC / "openai_path.txt").read_text())
openai_scale = 58.0 / 324.0  # mark spans ~324 units incl. control-point overshoot
openai_tx = 50 - 160 * openai_scale
codex = vector(
    "ic_agent_codex",
    path(rounded_tile(100, 22), "#0F0F0F")
    + "\n"
    + group(openai_scale, openai_tx, openai_tx, path(openai_d, "#FFFFFF")),
)
(out_dir / "ic_agent_codex.xml").write_text(codex, encoding="utf-8")

# ---- Grok: official tile slash (163.53 square) on the xAI black tile ----
grok_scale = 100.0 / 163.53
slash = to_absolute("m105.02 34.51-66.3 94.68h19.96l66.3-94.68z")
grok = vector(
    "ic_agent_grok",
    path(rounded_tile(100, 22), "#0A0A0A")
    + "\n"
    + group(grok_scale, 0, 0, path(slash, "#FFFFFF")),
)
(out_dir / "ic_agent_grok.xml").write_text(grok, encoding="utf-8")

print("wrote:", [p.name for p in out_dir.glob("ic_agent_*.xml")])
