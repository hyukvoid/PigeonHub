# ARCHITECTURE — state after MVP-011.5 → MVP-015

## Product shape
Start it. Walk away. PigeonHub tells you when it matters.

```
 Long-running sources                          One phone
┌──────────────┐   ┌───────────────┐        ┌──────────────────┐
│ CLI / Python │   │ ComfyUI       │        │ Android app      │
│ connector    │   │ connector     │        │  Job Inbox       │
│ (pigeonhub_  │   │ (submit/watch)│        │  relative time   │
│  connector)  │   │               │        │  durable delete  │
└──────┬───────┘   └──────┬────────┘        └────────▲─────────┘
       │  one-time code   │                          │
       │  (QR on phone)   │                          │ FCM data pushes
       ▼                  ▼                          │ (HIGH for terminal,
┌─────────────────────────────────────────┐            │  inbox-only otherwise)
│  Cloudflare Worker  pigeonhub-push      │────────────┤
│                                         │            │
│  POST /v1/channels/:id/messages         │            │
│   ├ job validation (jobs.ts)            │            │
│   ├ PROGRESS coalescing (job_coalescing)│            │
│   ├ health tracking (health.ts) NEW     │            │
│   └ durablePublish → D1 + FCM           │            │
│  POST /v1/webhooks/github (fan-out)  ───┤────────────┘
│  POST /v1/pairing/redeem (one-time)     │
│  GET  /v1/installations/me/messages     │  ← sync: tombstone filter
│  POST /v1/installations/me/messages/    │     + deleted_ids convergence
│      delete (tombstone transaction) NEW │
│  GET  /v1/installations/me/health    NEW│
└──────────────────┬──────────────────────┘
                   ▼
        Cloudflare D1 (pigeonhub-messages)
        messages · job_states · deleted_messages (011)
        connector_health (012) · pairing_codes · connector_tokens
```

## What this campaign added (no rewrites of verified pipelines)

| Layer | Addition | Why it fits the north star |
|-------|----------|---------------------------|
| Worker | `deleted_messages` tombstones + `deleted_ids` in sync | delete once → gone everywhere, forever |
| Worker | `connector_health` + `/me/health` | "why didn't I get notified?" answered without logs |
| Worker | health hooks on publish/webhook/sync | worker-observed truth, no guessing |
| Connector | `comfyui_connector.py` (submit/watch) | real ComfyUI lifecycle → same job contract |
| Connector | `agent_bridge_codex.py` | real CLI agent sessions → same job contract |
| Connector | `pair --qr-image` / `--code` | pairing without typing long codes |
| Android | live relative time (RelativeTime + TickingNow) | never-frozen "3 minutes ago" |
| Android | swipe delete + delete-all + tombstone convergence | inbox hygiene that survives sync |
| Android | QR render (QrCode.kt, zxing core) | pairing UX; short-lived code only |
| Android | HealthLine on Connections cards | state dot + last activity, honest states |

## Deliberate invariants kept
- One FCM pipeline, one job contract, one coalescer — ComfyUI/agents/CLI all
  publish the same events; no source-specific server logic.
- Deletion is server-first: tombstone insert + row delete in ONE D1 batch
  (`INSERT OR IGNORE … json_each` — D1 cannot bind nested arrays as VALUES rows
  nor parse `INSERT…SELECT…ON CONFLICT` without WHERE). Local rows are removed
  only after the server ack; sync never resurfaces tombstoned ids.
- Relative labels are never persisted: timestamp + `rememberTickingNow()` (60s
  cadence while resumed, instant on fg return). Elapsed durations clamp at zero
  (server clock ahead of device must never render as "in 40 sec").
- Pairing QR carries the one-time short-lived code only — a leaked screenshot
  expires within 10 minutes and is single-use anyway.
- Health states are coarse on purpose: CONNECTED ≤24h, DEGRADED ≤72h, UNKNOWN
  never-seen, DISCONNECTED reserved — rare-event sources never read as broken.
