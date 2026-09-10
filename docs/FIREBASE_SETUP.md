# Firebase setup for PigeonHub (post night-001)

Night-001 ships **without** Firebase configuration. Everything Firebase-related
is intentionally `BLOCKED_PENDING_FIREBASE_SETUP`. This document is the exact
checklist the project owner runs afterwards.

Rules (night-001 constraints that stay in force):

- Use a Firebase project that is **dedicated to PigeonHub** (a brand-new one is
  cheapest to reason about). Do **not** reuse `worldwar-e651c`,
  `com.globalwar.command`, `focuswave-70ae4`, or any config file copied from
  another project.
- `applicationId` is fixed to **`com.pigeonhub.app`** — register the Android app
  in Firebase with exactly this package name. Do not change the app to match an
  existing Firebase app.
- Never commit `google-services.json` or service account keys. `.gitignore`
  already blocks them.

## 1. Android app: google-services.json

1. Firebase Console → *Add project* (or your dedicated PigeonHub project).
2. Project settings → *Your apps* → **Add app → Android**.
3. Package name: `com.pigeonhub.app`. Nickname: PigeonHub. No SHA-1 needed for
   FCM.
4. Download `google-services.json` and save it to `android/app/google-services.json`.

### 5. Apply the google-services Gradle plugin

The repo builds **on purpose** without the plugin (it hard-fails when the JSON
is missing). Once the file exists, enable it:

1. `android/gradle/libs.versions.toml` — add under `[plugins]`:
   ```toml
   google-services = { id = "com.google.gms.google-services", version = "4.4.4" }
   ```
2. `android/build.gradle.kts` — add `alias(libs.plugins.google.services) apply false`.
3. `android/app/build.gradle.kts` — add `alias(libs.plugins.google.services)`
   to the `plugins {}` block (the `// NOTE:` comment marks the spot).
4. `cd android && ./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

### 6. Verify registration

1. Launch PigeonHub → **Device** tab. The FCM card should no longer say
   `BLOCKED_PENDING_FIREBASE_SETUP`.
2. Tap **Check FCM registration** → a masked token appears (e.g.
   `cVh8xJ2Q…aF4 (163 chars)`).
3. Tap **Copy token** → you will paste it into the sender env in step 2 below.

## 2. Sender: service account + device token

1. Firebase Console → Project settings → **Service accounts** →
   *Generate new private key* → save the JSON **outside the repo**, e.g.
   `server/secrets/serviceAccount.json` (`server/secrets/` is gitignored).
2. `cd server && cp .env.example .env`, then set:
   ```ini
   FCM_DEVICE_TOKEN=<paste the token copied from the Device tab>
   FIREBASE_SERVICE_ACCOUNT_PATH=secrets/serviceAccount.json
   ```
3. `npm start` — the startup log must say `FCM transport: real Firebase Admin SDK`.
4. Smoke test from any terminal:

```bash
curl -X POST http://localhost:8787/push \
  -H "Content-Type: application/json" \
  -d '{"title":"Hello PigeonHub","message":"First real FCM push","priority":"high"}'
```

A notification should land on the device within a second or two.

Optional hardening: set `PUSH_AUTH_TOKEN` in `.env` and send
`-H "Authorization: Bearer <token>"` on `/push`.

## 3. Device test matrix (rerun with real FCM)

1. App foreground (heads-up for `high`)
2. App background (shade notification)
3. Swipe app from Recent Apps (process dead → FCM wake-up)
4. Screen OFF
5. `priority: normal` vs `high` (channel + heads-up behaviour)
6. Same `message_id` twice (duplicate suppressed, single notification)
7. Tap notification → app opens, entry highlighted
8. `url` payload → tap-through to browser (https only)
9. Doze (adb: `dumpsys deviceidle force-idle`, then send `high`)
10. Negative: Settings → Force Stop, then send — delivery may be blocked by the
    OS until the app is started again; do not treat as a bug.

## 4. Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| Build fails with `File google-services.json is missing` | JSON not at `android/app/google-services.json` or plugin applied before the file exists |
| `BLOCKED_PENDING_FIREBASE_SETUP` still shown | JSON package mismatch or stale install — uninstall, rebuild, reinstall |
| `Requested entity was not found` on send | Token stale (app reinstalled) — re-copy from Device tab |
| ` Sender message id ... SenderId mismatch` | google-services.json from a different project than the service account — keep one dedicated project |
| Mock mode although credential set | `FIREBASE_SERVICE_ACCOUNT_PATH` typo / path not resolvable from `server/` |
