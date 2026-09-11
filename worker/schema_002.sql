-- MVP-001C: installations + private channels. Existing tables stay untouched.
-- Secrets are never stored raw: management/write credentials as SHA-256 hex,
-- FCM token as AES-GCM ciphertext (key = Worker secret, versioned).

CREATE TABLE IF NOT EXISTS installations (
  id TEXT PRIMARY KEY,
  bootstrap_id TEXT NOT NULL UNIQUE,
  management_credential_hash TEXT NOT NULL,
  invite_id TEXT NOT NULL UNIQUE,
  fcm_token_ciphertext TEXT NOT NULL,
  fcm_token_nonce TEXT NOT NULL,
  fcm_token_key_version INTEGER NOT NULL DEFAULT 1,
  fcm_token_version INTEGER NOT NULL DEFAULT 1,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL
);

-- 1 installation = 1 private channel (for now). installation_id UNIQUE enforces it.
CREATE TABLE IF NOT EXISTS channels (
  id TEXT PRIMARY KEY,
  installation_id TEXT NOT NULL UNIQUE,
  write_token_hash TEXT NOT NULL,
  write_token_version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_installations_enabled ON installations(enabled);
CREATE INDEX IF NOT EXISTS idx_channels_installation ON channels(installation_id);
