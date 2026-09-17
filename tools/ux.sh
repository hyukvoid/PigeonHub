#!/usr/bin/env bash
# RC-001A Artemis UX walk helpers. Source this file:
#   source /c/PigeonHub/tools/ux.sh
# Provides: dump_ui, texts, shot, tap_text, tap_bounds_center
ADB="${ADB:-$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
UIX=/c/PigeonHub/android/ui.xml
PYWIN() { python - "$@"; }

dump_ui() {
  "$ADB" shell "uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml" > "$UIX" 2>/dev/null
  wc -c < "$UIX"
}

texts() {
  python -c "
import re
xml = open(r'C:\PigeonHub\android\ui.xml', encoding='utf-8', errors='ignore').read()
out = [t.replace('&#10;', ' | ') for t in re.findall(r'text=\"([^\"]+)\"', xml) if t.strip()]
print(out)
"
}

bounds_of() {  # exact text match → "x1,y1,x2,y2"
  python - "$1" <<'PYEOF'
import re, sys
xml = open(r'C:\PigeonHub\android\ui.xml', encoding='utf-8', errors='ignore').read()
m = re.search(r'text="' + re.escape(sys.argv[1]) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m:
    print(f"{(int(m.group(1))+int(m.group(3)))//2},{(int(m.group(2))+int(m.group(4)))//2}")
PYEOF
}

tap_text() {  # tap center of first node containing substring
  local C
  C=$(python - "$1" <<'PYEOF'
import re, sys
xml = open(r'C:\PigeonHub\android\ui.xml', encoding='utf-8', errors='ignore').read()
pat = re.escape(sys.argv[1])
m = re.search(r'text="[^"]*' + pat + r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m:
    print(f"{(int(m.group(1))+int(m.group(3)))//2},{(int(m.group(2))+int(m.group(4)))//2}")
PYEOF
)
  if [ -z "$C" ]; then echo "TAP MISS: $1"; return 1; fi
  local X=${C%,*} Y=${C#*,}
  "$ADB" shell input tap "$X" "$Y"
}

shot() {
  "$ADB" exec-out screencap -p > "/c/PigeonHub/docs/reports/rc001a-artemis-ux/$1"
}

nav() {  # release build 3-tab nav: inbox|connections|settings
  case "$1" in
    inbox) "$ADB" shell input tap 172 2274 ;;
    connections) "$ADB" shell input tap 540 2274 ;;
    settings) "$ADB" shell input tap 907 2274 ;;
  esac
}
