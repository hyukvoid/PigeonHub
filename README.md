# PigeonHub

Push notifications for developers, straight from a `curl` request to your pocket.

```
HTTP request ──▶ dev sender (Fastify) ──▶ Firebase Cloud Messaging ──▶ Android device ──▶ PigeonHub notification ──▶ Inbox
```

**Status: FCM registration LIVE, real send pending one credential.** The
Android client builds and runs with the dedicated Firebase project
(`pigeonhub-b958d`, package `com.pigeonhub.app`): the Device tab fetches a real
FCM registration token. The only remaining step for true end-to-end push is the
owner-provided service account JSON for the dev sender
(see [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) — until then `/push`
runs in mock mode). The client pipeline is also fully exercisable locally
(in-app test push + adb injector).

## Repository layout

| Path | What it is |
| --- | --- |
| `android/` | Android app (Kotlin, Jetpack Compose, Material 3, FCM) |
| `worker/` | **Production transport**: Cloudflare Worker → D1 durable accept (idempotency, quota, seq) → OAuth2 (RS256 JWT) → FCM HTTP v1 (Workers/D1 **Free** plan) |
| `server/` | Local dev sender (Fastify + firebase-admin) — kept as a regression/reference tool |
| `docs/` | Setup guides, payload contract, session reports |
| `pigeonhub/` | MVP-016 user-facing CLI: login, lifecycle wrapper, progress, notifications |

> **Backend direction (decided):** production is **Cloudflare Workers + D1 + FCM**.
> **Private installations are live**: a fresh Android install generates its own
> management secret + write token (Keystore-encrypted, persisted before any
> network request), bootstraps with a single-use invite code, and gets a private
> publish endpoint. Copy cURL from the app and push yourself — no FCM token
> handling, no server-issued secrets. Reports:
> [mvp-001b-d1-durable-core](docs/mvp-001b-d1-durable-core-report.md) ·
> [mvp-001c-retry-safe-bootstrap](docs/mvp-001c-retry-safe-bootstrap-report.md)

### PigeonHub CLI (MVP-016)

The normal path is a declared command, not a background daemon:

```bash
python -m pip install -e .
pigeonhub login
pigeonhub run --name "Product crawler" -- python crawler.py
```

`pigeonhub login` creates a short-lived PC login request, displays a QR in the
terminal, waits for the Android user to scan and approve it, and stores only
the connector credential locally. The old `pair --code` / `--qr-image` path is
retained for protocol compatibility.

Inside a running command, the inherited job context makes lifecycle updates
possible without a vendor-specific connector:

```bash
pigeonhub progress 42 100
pigeonhub needs-action "Please choose the output folder"
pigeonhub notify "Build note" "The cache was warmed"
```

`run` publishes `RUNNING` before starting the child. If that publish cannot be
accepted, the child is not started. The child receives `PIGEONHUB_JOB_ID`, and
its stdout, stderr, and exit code are preserved. `logout` removes the local
credential; `status` reports local login and worker reachability.

Windows single-executable packaging and installer/PATH work remain MVP-018.

### Worker quick start

```bash
cd worker
npm install
npx wrangler secret put FIREBASE_CLIENT_EMAIL     # service account client_email
npx wrangler secret put FIREBASE_PRIVATE_KEY      # PEM or base64(PEM)
npx wrangler secret put FCM_TEST_DEVICE_TOKEN     # from the app Device tab
npx wrangler secret put PUSH_BEARER_SECRET        # any random dev bearer
npx wrangler deploy                               # https://<name>.<subdomain>.workers.dev
```

```bash
curl -X POST https://pigeonhub-push.pigeonhub.workers.dev/push \
  -H "Authorization: Bearer $PUSH_BEARER_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"title":"Build Complete","message":"Deployment succeeded","priority":"high","url":"https://example.com"}'
```

Local development: put the same four values in `worker/.dev.vars`
(gitignored) and run `npx wrangler dev`. Responses carry a non-secret
`X-PigeonHub-Auth: fresh|cache` header for cold/warm OAuth observation.

## Android app

- `applicationId` is **fixed**: `com.pigeonhub.app`. Never change it to match an
  existing Firebase project.
- Single module, no Hilt/Room/WorkManager — deliberately boring so it builds.
- Push pipeline (`android/app/src/main/java/com/pigeonhub/app/push/`):
  `PushPayloadValidator` → `MessageDeduper` (in-memory + SharedPreferences LRU,
  Room-swap-ready interface) → `InboxStore` → `NotificationRenderer`.
- Two notification channels: **PigeonHub Normal** (importance default) and
  **PigeonHub High** (importance high / heads-up capable). The sender's
  `priority` only chooses the channel; Android importance stays user-controlled.
- FCM boundary: `PigeonMessagingService` is real code behind a guard
  (`FirebaseGate`). Without Firebase config the app runs normally and the
  Device tab reports `BLOCKED_PENDING_FIREBASE_SETUP`.
- UI: **Inbox** (in-memory list, empty state "No notifications yet"),
  **Device** (FCM status, permission, channels, test push buttons, adb recipe),
  **Settings** (permission + system settings + about).

### Build

```bash
cd android
./gradlew :app:assembleDebug        # or: gradle :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

No Firebase config is required to build.

### Test a push without Firebase

Open the app → **Device** tab → *Test push* buttons, or from adb (cold process):

```bash
adb shell am broadcast -a app.pigeonhub.debug.TEST_PUSH \
  -n com.pigeonhub.app/.debug.TestPushReceiver \
  --es title "Build Complete" --es message "Deployment succeeded" \
  --es priority high --es url https://example.com \
  --es message_id "fixed-id-1"
```

## Local dev sender (server/)

Node.js LTS ≥ 20, npm.

```bash
cd server
npm install
cp .env.example .env     # FCM_DEVICE_TOKEN can be any placeholder in mock mode
npm start                # http://127.0.0.1:8787
```

- Without a service account it starts in **mock** mode: every request is
  validated and the exact FCM payload is logged, nothing reaches a device.
- With `FIREBASE_SERVICE_ACCOUNT_PATH` set it sends for real. See
  [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) and `server/.env.example`.

```bash
# health: shows transport mode
curl http://localhost:8787/health

# push (mock mode logs it; fcm mode delivers to the device)
curl -X POST http://localhost:8787/push \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Build Complete",
    "message": "Deployment succeeded",
    "priority": "high",
    "url": "https://example.com"
  }'
```

Response:

```json
{
  "ok": true,
  "mode": "mock",
  "message_id": "6f1c9...",
  "sent_at": "2026-09-11T01:23:45.678Z",
  "priority": "high",
  "fcm_message_id": "mock/6f1c9..."
}
```

Replay the same `message_id` (pass `"message_id": "..."` in the body) to verify
the device-side duplicate suppression.

## Payload contract (v1)

Data-only FCM message; exact field rules in [docs/PAYLOAD.md](docs/PAYLOAD.md).
Required: `message_id`, `title`, `message`. Optional: `priority`
(`normal`/`high`), `url` (**https only**), `sent_at`, `schema_version`.

## Security rules enforced in this repo

- No `google-services.json`, no Firebase service account JSON, no FCM tokens,
  no `.env` files are ever committed (see `.gitignore`).
- The Android app never embeds Firebase admin credentials.
- The sender redacts `Authorization` headers from logs and masks device tokens
  in mock output.
- The debug screen never displays the full FCM token (masked display + explicit
  copy action only).

## Next milestone (not started)

MVP-001: Firebase Anonymous Auth, Cloudflare D1 device registry, private
channels with write tokens, server-side message persistence. See
[docs/NEXT_PHASE.md](docs/NEXT_PHASE.md).
