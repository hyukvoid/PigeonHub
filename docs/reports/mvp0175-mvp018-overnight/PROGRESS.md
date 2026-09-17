# PROGRESS — MVP-017.5 + MVP-018 Overnight

Updated as work completes. Newest first.

## Overnight final state
- [x] **MVP-017.5 ZCode E2E**: setup gates verified (preview/confirm/apply/
      idempotent/remove/reinstall). Found+fixed strict-schema hook bug
      (extra `enabled` field dropped by ZCode). Hook→worker→FCM→Android card
      + shade evidence captured (5-state lifecycle, all `fcm_accepted`).
      Standalone ZCode CLI needs `zcode login` (owner) — payloads were fed to
      the real hook entry point instead, labeled honestly.
- [x] **MVP-017.5 Codex regression**: real `codex exec` session → RUNNING +
      DONE → Android card; discovered+documented the one-time hook trust gate
      (`/hooks`); captured real Stop payload → privacy allowlist confirmed on
      the wire.
- [x] **MVP-017.5 Claude**: setup gates verified (backup/idempotent/remove/
      reinstall, user keys preserved). Real task → API 402 quota
      (DEFERRED_OWNER_ACTION).
- [x] **MVP-017.5 Grok**: not installed anywhere (PATH/Programs/registry/
      ~/.grok checked) → DEFERRED_OWNER_ACTION.
- [x] **Pairing path**: PC credential pointed at a dead channel (emulator was
      recreated since MVP-015) → test connector token minted into the live
      channel for validation (documented + revocable, SECURITY.md); PC-first
      login QR/request/poll legs verified from the installed exe; phone
      approval = physical camera owner action.
- [x] **MVP-018**: version single-source (`0.18.0-beta.1`), first-run banner,
      `--version`; PyInstaller onefile (python-free verified, 1.2–1.6 s
      startup); Inno Setup per-user installer (winget-installed ISCC); PATH
      add/upgrade/uninstall safe; npm `.cmd` shim bug found+fixed+regression-
      tested; upgrade/uninstall/reinstall matrix PASS; credentials preserved
      by design; Defender clean; SHA256SUMS; README rewritten user-first.
- [x] Regression gate: Python 14/14, worker typecheck, Android unit +
      assembleDebug + assembleRelease, git diff --check, secret scan.

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
