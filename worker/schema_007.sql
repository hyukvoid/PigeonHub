-- MVP-003B fix: GitHub connections are OWNER-scoped, not device-scoped.
--
-- The original DDL put UNIQUE on github_installation_id, which allowed a
-- GitHub App installation to serve exactly ONE PigeonHub device: connecting
-- a second device (or reinstalling) had to STEAL the binding, silently
-- cutting the previous device off. The corrected shape is a fan-out
-- membership set:
--   github installation  ->  many active PigeonHub installations
-- A device still belongs to at most one GitHub installation
-- (UNIQUE pigeonhub_installation_id kept). Delivery fans out to every
-- member; stale/UNREGISTERED devices simply fail their leg and are
-- skipped, and a rejoining device never disrupts the others.

CREATE TABLE github_connections_v2 (
  id TEXT PRIMARY KEY,
  pigeonhub_installation_id TEXT NOT NULL UNIQUE,
  github_installation_id TEXT NOT NULL,
  connected_at TEXT NOT NULL
);

INSERT INTO github_connections_v2
  SELECT id, pigeonhub_installation_id, github_installation_id, connected_at
  FROM github_connections;

DROP TABLE github_connections;

ALTER TABLE github_connections_v2 RENAME TO github_connections;

CREATE INDEX idx_github_connections_gh_install
  ON github_connections(github_installation_id);
