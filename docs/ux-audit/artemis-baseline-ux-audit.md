# PigeonHub — Baseline UX Audit (Artemis task)

Date: 2026-09-11 · Build: debug (MVP-001E era, commit f7901c7) · Environment:
Medium_Phone_API_36.1 emulator (1080×2400), fresh `pm clear` state.
App code was not modified.

## Artemis setup status (honest record)

| Step | Result |
| --- | --- |
| Official repo | `github.com/google/artemis` cloned (Apache-2.0, Python 3.12+) |
| Install | **PASS with workaround** — `uv.lock` pins packages to a Google-internal proxy (`airlock-proxy.uplink.goog`); resolved by re-syncing from public PyPI. `artemis` CLI + daemon operational (dashboard at 127.0.0.1:8000) |
| Emulator connection | **PASS** — `artemis doctor`: ADB connected, 1080×2400, "Ready for perception and touch automation" |
| Physical device | **BLOCKED** — none attached (adb shows emulator only) |
| Agent execution | **BLOCKED_NO_LLM_API_KEY** — `artemis doctor`: "Multimodal LLM API Key ✖ Missing (GEMINI_API_KEY / OPENAI_API_KEY / ANTHROPIC_API_KEY)". Owner action: `artemis init` or a key from aistudio.google.com/app/apikey (free tier available) |

**Substitute executor (disclosed):** the audit below was performed by the
assistant itself — a multimodal agent driving the emulator UI from screenshots
and accessibility dumps only, following the Artemis task prompt (no known
coordinates, no source inspection for navigation). Caveat: the assistant
previously built this app, so "never seen it" purity is partial; on-screen
evidence was used as the sole navigation source.

## Task results (first-time user persona)

```
FIRST_LAUNCH_CLARITY           = PARTIAL
ONBOARDING_CLARITY             = FAIL
TEST_NOTIFICATION_DISCOVERABILITY = CONFUSING (two test buttons, different semantics)
INBOX_DISCOVERABILITY          = PASS
MY_PUSH_DISCOVERABILITY        = PARTIAL
SETTINGS_DISCOVERABILITY       = PASS
DEVELOPER_JARGON               = HIGH
GITHUB_PERCEIVED_AS_OPTIONAL   = YES (nothing in-app mentions GitHub)
CONFUSING_SCREENS              = 4 (documented below)
FAILED_TASKS                   = 2 of 7 for a user WITHOUT an invite
                                  (finish setup; send-from-computer)
CRASHES                        = 0
```

## What the naive user experienced, step by step

### 1. First launch — Inbox tab (01_first_launch.png)
- Intent: "figure out what this app does".
- What is on screen: permission banner ("Notifications are off" → Allow
  notifications), empty state ("No notifications yet — pushes you receive will
  appear here"), "Send a test push" link, tabs Inbox/My Push/Device/Settings.
- Naive read: "an inbox app… I wait for pushes." **Nothing says setup is
  required**, that an invite exists, or where to start. The user would wait
  indefinitely. The only actionable items are the permission banner and a
  mysterious "Send a test push".
- Clicks to understand the app: 4+ (tab exploration); still inconclusive.

### 2. My Push — invite gate (02_mypush_invite_gate.png)
- Intent: "finish setup".
- Found: "Install PigeonHub — Enter the invite code you received…", disabled
  Install button.
- Attempted action: proceed without a code → impossible (button disabled).
- Result: **dead end.** No in-app way to request an invite, no explanation of
  what a PigeonHub invite is or who sends them ("you received" — from whom?).
- Clicks wasted: 3–4 (tab switch, field focus, typing attempt) before
  concluding it is blocked.
- Confusion cause: beta invite model is enforced but never explained; the
  first screen should say "PigeonHub is invite-only during beta — get a code
  at …".

### 3. Device / Debug tab (03_device_tab.png)
- Intent: explore; maybe settings live here.
- Found: "Device / Debug" — FCM registration, masked token + "Copy token",
  "set it as FCM_DEVICE_TOKEN for the local sender", per-channel Settings
  links, and a second "Test push" section (NORMAL/HIGH) described as "runs the
  production pipeline locally".
- Naive reaction: this is developer tooling (the header literally says Debug).
  Two extra failure modes: (a) the user copies a token they don't need;
  (b) the user taps NORMAL/HIGH test push here, sees a notification, and
  concludes "delivery works" — which bypasses the real server path entirely.

### 4. Settings tab (04_settings_tab.png)
- Found: Notifications (permission status + App notification settings +
  Android system settings), Channels (Normal/High deep links), About.
- Verdict: clear; notification settings ARE discoverable here (2 clicks from
  any tab: Settings → visible; system settings one more).

### 5. Setup completed as an invited beta user
- Using an invite code: typed → Install → "Setting up PigeonHub..." →
  Connected. Flow itself is short (2 screens) and the invite field accepted
  the code; on the emulator a handwriting-IME popup interfered with typing
  twice (environment-specific, but text entry robustness matters).
- Post-setup My Push (06_mypush_connected.png): Connected, Endpoint,
  Copy endpoint, Copy cURL, Send test notification, Write credential ••••(v1),
  Regenerate. "Send test notification" performed the REAL round trip
  (device → Worker → D1 → FCM → notification arrived).

### 6. Inbox after setup (07_inbox_after_setup.png)
- The received push shows: NORMAL chip, "new", "0 minutes ago", title/body,
  and the metadata line "seq 1 · via FCM".
- Naive confusion: "seq 1 · via FCM" and the body "Sent from this device
  through the Worker, D1 and FCM." expose internal infrastructure names.

### 7. "How would I send a notification from a computer?"
- Answer exists post-setup: Copy cURL (contains endpoint + write token) → run
  in a terminal → notification arrives. Verified working (identical request
  shape was executed successfully against the device).
- Naive discoverability: only after setup, and "Copy cURL" requires knowing
  what cURL is. Pre-setup: impossible and unexplained.

### 8. "Is GitHub required?"
- Nothing in the app mentions GitHub at all → perceived answer: **No**.
  (Correct: GitHub is only where the source lives.)

## Confusion points (detail records)

| # | Screenshot | Wanted to | Artemis/naive action | Actual result | Clicks | Cause |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 01_first_launch.png | Understand the app / get pushes | Read screen, wait, explore tabs | Empty inbox; no setup hint | 4+ | No first-run guidance; onboarding hidden in a non-obvious tab |
| 2 | 02_mypush_invite_gate.png | Finish setup | Type code (none owned), seek "how to get one" | Blocked; disabled button; no guidance | 3–4 | Invite-only beta unexplained in-app |
| 3 | 03_device_tab.png | Find settings / test delivery | Open "Device / Debug", tap NORMAL test push | Local-only notification; token exposed | 2 | Dev tooling shipped in user navigation; second test-push path |
| 4 | (06 vs 01) | Verify real delivery | Two similarly-named test buttons exist on different tabs | "Send a test push" (local) ≠ "Send test notification" (real round trip) | — | Naming + placement ambiguity |
| 5 | 07_inbox_after_setup.png | Read a message | Open inbox | Message shows "seq 1 · via FCM", body names Worker/D1/FCM | 0 | Internal metadata rendered to users |

## UX BLOCKERS (top 5, severity order)

1. **[HIGH] First launch is a dead end.** No onboarding states that setup is
   required, that PigeonHub is invite-only in beta, or that setup lives in the
   "My Push" tab. A new user's pushes never arrive and nothing explains why.
   Evidence: 01 → 02.
2. **[HIGH] Invite code obtainability is unexplained.** Setup requires a code
   the user cannot obtain in-app; no request path, no contact, no "what is
   this?" hint beyond "the invite code you received". Beta-intentional, but it
   must be stated on the gate screen. Evidence: 02.
3. **[MEDIUM-HIGH] Two test-push mechanisms with different semantics.**
   Inbox "Send a test push" runs a local pipeline (works even before setup,
   proves nothing about server delivery); My Push "Send test notification" is
   the real Worker→D1→FCM round trip. A user "succeeds" on the wrong one and
   wrongly concludes server delivery works. Evidence: 01 vs 06.
4. **[MEDIUM] Developer tooling exposed as a main tab.** "Device / Debug"
   (token copy, env-var instructions, adb recipes, local test pushes) is one
   tap from first launch. Should be gated (e.g., hidden or debug-build-only)
   or clearly labelled as developer diagnostics. Evidence: 03.
5. **[LOW-MEDIUM] Internal metadata rendered to users.** "seq 1 · via FCM"
   rows and infra names inside message bodies ("through the Worker, D1 and
   FCM") read as debug output. Acceptable for the developer beta; review
   before any broader audience. Evidence: 07.

## Notes

- No crash occurred during the audit. The permission flow (banner → system
  dialog → granted) worked as designed (05_permission_dialog.png).
- The emulator's handwriting-IME panel intercepted text entry twice during
  invite typing (environment issue — Gboard stylus demo — not an app bug),
  but it demonstrates that a flaky IME can block setup; the invite field is
  the single point of entry to onboarding.
- Artemis remains installed and operational; once the owner supplies
  `GEMINI_API_KEY` (free tier) via `artemis init`, the same task prompt can be
  re-run by the Artemis agent for automated regression of the fixes above.
