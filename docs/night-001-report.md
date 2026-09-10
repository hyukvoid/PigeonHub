# Night-001 Report — PigeonHub DB-less Android Push Prototype

Date: 2026-09-11 (session 2026-09-10 night ~ 2026-09-11)
Scope: everything achievable WITHOUT Firebase setup and WITHOUT paid infra.
Firebase/FCM real integration: intentionally deferred to a owner-run step
(docs/FIREBASE_SETUP.md). Nothing was re-used from other Firebase projects;
`applicationId` stayed fixed at `com.pigeonhub.app`.

```
ANDROID_BUILD          = PASS   (:app:assembleDebug — Gradle 9.4.1, AGP 8.13.2, Kotlin 2.2.21,
                                 compileSdk 36 / minSdk 26; green with zero Firebase config;
                                 gradle wrapper committed)
FCM_REGISTRATION       = BLOCKED_PENDING_FIREBASE_SETUP
                                 (FirebaseGate guards all Firebase access; Device tab shows the
                                  blocked state + instructions. Server: mock transport auto-selected.)
LOCAL_SERVER           = PASS   (Fastify dev sender on :8787; strict tsc clean; 8/8 node:test;
                                 /health + /push smoke-tested via curl; optional bearer auth enforced)
CURL_PUSH              = PASS (mock transport) / REAL_FCM = BLOCKED_PENDING_FIREBASE_SETUP
                                 (full request path validated: JSON schema, https-only url rule,
                                  message_id/sent_at generation, exact FCM payload logged)
PHYSICAL_DEVICE        = BLOCKED_NO_ANDROID_DEVICE  →  fallback used: Android Emulator
                                 (Medium_Phone_API_36.1, playstore image); no waiting loops
FOREGROUND             = PASS   (in-app test push → notification posted to pigeonhub_normal,
                                 importance DEFAULT; permission banner → runtime grant flow verified on-screen)
BACKGROUND             = PASS   (HOME → adb inject → notification present in shade)
PROCESS_NOT_RUNNING    = PASS*  (am force-stop → pid gone → broadcast → process restarted by the
                                 system, pipeline delivered, notification posted.
                                 *This validates OUR cold pipeline; true FCM high-priority wake of a
                                  dead process requires real FCM → BLOCKED_PENDING_FIREBASE_SETUP)
SCREEN_OFF             = PASS   (mWakefulness=Asleep → message delivered + notification posted)
NORMAL_PRIORITY        = PASS   (payload priority normal → channel pigeonhub_normal, importance 3)
HIGH_PRIORITY          = PASS   (payload priority high → channel pigeonhub_high, importance 4;
                                 FCM android.priority wiring implemented, real send blocked pending Firebase)
DOZE                   = BLOCKED_PENDING_FIREBASE_SETUP
                                 (doze validation needs a real FCM data message to wake the app;
                                  the adb injector is not equivalent)
DUPLICATE              = PASS   (same message_id: 1st "delivered", 2nd "duplicate push suppressed";
                                 suppression survives force-stop via SharedPreferences LRU;
                                 unit-tested LRU eviction too)
NOTIFICATION_TAP       = PASS   (app backgrounded → tap shade notification → MainActivity resumed via
                                 PendingIntent → entry highlighted "Opened from notification")
CLICK_URL              = PASS   (inbox entry "Open" → ACTION_VIEW https-only → Chrome opened and
                                 navigated to https://example.com/open-me; http/javascript/garbage urls
                                 rejected with warnings — covered by unit tests)
ARTEMIS                = BLOCKED (Artemis tooling not available in this environment. The full Artemis
                                 checklist was executed MANUALLY instead: launch → permission →
                                 background → push → shade check → tap → app opened — all verified)
SECURITY_CHECK         = PASS   (git ls-files scan: no google-services.json, no service account JSON,
                                 no FCM tokens, no .env, no real key material (AIzaSy…/PRIVATE KEY:
                                 zero matches). Authorization headers redacted in Fastify logs; device
                                 token masked in mock logs and UI; debug receiver compiled into debug
                                 builds only)
README                 = PASS   (README + docs/FIREBASE_SETUP.md + docs/PAYLOAD.md + docs/NEXT_PHASE.md)
```

## FAILED_TESTS

None. Every test either PASSED or was BLOCKED for an environmental reason that
is documented above (Firebase not configured / no physical device attached).

## Additional verification performed

- Android unit tests: **16/16 PASS** (payload validator: required fields, schema
  guard, priority degradation, https-only url policy, truncation warnings; deduper
  LRU behavior).
- Server tests: **8/8 PASS** (validation errors, message_id override, https rule,
  missing-token rejection, bearer auth 401/200, mock mode health).
- Channels verified live via `dumpsys notification`: `pigeonhub_normal`
  (IMPORTANCE_DEFAULT=3) and `pigeonhub_high` (IMPORTANCE_HIGH=4) both created.
- In-app "Test push" buttons and the debug-only adb receiver exercise the exact
  production `PushPipeline` (validate → dedupe → inbox → render).
- Inbox empty state ("No notifications yet"), permission banner, Device/Debug
  screen and Settings screen all verified on the emulator.

## KNOWN_ISSUES

1. `composeBom` pinned to `2025.09.01` (Compose 1.9.x): newer BOMs (2026.06+)
   require compileSdk 37 + AGP 9.1+. Upgrade together with AGP in one step —
   noted in `gradle/libs.versions.toml`.
2. True end-to-end push (curl → FCM → device) waits on the owner's Firebase
   setup; the repo is prepared so this is a config-only change
   (docs/FIREBASE_SETUP.md: register `com.pigeonhub.app`, drop
   `google-services.json`, apply the plugin, add service account + token env).
3. Doze / force-stopped-wake semantics can only be validated with real FCM.
4. Git Bash + adb one-liners: extras with spaces must be wrapped so the device
   shell sees quotes (`adb shell "am broadcast … --es title 'My title'"`), or
   values arrive garbled. Documented here; not an app bug.
5. Emulator-only: a "System UI isn't responding" ANR dialog appeared once at
   first boot (emulator SystemUI, unrelated to PigeonHub).
6. Minor UX: when the inbox is scrolled, newly arrived entries insert above the
   viewport (LazyList key anchoring) — acceptable for MVP; revisit in MVP-001.
7. Inbox is process-lifetime memory by design; durable dedupe (512-id
   SharedPreferences LRU) already survives process death. Room swap point:
   `MessageDeduper` interface.

## COMMITS

```
5f9219d android: PigeonHub Android client (Compose, push pipeline, channels, FCM boundary)
8acad9b server: disposable local dev sender (Fastify + firebase-admin)
1aaa75f docs: README, Firebase setup guide, payload contract, next-phase notes
<final> docs: night-001 report   (this file)
```

Pushed to `https://github.com/hyukvoid/PigeonHub.git` branch `main`
(push succeeded; re-pushed after this report).

## FINAL_STATUS

**COMPLETE_WITH_BLOCKS** — every work item achievable without Firebase and
without a physical device was implemented and verified on the emulator, with
green builds and passing test suites. All Firebase/FCM-dependent acceptance
items are explicitly marked `BLOCKED_PENDING_FIREBASE_SETUP` (or
`BLOCKED_NO_ANDROID_DEVICE` for the physical-device preference) and are fully
prepared behind documented, config-only steps.

## NEXT (not started — bookmark only)

MVP-001 per docs/NEXT_PHASE.md: owner-run Firebase setup → real FCM E2E
(foreground/background/process-dead/doze matrix) → Cloudflare Workers + D1
device registry + private channels + write tokens → server-side message
persistence → Room-backed dedupe.
