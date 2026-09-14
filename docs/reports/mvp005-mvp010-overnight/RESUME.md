# RESUME — next session picks up here (2026-09-14 morning)

Branch: `autonomous/mvp005-mvp010-overnight-20260914` — all work committed here, `main` untouched.
Working tree: clean. Latest commit: see `git log --oneline -10`.

## State

- MVP-005…011 all VERIFIED (see FINAL-REPORT.md for the table + evidence paths).
- MVP-011: worker coalescing deployed (schema_010 `job_states`), worker version `1ac1e820…`.
- Worker deployed (Cloudflare `pigeonhub-push`): job columns on publish/FCM/sync, GitHub
  webhook job mapping, pairing endpoints. D1 migrations applied: `schema_008.sql`,
  `schema_009.sql` (both additive).
- Android debug APK installed on `emulator-5554` (AVD PigeonHub-E2E-API36, wiped mid-campaign).
  Device state: onboarded install `ch_5d0a6c666e1d406b98d0…`, per-app locale reset to system
  (en), appearance System, job-alert toggles all ON, font 1.0, night off.
- Pairing for the local PC connector is DONE: `~/.pigeonhub/credentials.json` holds a live
  connector token for that channel (outside the repo). `worker/.test-invite-current` holds the
  last unused/burned test invite (codes are single-use; provision a fresh one via the
  INVITE_TEST_HASHES flow before any new onboarding E2E).

## Known leftovers (ranked)

1. Server-side job-progress coalescing (currently every PROGRESS event is a D1 row + FCM send;
   quota 5/min can throttle chatty connectors — client retries cover terminals only).
2. QR rendering for pairing codes (text code only today).
3. Real ComfyUI app integration (DEFERRED — contract + simulator verified).
4. Re-test a full real `claude -p` session Stop-hook when API quota resets (manual lifecycle
   already verified).
5. Bottom-nav EN labels wrap at font ≥150% (accepted MVP-004 finding; Korean unaffected).
6. Test-artifact drift note: pruning D1 messages mid-life of a device reassigns seqs and causes
   (channel, seq) collisions on the phone — now absorbed by the reserved-seq fallback, but avoid
   deleting production rows under a live installation.

## Quick verification commands

- Android unit tests: `cd android && ./gradlew testDebugUnitTest`
- Build: `cd android && ./gradlew assembleDebug`
- Worker typecheck: `cd worker && npx tsc --noEmit`
- Real MY_PUSH: app → Connections → Send test notification → logcat `[fcm] delivered push`
- Real GitHub E2E: `gh workflow run "GitHub App E2E" -R hyukvoid/PigeonHub` (gh binary also
  at `tools/gh.zip`), then watch logcat for `gh-run-<id>…`
- Connector E2E: `python connectors/pigeonhub_connector.py run --name X -- python …`
  (already paired; or re-pair via a fresh code from the ComfyUI card)

## Watch-outs

- Emulator `input tap` is flaky when several taps are chained in one `adb shell` string —
  run each tap as its own adb command and re-dump `uiautomator` right before tapping.
- Read-tool screenshots are 900×2000; device is 1080×2400 (×1.2) — always take tap targets
  from `uiautomator dump`, never from the displayed image.
