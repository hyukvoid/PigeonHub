-- MVP-001D: retention watermark placeholder on channels.
-- Physical deletion stays out of scope (MVP-001E); the floor stays 0 until then,
-- but the contract (retention_floor_seq / history_truncated) is already served.
ALTER TABLE channels ADD COLUMN retention_floor_seq INTEGER NOT NULL DEFAULT 0;
