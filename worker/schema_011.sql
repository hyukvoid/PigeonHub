-- MVP-010.5: durable deletion tombstones. A deleted message id must never
-- reappear on any device: the sync query excludes tombstoned ids and returns
-- the tombstone list so other devices converge. Additive only.

CREATE TABLE IF NOT EXISTS deleted_messages (
  channel_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  deleted_at TEXT NOT NULL,
  PRIMARY KEY (channel_id, message_id)
);
CREATE INDEX IF NOT EXISTS idx_deleted_channel_time
  ON deleted_messages(channel_id, deleted_at);
