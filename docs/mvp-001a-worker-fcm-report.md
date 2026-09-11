# MVP-001A Report — Cloudflare Worker → FCM Production Transport Gate

Date: 2026-09-11
Scope: prove that a **Cloudflare Workers Free plan** Worker can act as
PigeonHub's production FCM sender. No D1, no Queues, no Auth, no Room, no
registry — explicitly out of scope (NEXT_PHASE stays frozen).

Production transport verified end to end:

```
curl
  ↓  https://pigeonhub-push.pigeonhub.workers.dev/push   (bearer secret)
Cloudflare Worker (workerd, Free plan)
  ↓  RS256 JWT signed with WebCrypto (service account key from Worker Secrets)
  ↓  POST oauth2.googleapis.com/token  (jwt-bearer grant)  → access token
  ↓  POST fcm.googleapis.com/v1/projects/pigeonhub-b958d/messages:send (data-only)
Android PigeonHub (com.pigeonhub.app, unchanged PushPipeline)
  ↓  validate → dedupe → inbox → channel render
System Notification  (pigeonhub_normal / pigeonhub_high)
```

## Result block

```
CLOUDFLARE_PLAN          = FREE   (new owner account; no plan upgrade performed)
PAID_RESOURCE_CREATED    = NO

WORKER_BUILD             = PASS   (tsc strict clean; wrangler 4.131.0)
WORKER_DEPLOY            = PASS   (wrangler deploy; required one-time owner actions:
                                  OAuth login + account email verification)
WORKER_URL               = https://pigeonhub-push.pigeonhub.workers.dev

JWT_SIGNING              = PASS   (WebCrypto RSASSA-PKCS1-v1_5/SHA-256, pkcs8 import)
OAUTH_ACCESS_TOKEN       = PASS   (jwt-bearer exchange; token cached in isolate
                                  memory, refreshed 60s before expiry, in-flight
                                  shared; tokens never logged)
FCM_HTTP_V1              = PASS   (data-only message; android.priority HIGH/NORMAL)

COLD_AUTH_TEST           = PASS   (first request on a cold isolate performed the full
                                  JWT→OAuth→FCM path; X-PigeonHub-Auth: fresh)
WARM_AUTH_TEST           = PASS   (subsequent request served from isolate cache;
                                  X-PigeonHub-Auth: cache)

WORKER_TO_FCM            = PASS   (real fcm_message_id returned:
                                  projects/pigeonhub-b958d/messages/0:…)
FCM_TO_ANDROID           = PASS   (app logcat: [fcm] delivered push … for every case)
SYSTEM_NOTIFICATION      = PASS   (dumpsys: NotificationRecord on the correct channel)

HIGH_PRIORITY            = PASS   (channel pigeonhub_high, importance 4)
NORMAL_PRIORITY          = PASS   (channel pigeonhub_normal, importance 3)
NOTIFICATION_TAP         = PASS   (production Worker notification tapped from the
                                  shade → app foreground → entry highlighted
                                  "Opened from notification")
CLICK_URL                = PASS   (entry "Open" → ACTION_VIEW https url → Chrome)
INVALID_AUTH             = PASS   (wrong bearer → 401; missing header → 401;
                                  rejected before any Google call)
INVALID_PAYLOAD          = PASS   (missing title / bad priority / http url → 400 with
                                  field errors; rejected before OAuth/FCM)

SECRET_CHECK             = PASS   (see below)

CPU_LIMIT_FAILURES       = 0      (10/10 sampling requests HTTP 200; no CPU-limit
                                  termination, no Worker exceptions observed)
TEST_COUNT               = 10 formal samples + cold/warm/error matrix above
```

## Free-tier observation (PHASE L)

- Sampling: 10 production requests spaced ~12 s, mixed normal/high.
  Outcome: 4 responses with `X-PigeonHub-Auth: fresh` (that isolate performed a
  complete JWT sign + OAuth exchange) and 6 with `cache`; **0 failures**.
- Cold path (JWT sign ≈ few ms of CPU + network RTT) completed without any
  sign of the Free CPU limit; wall-clock latency is dominated by the two
  Google round trips, which is expected and not CPU time.
- Additional cold exchanges were also observed working from local `wrangler
  dev` (workerd) during development. Total successful cold OAuth exchanges
  observed today: ≥ 6 (4 production samples + local runs).

## Test matrix (PHASE K)

| Test | Result | Evidence |
| --- | --- | --- |
| 1 Cold Auth | PASS | fresh header + real FCM message id on first production call |
| 2 Warm Auth | PASS | cache header on follow-up calls |
| 3 High Priority | PASS | pigeonhub_high notification ("Worker PROD Cold") |
| 4 Normal Priority | PASS | pigeonhub_normal ("Worker Warm", samples) |
| 5 Notification Tap | PASS | tap → MainActivity + highlighted entry |
| 6 HTTPS URL | PASS | https://example.com/prod row → Open → Chrome |
| 7 Invalid Auth | PASS | 401 both wrong-bearer and no-header |
| 8 Invalid Payload | PASS | 400 with errors, zero Google-side calls |

## Secret management (PHASE E)

- Service account JSON stayed OUTSIDE the repo (`~/.secrets/`, owner file).
  Only three values were extracted into Worker Secrets:
  `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY` (base64 transport — the
  Worker accepts base64 or raw PEM), `FCM_TEST_DEVICE_TOKEN` (device token,
  from the app's Device tab via the local app prefs), plus
  `PUSH_BEARER_SECRET` (random dev bearer, generated locally).
- Deployed secrets were set with `wrangler secret put` (stdin); local dev uses
  `worker/.dev.vars`. Both are gitignored; verified via `git check-ignore`.
- No secret, key, token or bearer value appears in terminal output, Git
  history, or this report. `.dev.vars`-style secrets are read at request time
  via env bindings only.
- Worker auth: `POST /push` requires `Authorization: Bearer
  <PUSH_BEARER_SECRET>` (development gate — explicitly NOT the future
  per-channel write-token design).

## Blockers encountered (resolved during the session)

- Cloudflare account was not logged in → `wrangler login` (owner browser
  approval, second attempt).
- New account had no workers.dev subdomain → registered `pigeonhub` via the
  Workers subdomain API (free feature).
- Error 10034: account email verification was required before first deploy →
  owner clicked the verification mail link; deploy then succeeded.
- `npm install` peer conflict → `@cloudflare/workers-types` bumped to ^5.

None of these remained as FAIL/BLOCKED at session end.

## KNOWN_ISSUES

1. `FIREBASE_PROJECT_ID` is a plaintext var in `wrangler.jsonc` (it appears in
   the public FCM URL anyway; not a secret).
2. Device targeting uses `FCM_TEST_DEVICE_TOKEN` — a registry (D1) is the next
   milestone; until then the Worker pushes to the single enrolled test device
   (or an explicit `{token}` override in the request body).
3. Isolate-cache observation is probabilistic: `fresh` on a request means that
   isolate was cold/expired, not that the cache is broken.
4. Minor client UX (carried from night-001): newly inserted inbox entries can
   appear above the scroll anchor.

## COMMITS

```
<this commit> worker: Cloudflare Worker → FCM production transport (MVP-001A)
```

Pushed to `https://github.com/hyukvoid/PigeonHub.git` branch `main` after
secret scan PASS.

## FINAL_VERDICT

**MVP_001A = PASS**

Cloudflare Workers **Free plan** reliably performs the production transport
(RS256 JWT → OAuth2 → FCM HTTP v1 → Android system notification), including
cold-isolate auth, with zero CPU-limit failures. The local Fastify sender
remains as a regression/reference tool; the Worker (`worker/`) is the
production path. D1 / durable message core (MVP-001B) is the next milestone
and was NOT started, per scope.
