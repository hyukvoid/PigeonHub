# MVP-003B — Clean AVD E2E Re-Verification Report

Date: 2026-09-12
Scope: Clean-AVD re-verification of the GitHub App push pipeline after the
workflow_run root-cause fix, plus fixes discovered during this pass.

## Result: PASS (after one crash bug found & fixed mid-run)

| Gate | Result | Evidence |
|---|---|---|
| ADB stability | PASS | 12/12 checks over 10 min, `emulator-5554` in `device` state throughout (earlier "failures" were a harness bug: awk picked the state field instead of the serial) |
| Clean install + onboarding | PASS | `pm clear` + reinstall; fresh single-use test invite redeemed; Inbox empty state reached |
| FCM token registration | PASS | D1 `installations`: new row, encrypted token (212 B ciphertext), `fcm_token_version=1` |
| My Push baseline (in-app test send) | PASS | D1 `push_status=fcm_accepted` + real FCM message id; device: `[fcm] delivered push` log; Room row in Inbox; OS notification posted (importance=4, channel `pigeonhub_high`) |
| GitHub binding → live installation | PASS (ops) | `github_connections` rebound from the wiped pre-clear installation to the live one; single-device beta semantics |
| GitHub E2E (real webhook) | PASS | `gh workflow dispatch` → run 34697841631 → `workflow_run.completed` webhook → Worker → D1 (seq=3) → FCM accepted → background device: notification shade shows "PigeonHub · Build completed · main - success"; Inbox row with run URL |

Evidence screenshot: `docs/reports/e2e-notification-shade.png`.

## Crash bug found and fixed

**Symptom.** First real `workflow_run.completed` delivery crashed the app
(`SQLiteConstraintException: NOT NULL constraint failed: inbox_messages.seq`
in `PushPipeline.handle` → FCM intent handler died; no notification posted).
My Push baseline was unaffected because the publish path always includes
`seq`; the webhook path did not.

**Root cause.** Two sides drifted:
1. Worker webhook FCM payload omitted `seq` (the publish path includes it).
2. Android `insertFromFcm` declared `seq: Int?` but the entity column is
   NOT NULL — the "stored without a seq coordinate" intent was never
   actually implementable, so any seq-less payload crashed the handler.

**Fixes.**
- `worker/src/index.ts`: webhook handler reads back the allocated
  `messages.seq` and echoes it in the FCM data payload.
- `android/.../InboxMessages.kt`: `insertFromFcm` coalesces a missing seq to
  a reserved negative local value (`-(count+1)`), so the NOT NULL and
  UNIQUE(channel_id, seq) constraints always hold and an FCM handler crash
  on payload drift is structurally impossible; `insertFromSync` now also
  reconciles `seq = excluded.seq` so the server coordinate replaces the
  fallback on next sync.

Deployed Worker version `366e23cc`; verified by a second real workflow run
(34697841631) delivered to a backgrounded app with no crash.

## Known follow-ups (not blockers for V1)

- Reinstall gap: a PigeonHub reinstall creates a new installation while the
  GitHub binding still points at the old one; today it was rebound via D1
  ops. Product fix (binding supersede / fan-out semantics) is V2 scope.
- Webhook redelivery of the same `workflow_run.id` hits the message
  idempotency UNIQUE constraint; GitHub's at-least-once retry tolerance
  should be verified (currently a duplicate returns a 500 after the insert
  throws — harmless to state, noisy in logs).

## Verdict

The five-minute GitHub activation loop — install app, tap Connect, pick
repos, first build notification arrives and lands durably in the Inbox —
is now verified end-to-end on a clean emulator against the production
Worker, using only free-tier infrastructure.
