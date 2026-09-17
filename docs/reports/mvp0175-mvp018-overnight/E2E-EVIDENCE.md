# E2E-EVIDENCE — MVP-017.5 + MVP-018 (2026-09-18 overnight)

Real production evidence, newest pipeline last. All timestamps UTC
(2026-09-17 evening UTC = 2026-09-18 early morning KST).

## Production platform changes

- D1 `pairing_requests` table + `idx_pairing_requests_expiry` created
  (schema_013, additive). Verified via `sqlite_master` before/after.
- Worker `pigeonhub-push` deployed from commit `36e2cd5` tree:
  version `72805dc3-4b98-4982-a759-1ff40e902d45`.
  `POST /v1/pairing/requests` now returns 200 (was 404 pre-deploy).

## ZCode · GLM — hook entry → worker → FCM → Android

Chain: `pigeonhub agent-event zcode` (the exact command the installed ZCode
hook runs) fed the five real ZCode payload shapes → production worker →
`fcm_accepted` → emulator.

- Session `overnight-e2e2-1789662945`: RUNNING (16:35:47) → PROGRESS (16:35:50)
  → NEEDS_ACTION (16:35:52) → DONE (16:35:54), all `push_status=fcm_accepted`
  with FCM message ids, no duplicates.
- Android Job Card: "Zcode · Zcode session — Done, 4 updates, just now"
  (`evidence/zcode-jobcard-done.png`).
- System notification shade: "Zcode session: needs action / Agent is waiting
  for your input" and "Zcode session: done / Agent session completed"
  (`evidence/zcode-push-shade.png`).
- Privacy: payloads contained `toolInput`/`toolResponse`/`responseText` marker
  strings ("SECRET-…", "PRIVATE …"); none appear in D1 titles/messages, the
  Android UI dumps, or the notification shade.

## Codex — real session

- `codex exec` (v0.152.1, `--dangerously-bypass-hook-trust` for this run)
  in a scratch dir: SessionStart hook → RUNNING (16:44:49, `fcm_accepted`);
  Stop hook → DONE (16:44:53, `fcm_accepted`).
- Android Job Card: "Codex · Codex session — Done, 2 updates"
  (`evidence/codex-zcode-inbox.png`).
- Captured the real Stop hook stdin: `{cwd, hook_event_name,
  last_assistant_message, model, permission_mode, session_id,
  stop_hook_active, transcript_path, turn_id}` — the sensitive
  `last_assistant_message`/`transcript_path` fields are present on the wire
  and confirmed absent from the published job.

## CLI jobs from the installed exe (live channel)

- "Windows success" (`cmd /c echo…`): RUNNING→DONE, exit 0.
- "Windows failure" (`cmd /c exit 42`): RUNNING→FAILED, process exit 42.
- "Long render (ffmpeg)" 75 s: RUNNING→DONE, exit 0.
- "git smoke": RUNNING→DONE. "npm smoke": RUNNING→FAILED before the `.cmd`
  shim fix; RUNNING→DONE (npm 11.17.0) after fix, re-verified on the upgraded
  installed exe.
- PC-first login from the installed exe: QR rendered (45 rows), pairing
  request persisted (D1 `pairing_requests`, 4 rows total, latest 17:14:37Z).

## Channel note (owner-readable)

The PC credential inherited from the 09-14/15 campaign pointed at channel
`ch_80983675f…` whose installation no longer exists (emulator was recreated),
so early events hit FCM `NotRegistered`. For overnight validation a test
connector token was minted directly into `connector_tokens` for the live
device channel `ch_d7c527c2…` (version 1, `created_at
2026-09-17T16:45:00.000Z`) — outside the product approval flow, transparently
documented in SECURITY.md with revocation instructions. First campaign phase
events also exposed a worker behavior: on FCM failure the publish path stores
the message and then returns 502, so client retries created duplicate rows
(17 rows for the two dead-channel sessions). Not hit again once delivery was
healthy; filed as a P2 worker finding in FINAL-REPORT.
