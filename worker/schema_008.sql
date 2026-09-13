-- MVP-005: optional structured Job layer over the existing message core.
-- ADDITIVE ONLY: every column is nullable; unstructured messages keep working
-- unchanged (OLD_MESSAGE_COMPAT). No destructive migration.

ALTER TABLE messages ADD COLUMN job_source TEXT;
ALTER TABLE messages ADD COLUMN job_id TEXT;
ALTER TABLE messages ADD COLUMN job_name TEXT;
ALTER TABLE messages ADD COLUMN job_state TEXT;
ALTER TABLE messages ADD COLUMN job_started_at TEXT;
ALTER TABLE messages ADD COLUMN job_finished_at TEXT;
ALTER TABLE messages ADD COLUMN job_progress_current INTEGER;
ALTER TABLE messages ADD COLUMN job_progress_total INTEGER;
ALTER TABLE messages ADD COLUMN job_attention_reason TEXT;
ALTER TABLE messages ADD COLUMN job_result_summary TEXT;
ALTER TABLE messages ADD COLUMN job_deep_link TEXT;

-- Job event fan-in: "latest events for this job" lookups and future cleanup.
CREATE INDEX IF NOT EXISTS idx_messages_job
  ON messages(channel_id, job_source, job_id, seq);
