# PigeonHub push payload — schema_version 1

FCM messages are **data-only**. The client owns rendering, validation and
channel choice; a `notification` payload would bypass the pipeline and is not
used. All values travel as FCM data strings.

| Field | Required | Type / values | Rules |
| --- | --- | --- | --- |
| `message_id` | yes | string ≤ 256 | Unique per logical message. Server generates a UUID; pass it explicitly to replay/duplicate-test. Client dedupes on it. |
| `title` | yes | string 1–500 | Trimmed; longer input truncated with a warning (notification still renders). |
| `message` | yes | string 1–4000 | Trimmed; longer input truncated with a warning. |
| `priority` | no | `normal` \| `high` | Default `normal`. Chooses the Android channel (`pigeonhub_normal` / `pigeonhub_high`) and sets the FCM android priority. Unknown values degrade to `normal` with a client warning — they never drop the message. |
| `url` | no | absolute **https** URL | Dropped with a warning when http/invalid/malicious; the notification still renders. Tap opens the app, the Inbox entry shows the URL, "Open" launches an external browser. |
| `sent_at` | no | ISO-8601 string | Display-only. Server sets it when not supplied by the caller. |
| `schema_version` | no | integer ≥ 1 | Server sends `"1"`. Missing → treated as `1`. Greater than the client's supported version → payload rejected (never rendered as garbage). |

## Example

```json
{
  "message_id": "6f1c9b2e-4a1d-4c1f-9a3b-2f7d8e5a1b10",
  "title": "Build Complete",
  "message": "Deployment succeeded",
  "priority": "high",
  "url": "https://example.com/deploys/42",
  "sent_at": "2026-09-11T01:23:45.678Z",
  "schema_version": "1"
}
```

## Client processing order (fixed)

1. `PushPayloadValidator` — reject if required fields missing / schema unsupported.
2. `MessageDeduper` — drop repeated `message_id` (same session or SharedPreferences LRU, 512 entries).
3. `InboxStore` — record in the in-memory inbox.
4. `NotificationRenderer` — post to the channel matching `priority`.

The pipeline is synchronous and network-free, so it is safe inside
`FirebaseMessagingService.onMessageReceived`.
