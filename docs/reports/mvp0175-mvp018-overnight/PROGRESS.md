# PROGRESS — MVP-017.5 + MVP-018 Overnight

Updated as work completes. Newest first.

## 2026-09-18 (overnight)

- [x] Repo audit: branch `autonomous/mvp017-agent-integrations-codex-20260917`,
      one unstaged change (`ui.xml` — Android UIAutomator dump artifact,
      verified non-source, preserved untouched).
- [x] Branch created: `autonomous/mvp0175-mvp018-zcode-overnight-20260918`.
- [x] Production D1 audited: tables present through schema_012
      (`connector_health`, `deleted_messages`); `pairing_requests` missing.
- [x] `schema_013.sql` applied to production D1 (additive, IF NOT EXISTS).
      Verified: `pairing_requests` + `idx_pairing_requests_expiry` present.
- [x] Worker `pigeonhub-push` deployed from committed tree (commit `36e2cd5`):
      version `72805dc3-4b98-4982-a759-1ff40e902d45`, deployed ~2026-09-17T16:00Z.
- [x] `POST /v1/pairing/requests` now live (200, full request payload).
- [x] `pigeonhub` CLI installed (`pip install -e`), on PATH.
- [x] Login state: logged in, channel `ch_80983675f...`, worker reachable.
- [x] Android emulator `Medium_Phone_API_36.1` booting for device-side evidence.
