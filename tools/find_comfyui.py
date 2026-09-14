"""Deeper ComfyUI hunt: depth-2/3 dir scan on C:/ and D:/, processes, config dirs."""
import os
import pathlib

hits = []
for root in ("C:/", "D:/"):
    try:
        top = list(os.scandir(root))
    except Exception:
        continue
    for e1 in top:
        if not e1.is_dir():
            continue
        name = e1.name.lower()
        if "comfy" in name:
            hits.append(e1.path)
            continue
        # skip obviously huge/system dirs at depth 2
        if name in ("windows", "program files", "program files (x86)", "programdata", "$recycle.bin", "system volume information"):
            continue
        try:
            for e2 in os.scandir(e1.path):
                if e2.is_dir() and "comfy" in e2.name.lower():
                    hits.append(e2.path)
        except Exception:
            pass

home = pathlib.Path.home()
for cfg in (".comfyui", "ComfyUI", ".config/ComfyUI", "AppData/Roaming/ComfyUI", "AppData/Local/ComfyUI", "AppData/Roaming/ComfyUI-Desktop"):
    p = home / cfg
    if p.exists():
        hits.append(str(p))

print("HITS:", hits if hits else "none")

# any running process?
try:
    out = os.popen('tasklist /FI "IMAGENAME eq ComfyUI.exe" /FO CSV 2>NUL').read()
    print("PROC:", out.strip()[:200])
except Exception:
    pass
