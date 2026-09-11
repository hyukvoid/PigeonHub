-- MVP-003A: agent event metadata (nullable columns on messages — no new table).
-- Only populated when the publish body contains a valid agent_event object.
ALTER TABLE messages ADD COLUMN event_type TEXT;
ALTER TABLE messages ADD COLUMN provider TEXT;
ALTER TABLE messages ADD COLUMN run_id TEXT;
ALTER TABLE messages ADD COLUMN attention_reason TEXT;
ALTER TABLE messages ADD COLUMN facts_json TEXT;
CREATE INDEX IF NOT EXISTS idx_messages_run
  ON messages(channel_id, provider, run_id);
