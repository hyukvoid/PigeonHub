-- MVP-007: connector pairing. Short-lived (10 min), one-time, revocable
-- pairing codes; redemption mints a channel-scoped connector token so the
-- plaintext token is never stored server-side and existing device tokens are
-- never rotated (multi-device fan-out stays intact).

CREATE TABLE IF NOT EXISTS pairing_codes (
  code_hash TEXT PRIMARY KEY,
  installation_id TEXT NOT NULL,
  channel_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  redeemed_at TEXT,
  revoked INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pairing_installation
  ON pairing_codes(installation_id, created_at);

CREATE TABLE IF NOT EXISTS connector_tokens (
  channel_id TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY (channel_id, token_hash)
);
