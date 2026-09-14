-- MVP-015: per-connector connection health. One row per (channel, source).
-- The device never derives health from guessing: the worker records real
-- activity it observes (accepted publishes, webhooks, device syncs).
-- Additive only.

CREATE TABLE IF NOT EXISTS connector_health (
  channel_id TEXT NOT NULL,
  source TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  last_event_at TEXT,
  last_success_at TEXT,
  last_failure_at TEXT,
  last_failure_reason TEXT,
  PRIMARY KEY (channel_id, source)
);
