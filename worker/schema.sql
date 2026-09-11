-- PigeonHub durable message core (MVP-001B). Minimal tables by design.
-- Device registry / channels / installations are later milestones; the
-- channel_id + (scope) columns are already shaped for that migration.

CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY,
  channel_id TEXT NOT NULL,
  seq INTEGER NOT NULL,
  title TEXT NOT NULL,
  message TEXT NOT NULL,
  priority TEXT NOT NULL,
  url TEXT,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  idempotency_key TEXT,
  request_hash TEXT NOT NULL,
  push_status TEXT NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  fcm_message_id TEXT
);

-- Cursor-friendly per-channel sequence (no gaps/collisions on insert:
-- seq is allocated inside the INSERT statement while D1 serializes writes;
-- this UNIQUE index is the backstop).
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_channel_seq
  ON messages(channel_id, seq);

-- Idempotency: at most one message per (channel, key). Partial index so
-- keyless messages stay independent.
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_channel_idem
  ON messages(channel_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Pending-message recovery scans (Cron/Queue is a later decision).
CREATE INDEX IF NOT EXISTS idx_messages_status
  ON messages(channel_id, push_status);

CREATE TABLE IF NOT EXISTS quota_buckets (
  scope TEXT NOT NULL,
  bucket_start TEXT NOT NULL,
  accepted_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (scope, bucket_start)
);
