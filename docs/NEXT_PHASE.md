# NEXT PHASE (recorded, NOT started in night-001)

Night-001 deliberately stops before all of the following. This file is a
bookmark, not a task list for tonight.

## Decided production architecture

```
client (Android)          edge/backend                    messaging
┌────────────────┐        ┌─────────────────────────┐     ┌─────────┐
│ PigeonHub app  │ ◀───── │ Cloudflare Workers      │ ──▶ │ FCM     │
│ (FCM token)    │        │ Cloudflare D1 (registry)│     │         │
└────────────────┘        └─────────────────────────┘     └─────────┘
```

- `PAID_INFRA_ALLOWED = false` — Workers free tier + D1 free tier + FCM.
- The Fastify dev sender in `server/` is disposable and will be retired once
  the Worker exists.

## MVP-001 (next milestone)

1. **Firebase integration** (owner-run; see docs/FIREBASE_SETUP.md)
   - dedicated Firebase project, `google-services.json` for `com.pigeonhub.app`
   - real FCM delivery: foreground / background / process-not-running / doze
2. **Firebase Anonymous Auth** — device identity without passwords.
3. **Cloudflare D1 device registry** — replace the SharedPreferences stop-gaps:
   - device registration (anon uid ↔ FCM token)
   - private channel table
   - per-channel **write token** so `curl` stays the primary interface
4. **Server-side message persistence** — D1 message log for an inbox that
   survives reinstall/reinstall of the app (replaces the in-memory InboxStore).
5. **Room-based dedupe** — swap `SharedPreferencesMessageDeduper` for a Room
   implementation of the same `MessageDeduper` interface (interface already in
   place; no other code changes expected).
6. **Cloudflare Queues** — only if delivery retry/decoupling is actually needed.

## Explicitly NOT in MVP-001

User accounts beyond anonymous auth, web dashboard, iOS, multi-device fan-out
UI, attachments/images, search, analytics, payments, self-hosting, WebSocket /
SSE / MQTT transports.
