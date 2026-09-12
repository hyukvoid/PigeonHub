# MVP-003B Fix — GitHub Connection Is Owner-Scoped Fan-Out, Not Device-Scoped

Date: 2026-09-13
Trigger: Clean-AVD E2E rebinding left the physical Galaxy device cut off
from GitHub deliveries (Galaxy GitHub = FAIL while emulator = PASS).

## ROOT CAUSE — proven in D1

`github_connections` live DDL before the fix:

```sql
CREATE TABLE github_connections (
  id TEXT PRIMARY KEY,
  pigeonhub_installation_id TEXT NOT NULL UNIQUE,
  github_installation_id TEXT NOT NULL UNIQUE,   -- ← the bug
  connected_at TEXT NOT NULL
)
```

`UNIQUE(github_installation_id)` allowed ONE device per GitHub App
installation. State at the time of the report:

| github_installation_id | bound pigeonhub_installation | device |
|---|---|---|
| 161059066 | e2e725ab (boot-c99fd0b) | **emulator only** |

The physical Galaxy's installation existed in `installations` but was not
the binding target — connecting the emulator during the clean-AVD E2E had
stolen the single row from the Galaxy. The flip experiment (rebind →
Galaxy PASS / emulator FAIL) was therefore proven by construction: the
delivery target followed the single row on every rebinding.

## Correct architecture (implemented)

```
GitHub installation (161059066)          ← stable owner identity
  → github_connections membership set    ← many active installations
  → common delivery pipeline             ← per-channel durable message
  → FCM fan-out                          ← one leg per device
```

- **schema_007**: `github_installation_id` UNIQUE dropped; the table is a
  fan-out membership set. A device is still single-GitHub
  (`UNIQUE pigeonhub_installation_id` kept).
- **workflow_run.completed**: fan-out to EVERY enabled member —
  per-channel message copies (`gh-run-<run>-<channel>`), per-channel seq
  echoed in FCM, webhook redelivery absorbed per channel, and a stale
  token fails ONLY its own leg (404/UNREGISTERED isolation).
- **installation.created**: every active installation joins as a member —
  connecting is additive, never a steal.
- **GET /v1/github/status**: an unbound device auto-joins while the
  deployment has exactly one GitHub installation (beta = single owner).
  Reinstall and new devices therefore inherit the GitHub connection
  without any re-connect ritual. The guess disables itself the moment a
  second GitHub installation exists (multi-tenant comes with owner
  accounts).
- **Android Connections**: polls the real connection state (the static
  "Connected" label was dishonest) and re-polls on return from the GitHub
  page; `github_not_connected` state added (KO/EN).

Deployed Worker version `d0262543`. Commit `da21062`.

## Verification

| Check | Result |
|---|---|
| Auto-join via product path | PASS — a second installation polled `/v1/github/status` once and joined (`connected=true`) |
| Membership set grows additively | PASS — 2 rows, both → 161059066; the emulator's row untouched |
| ONE workflow run → all members | PASS — run 34701644323 produced TWO per-channel messages |
| Healthy leg delivered | PASS — emulator leg `fcm_accepted` (seq 8); shade shows grouped "Build completed" ×2; Inbox shows the run after Refresh |
| Stale-token leg isolated | PASS — the second leg failed `fcm 404 NotRegistered` ALONE; emulator leg unaffected (exclusion requirement) |
| Redelivery idempotency per channel | PASS — `gh-run-<id>-<channel>` re-absorbed by the existence check |

Evidence: `docs/reports/fanout-emulator-shade.png`.

## Physical Galaxy — pending one device action

The Galaxy dropped off USB mid-session (`R3KL103BADZ` absent from adb for
the whole verification window), so the physical-device leg could not be
driven. Note: `server/.env`'s `FCM_DEVICE_TOKEN` is already stale
(NotRegistered) — the Galaxy's own app will re-register its current token
on next launch, which is exactly what the fan-out will target.

When the Galaxy is re-connected, the remaining gate takes ~2 minutes:

1. `adb install -r app-release.apk` (keeps its registration)
2. Launch PigeonHub → open **Connections** → status poll auto-joins it
3. Dispatch the E2E workflow once
4. Expect: Galaxy system notification + emulator system notification +
   both Inboxes, from that single run

Connecting a device must never again move the target — with the
membership-set schema that failure mode is structurally impossible.
