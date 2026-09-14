-- MVP-011: server-side job progress coalescing state.
-- One row per (channel, source, job_id). Durable so correctness survives
-- worker restarts/isolate moves. Additive only.

CREATE TABLE IF NOT EXISTS job_states (
  channel_id TEXT NOT NULL,
  job_source TEXT NOT NULL,
  job_id TEXT NOT NULL,
  last_state TEXT NOT NULL,
  last_emitted_at TEXT NOT NULL,
  last_message TEXT,
  last_current INTEGER,
  last_total INTEGER,
  pending_current INTEGER,
  pending_total INTEGER,
  pending_message TEXT,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (channel_id, job_source, job_id)
);
