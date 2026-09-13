# MVP-005→010 — ARCHITECTURE

## Job Event contract (normalized, source-agnostic)

Published through the existing channel publish API as an OPTIONAL `job` object:

```json
POST /v1/channels/:id/messages
{
  "title": "…",            // required (compat): short human line, e.g. "Product crawler failed"
  "message": "…",          // required (compat): detail line, e.g. "HTTP 429 at 18,431/50,000"
  "priority": "normal|high",
  "url": "https://…",      // optional deep link (compat field)
  "job": {
    "source": "github|comfyui|cli|agent|…",   // required, <=32 chars, [a-z0-9_-]
    "job_id": "run-12345",                    // required, stable per job, <=128 chars
    "job_name": "Product crawler",            // optional, <=200 chars
    "state": "RUNNING|PROGRESS|DONE|FAILED|NEEDS_ACTION",  // required
    "started_at": "2026-09-14T01:00:00Z",     // optional ISO-8601
    "finished_at": "…",                        // optional ISO-8601
    "progress_current": 18431,                 // optional int >= 0
    "progress_total": 50000,                   // optional int >= 0
    "attention_reason": "approve migration",   // optional, <=300 chars
    "result_summary": "3 videos, 1.2 GB",      // optional, <=300 chars
    "deep_link": "https://…"                   // optional https
  }
}
```

Notes:
- `PROGRESS` is an event state (rendered as "RUNNING · n/m"), not a job phase. Job phase
  displayed on the phone: RUNNING (incl. PROGRESS events), DONE, FAILED, NEEDS_ACTION.
- Unstructured pushes (no `job`) are 100% unchanged — OLD_MESSAGE_COMPAT.
- Same `(source, job_id)` events are separate durable message rows (sync-safe) but are
  coalesced into ONE Job Card on the phone and gated by ONE delivery policy.

## Server

- `schema_008.sql`: additive `ALTER TABLE messages ADD COLUMN job_*` + `pairing_codes` table.
- Publish handler: validates `job`, stores columns, includes job fields in the FCM data payload.
- Sync endpoint: returns job columns per message.
- GitHub webhook (`workflow_run.completed`): synthesizes the first structured source —
  source=`github`, job_id=`run-<id>`, state= success→DONE / failure→FAILED (others→DONE with
  conclusion in result), started_at=run started_at, finished_at=now, deep_link=run URL.
- Pairing (MVP-007): management-auth `POST /v1/installations/me/pairing-codes` issues
  short-lived (10 min), one-time, revocable codes; `POST /v1/pairing/redeem` exchanges a code
  for channel endpoint + fresh write token. Codes live in D1 with TTL; revocation = DELETE row.

## Android

- Room v4→v5: `inbox_messages` gains nullable `job_*` columns (additive migration).
- `PushPayloadValidator` parses job fields (schema stays "1"; job fields optional).
- Policy (in PushPipeline render step): job RUNNING/PROGRESS → persist only;
  DONE → normal-channel notification; FAILED/NEEDS_ACTION → high-channel notification;
  dedupe per `(source, job_id, state)` via existing MessageDeduper + a job-state gate.
- HomeScreen groups consecutive messages with the same `(job_source, job_id)` into one
  JobCard (latest state wins); unstructured messages render exactly as before.
