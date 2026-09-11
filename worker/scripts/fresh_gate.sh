#!/usr/bin/env bash
# MVP-002A PHASE 7 — Fresh-install gate (release build), one full run.
# Usage: fresh_gate.sh <run-number> <test-invite-code>
# T0 = fresh app launch, T1 = Copy cURL screen visible. No secrets printed.
set -u
ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
RUN="$1"; CODE="$2"
EV="/c/PigeonHub/docs/evidence/mvp-002a/phase-7"
mkdir -p "$EV"

dump() { "$ADB" shell "uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml" 2>/dev/null; }
tap_text() { # tap center of first node with exact text
  local B N A B2 C D
  B=$(tr '<' '\n<' < "$1" | grep "text=\"$2\"" | grep -o 'bounds="[^"]*"' | head -1)
  [ -z "$B" ] && { echo "TAP MISS: $2"; return 1; }
  N=$(echo "$B" | tr -d '\r' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
  "$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 ))
}

"$ADB" uninstall com.pigeonhub.app >/dev/null 2>&1
"$ADB" install /c/PigeonHub/android/app/build/outputs/apk/release/app-release.apk >/dev/null 2>&1 || { echo "RUN$RUN install FAILED"; exit 1; }
"$ADB" logcat -c

T0=$(date +%s)
"$ADB" shell am start -n com.pigeonhub.app/.MainActivity >/dev/null 2>&1
sleep 6
dump > "$EV/run${RUN}_1_purpose.xml"
B=$(tr '<' '\n<' < "${EV}/run${RUN}_1_purpose.xml" | grep 'text="Get started"' | grep -o 'bounds="[^"]*"' | head -1)
N=$(echo "$B" | tr -d '\r' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
"$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 )); sleep 2
dump > "$EV/run${RUN}_2_invite.png" 2>/dev/null
"$ADB" exec-out screencap -p > "$EV/run${RUN}_2_invite.png"
# type invite code
B=$(tr '<' '\n<' < /dev/null 2>/dev/null)
dump > /tmp/g.xml; B=$(tr '<' '\n<' < /tmp/g.xml | grep 'class="android.widget.EditText"' | grep -o 'bounds="[^"]*"' | head -1)
N=$(echo "$B" | tr -d '\r' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
"$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 )); sleep 1
"$ADB" shell input text "$CODE"; sleep 1
dump > /tmp/g.xml
tap_text /tmp/g.xml "Continue"; sleep 2
# permission: tap Allow notifications, then system Allow
dump > /tmp/g.xml
B=$(tr '<' '
<' < /tmp/g.xml | grep 'text="Allow notifications"' | grep -o 'bounds="[^"]*"' | tail -1)
N=$(echo "$B" | tr -d '' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
"$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 )); sleep 4
dump > /tmp/g2.xml
B=$(tr '<' '\n<' < /tmp/g2.xml | grep 'permission_allow_button' | grep -o 'bounds="[^"]*"' | head -1)
if [ -n "$B" ]; then N=$(echo "$B" | tr -d '\r' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
  "$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 )); fi
# wait for bootstrap -> Connected (Copy cURL visible on My Push tab)
CONNECTED=""
for i in $(seq 1 12); do sleep 3; dump > /tmp/g.xml
  if grep -q 'text="Copy cURL"' /tmp/g.xml; then CONNECTED=yes; break; fi
  # if still on permission explanation (denied path), continue is fine
done
"$ADB" exec-out screencap -p > "$EV/run${RUN}_3_connected.png"
T1=$(date +%s)
# go to My Push tab if Copy cURL not yet visible
if [ -z "$CONNECTED" ]; then
  dump > /tmp/g.xml; B=$(tr '<' '\n<' < /tmp/g.xml | grep 'text="My Push"' | grep -o 'bounds="[^"]*"' | head -1)
  N=$(echo "$B" | tr -d '\r' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
  "$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 )); sleep 3
  dump > /tmp/g.xml; grep -q 'text="Copy cURL"' /tmp/g.xml && CONNECTED=yes
  "$ADB" exec-out screencap -p > "$EV/run${RUN}_4_mypush.png"
fi
SEND=""; dump > /tmp/g.xml
if grep -q 'text="Send test notification"' /tmp/g.xml; then
  B=$(tr '<' '\n<' < /tmp/g.xml | grep 'text="Send test notification"' | grep -o 'bounds="[^"]*"' | head -1)
  N=$(echo "$B" | tr -d '\r' | sed 's/[^0-9]/ /g'); read -r A B2 C D <<< "$N"
  "$ADB" shell input tap $(( (A+C)/2 )) $(( (B2+D)/2 )); sleep 10
  dump > /tmp/g.xml; grep -q 'This device received the push' /tmp/g.xml && SEND="DEVICE_PUSH_RECEIVED"
  grep -q 'Saved on the PigeonHub server' /tmp/g.xml && SEND="${SEND:+$SEND/}SERVER_ACCEPTED"
  "$ADB" exec-out screencap -p > "$EV/run${RUN}_5_test_result.png"
fi
ELAPSED=$((T1 - T0))
echo "RUN$RUN RESULT: connected_curl_screen=$CONNECTED elapsed=${ELAPSED}s test_state=$SEND"
