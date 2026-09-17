-- MVP-016: PC-first login requests. The QR carries request_id + challenge;
-- the poll secret is retained only by the PC process and is hashed here.
CREATE TABLE IF NOT EXISTS pairing_requests (
  request_id TEXT PRIMARY KEY,
  challenge_hash TEXT NOT NULL,
  poll_secret_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  approved_at TEXT,
  consumed_at TEXT,
  installation_id TEXT,
  channel_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_pairing_requests_expiry
  ON pairing_requests(expires_at);
