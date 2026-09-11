# MVP-001a Report — Real FCM End-to-End (curl → Fastify → Firebase Admin → FCM → Android)

Date: 2026-09-11
Precondition carried over from night-001: dedicated Firebase project
(`pigeonhub-b958d`), Android app registered as `com.pigeonhub.app`,
google-services plugin active, app fetches a real FCM registration token.
No database, no Cloudflare, no Auth, no Room — untouched, per scope.

Delivery chain verified: `curl → Fastify dev sender (mode=fcm) → Firebase
Admin SDK → FCM → Android Emulator (Medium_Phone_API_36.1) → PigeonHub
system notification → PigeonHub Inbox`. Every matrix item below used REAL
Firebase Cloud Messaging network delivery — the night-001 adb debug injector
results were not reused anywhere.

```
SECRET_TRACKING_CHECK        = PASS
FIREBASE_ADMIN_AUTH          = PASS  (server /health: mode=fcm, firebaseConfigured=true)
FCM_REGISTRATION             = PASS  (unchanged from previous phase; live device token)
REAL_FCM_SEND                = PASS  (server returned real FCM message ids
                                      "projects/pigeonhub-b958d/messages/0:…")
FCM_TO_ANDROID               = PASS  (app logcat: [fcm] delivered push … for every case)
SYSTEM_NOTIFICATION          = PASS  (dumpsys: NotificationRecord on the correct channel)
FOREGROUND                   = PASS  (heads-up channel pigeonhub_high, importance 4)
BACKGROUND_FCM               = PASS  (delivered with app backgrounded)
NOTIFICATION_TAP             = PASS  (tap → MainActivity resumed, entry highlighted
                                      "Opened from notification")
CLICK_URL (HTTPS)            = PASS  (entry → Open → Chrome navigated to the https url;
                                      https-only rule unchanged)
DEDUP_REAL_FCM               = PASS  (same message_id twice over real FCM:
                                      1st delivered, 2nd "duplicate push suppressed",
                                      exactly one notification)
HIGH_PRIORITY                = PASS  (all high sends landed on channel pigeonhub_high)
NORMAL_PRIORITY              = PASS  (normal sends landed on channel pigeonhub_normal)
PROCESS_NOT_RUNNING_REAL_FCM = PASS  (process killed via `am kill` ≙ swipe-from-Recents;
                                      HIGH message WOKE the process: pid empty → new pid,
                                      delivered + notification posted)
SCREEN_OFF_REAL_FCM          = PASS  (mWakefulness=Asleep; HIGH delivered + posted)
DOZE_HIGH_REAL_FCM           = PASS  (force-idle + unplugged + screen off → IDLE;
                                      HIGH delivered while IDLE)
DOZE_NORMAL_REAL_FCM         = PASS  (normal message DEFERRED while IDLE — zero
                                      delivery for 15s+ — then delivered right after
                                      deviceidle returned to ACTIVE; matches FCM doze
                                      semantics for normal priority)
FORCE_STOP (negative)        = CONFIRMED EXPECTED FAILURE
                                      (force-stop → HIGH real FCM → no delivery, as
                                      documented for stopped apps; NOT counted as a
                                      test failure and app was restarted afterwards)
```

## Secret management state (this phase's first goal)

- `.gitignore` replaced with the agreed policy: `!debug.keystore` exception
  REMOVED; `**/google-services.json`, all `*firebase-adminsdk*` /
  `*service-account*` / `*service_account*` patterns, `.env` (except
  `!.env.example`), `server/secrets/`, `android/secrets/`, `*.pem`, `*.p12`,
  `*.pfx`, `*.key`, keystores — all excluded.
- Tracked-file scan (PowerShell `git ls-files | Select-String …`): the only
  match is `server/.env.example`, which is the committed EMPTY template
  (`!.env.example` exception is intentional). No real secret file is tracked;
  nothing needed `git rm --cached`.
- The Firebase Admin service-account JSON lives OUTSIDE the repo
  (`~/.secrets/…firebase-adminsdk….json`, owner-provided) and was never copied
  into the repository. Server resolves it via `FIREBASE_SERVICE_ACCOUNT_PATH`
  in `server/.env` (absolute path only — no key material).
- `server/.env` holds the real device FCM token; it is gitignored (verified
  with `git check-ignore`), not tracked, and its value was never printed to
  terminal output or written into any report. The token also stays masked in
  the app UI and in server logs (unchanged redaction rules).
- Dev-sender change enabling this: a tiny dependency-free `.env` loader
  (`server/src/dotenv.ts`) was added so `npm start` picks up `server/.env`
  automatically. Existing non-empty environment variables still win.

## Test environment notes

- Emulator runs in windowed GUI mode (restarted without `-no-window`); one
  hung qemu launch was killed and cold-booted with `-no-snapshot` — no data
  wipe, app + settings preserved.
- "Recent Apps에서 앱 제거" was approximated with `adb shell am kill` after
  backgrounding (same effect: background process death without stopped-state).
  True FCM wake-up was still exercised on a genuinely dead process.
- Doze entry: `dumpsys battery unplug` + `dumpsys deviceidle force-idle`;
  exit: `dumpsys deviceidle step` back to ACTIVE + `dumpsys battery reset`.

## FAILED_TESTS

None.

## KNOWN_ISSUES

1. Normal-priority messages under doze are deferred by FCM (verified; this is
   platform behaviour, not a bug — use `priority: "high"` for urgent pushes).
2. Force-stopped apps receive nothing until the user re-opens the app
   (platform behaviour; documented as a negative test).
3. The device token rotates on app data loss; if pushes start failing with
   "not found", re-copy the token from the Device tab into `server/.env`.

## COMMITS

```
<this commit> chore: gitignore secret policy + server .env loader + mvp-001a report
```

Pushed to `https://github.com/hyukvoid/PigeonHub.git` branch `main`.

## FINAL_STATUS

**REAL FCM E2E = PASS.** All required conditions PASS:
SECRET_TRACKING_CHECK, FIREBASE_ADMIN_AUTH, FCM_REGISTRATION, REAL_FCM_SEND,
FCM_TO_ANDROID, SYSTEM_NOTIFICATION, BACKGROUND_FCM, NOTIFICATION_TAP — plus
PROCESS_NOT_RUNNING, SCREEN_OFF, DOZE_HIGH, DOZE_NORMAL, DEDUP over real FCM.

Night-001's `BLOCKED_PENDING_FIREBASE_SETUP` items are now resolved with real
Firebase Cloud Messaging. Next milestone per docs/NEXT_PHASE.md (Cloudflare
Workers + D1 device registry) remains NOT started, as instructed.
