# MVP-002A Report — First Real Automation: Productization & Destruction Test

Date: 2026-09-11~12
Scope: onboarding comprehension fix, release navigation cleanup, honest
delivery states, GitHub Actions integration, destruction testing.
No architecture rewrite, no new features beyond the first-use flow.

## Result block

```
FINAL_VERDICT            = MVP_002A = PASS

BASELINE                 = FIRST_LAUNCH_CLARITY: PARTIAL, ONBOARDING_CLARITY: FAIL,
                           TEST_NOTIFICATION_DISCOVERABILITY: CONFUSING,
                           DEVELOPER_JARGON: HIGH, CRASHES: 0
AFTER                    = FIRST_LAUNCH_DEAD_END: 0, ONBOARDING: PASS,
                           INVITE_EXPLANATION: PASS, PERMISSION_DENIED_FLOW: PASS,
                           MAIN_NAV: Inbox/My Push/Settings, DEVICE_DEBUG_RELEASE: 0,
                           NORMAL_UI_JARGON: 0, USER_VISIBLE_TESTS: 1

FIRST_USE_FLOW           = PASS (fresh install → purpose → invite → permission →
                                  bootstrap → Connected → Send test notification →
                                  DEVICE_PUSH_RECEIVED → Copy cURL screen)
INVITE_FLOW              = PASS (Private beta explained; why/where on screen;
                                  single-use test pool separate from beta pool)
PERMISSION_FLOW          = PASS (explain-before-request; deny-safe: setup continues;
                                  denied state: "Messages will still appear in Inbox")
SETUP_RESUME             = PASS (REGISTERING process death → relaunch → same
                                  installation converged, no new credentials)

NAVIGATION               = PASS (release: exactly Inbox / My Push / Settings)
DEBUG_UI_REMOVAL         = PASS (Device/Debug, FCM token copy, FCM_DEVICE_TOKEN
                                  instructions, local test injector, ADB recipes — all
                                  behind BuildConfig.DEBUG, zero exposure in release)
JARGON_AUDIT             = PASS (release inbox/mypush: 0 instances of FCM/D1/Worker/
                                  Room/seq/ADB/management/token/debug;
                                  "Write credential" → "Send key")

HONEST_DELIVERY_STATES   = PASS   4 states implemented and verified:
                                  SERVER_ACCEPTED = "Saved on the PigeonHub server."
                                  DEVICE_PUSH_RECEIVED = "This device received the push."
                                  SYNC_RECOVERED_ONLY = "recovered into your Inbox."
                                  OS_NOTIFICATION_DISABLED = "Android notifications are off."

COPY_CURL                = PASS   two entry points only: (1) after real test success,
                                  (2) My Push tab. Not in Settings. Write token reaches
                                  clipboard only via explicit user copy action.

REAL_AUTOMATION          = PASS   GitHub Actions → Worker → D1 → FCM → Android:
                                  4+ real workflow_dispatch + main-push events, all
                                  runs "completed success", device notification confirmed
GITHUB_ACTIONS_RUNS      = 4+ (workflow_dispatch ×3, main-push ×1+; every later
                                  main push fires an event automatically)

FRESH_INSTALL_GATE_1     = PASS   release build; Copy cURL screen reached;
                                  DEVICE_PUSH_RECEIVED; elapsed 96s
FRESH_INSTALL_GATE_2     = PASS   release build; 96s; DEVICE_PUSH_RECEIVED
FRESH_INSTALL_GATE_3     = PASS   release build; 95s; DEVICE_PUSH_RECEIVED

FIRST_USE_TIME           = 95s / 96s / 95s (deterministic self-run benchmark;
                                  target ≤5 min = 300s; actual ~1.6 min = 3× under)

SCREEN_MATRIX            = PASS   normal phone + font 100% + light: PASS
                                  normal phone + font 200% + dark: PASS after FlowRow fix
                                  (font 200% caused per-character button stacking in the
                                  original Row layout — found by this test, fixed)
                                  small phone profile: BLOCKED_NO_SMALL_AVD
ACCESSIBILITY            = PARTIAL TalkBack labels present on nav tabs and icon
                                  buttons; full accessibility audit deferred
FAILURE_INJECTION        = PASS   F5 permission denied → OS_NOTIFICATION_DISABLED
                                  F6 offline → offline banner + saved messages
                                  F7 FCM skipped → sync recovery (MVP-001D evidence)
                                  F8 force close/restart → state resume verified
                                  F9 upgrade path → onboarding reappears = 0
                                  F10 light↔dark transition → PASS (both render)
                                  F12 kill switch → 503 {stored:false} verified

REGRESSION               = PASS   bootstrap resume / private publish / auth isolation /
                                  D1 persistence / FCM receive / Room persistence /
                                  missed-push sync / write-token rotation / retention
                                  contract / kill switch / capacity gate — all still
                                  working after the UI changes

SECURITY                 = PASS   management credential: 0 exposure in release UI
                                  FCM token: 0 exposure in release UI
                                  debug commands: 0 exposure in release UI
                                  env vars: 0 exposure in release UI
                                  Copy cURL: write token only on explicit user action
                                  GitHub workflow: 0 literal secrets (uses repository
                                  secrets exclusively)

SECRET_SCAN              = PASS   tracked-file scan clean; real-value substring counts = 0
PAID_RESOURCE_CREATED    = NO

ARTEMIS_AUTONOMOUS       = NOT_RUN (BLOCKED_NO_LLM_PROVIDER — GEMINI_API_KEY missing;
                                    owner: `artemis init` or aistudio key to enable)
SCRIPTED_HEURISTIC_UX_AUDIT = PASS (T1–T7 on release build: purpose ✓, invite ✓,
                                    test notification ✓, inbox ✓, automation ✓,
                                    settings ✓, internal/debug info found = 0)
HUMAN_FIRST_USE          = NOT_PERFORMED (no human test subject available this session)

FREE_TIER_USAGE          = Workers Free: well under limits (total requests this session
                                  ~100 vs 100k/day; CPU per request <10ms vs 10ms limit)
                                  D1 Free: ~200 rows written vs 100k/day limit
                                  FCM: free, unlimited
```

## KNOWN_ISSUES

1. Small-phone screen matrix was not tested (no small-phone AVD available on
   this machine). The FlowRow fix addresses the font-200% clipping case, which
   is the more common accessibility issue; a 360×640 AVD test remains TODO.
2. The interrupt-REGISTERING auto-resume depends on the stored invite code
   (local DataStore). If the DataStore entry is somehow lost while the
   status is REGISTERING, the state silently stays REGISTERING with no
   automatic recovery. A future version could store the invite code encrypted
   with the same Keystore key as the credentials blob.
3. Test invite pool rotation via `wrangler secret put` has a ~30s propagation
   delay before the new codes are accepted. Test scripts must wait after
   rotating. This is test-harness friction, not a product bug.
4. The release build is signed with the debug keystore (local validation
   convenience). Store/public distribution requires the owner to configure
   a proper release keystore.

## OWNER ACTIONS NEEDED

1. Set `GEMINI_API_KEY` via `artemis init` (free at aistudio.google.com/app/apikey)
   to enable Artemis autonomous UX testing for future milestones.
2. Distribute the 5 beta invite codes (worker/.dev.vars INVITE_CODE_1..5) to
   the first 5 beta users.
3. Optional: configure a release keystore for store-grade signing.

## COMMITS

```
90cee44  android: first-use onboarding, release nav cleanup, honest delivery states (MVP-002A P1-3)
1e66efa  ci+worker: GitHub Actions notification integration (MVP-002A P4)
8c36c44  android: destruction-test fixes — FlowRow button wrap at font 200% + Room v3 identity repair migration
a6ac0fc→f7901c7  (MVP-001E baseline commits, before this session)
ef11345  docs: baseline UX audit evidence
<this commit> docs: MVP-002A report + dogfood template + evidence
```

## DOGFOODING TEMPLATE

`docs/dogfooding/dogfood-log-template.md` — 7-day, 2 automations, 20 events,
per-event tracking of delivery state, sufficiency, annoyance, duplicates,
missing messages, and sync recovery.

## SESSION END NOTES

- The Fastify dev sender (localhost:8787) and wrangler dev (localhost:8788)
  are still running as reference tools. They can be stopped at any time.
- The emulator is running in GUI windowed mode with 2 registered installations
  (1 device + 1 CI automation) and 5 fresh beta invite codes available.
- All workers are deployed at
  https://pigeonhub-push.pigeonhub.workers.dev (production).

## NEXT (not started — per STOP CONDITION)

MVP-002B: 7-day dogfooding → 2 automations / 20 real events → external target
user #1 observation. Only after human first-use observation:
Quiet Hours evaluation, theme picker evaluation, multi-device evaluation.
