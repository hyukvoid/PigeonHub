# NEGATIVE-PATH-QA — BETA-002

Automated (local worker, real subprocesses) unless noted. New this campaign
marked ★.

| # | Scenario | Test / method | Result |
|---|---|---|---|
| 30 | Network unavailable before RUNNING | `test_publish_outage_never_starts_the_command` (recipes E2E): publish fails → child never starts → exit 78 | PASS |
| 31 ★ | Network dies AFTER RUNNING | `test_terminal_publish_survives_network_drop_without_duplication`: RUNNING accepted, terminal attempt dropped mid-connection → retried with the **same idempotency key** → stored; no duplicate rows (worker-side idempotency per MVP-019) | PASS |
| 32 ★ | Dead pairing (FCM NotRegistered) | `test_stale_pairing_prints_recovery_guidance`: stored-but-failed delivery prints "This PC's pairing can no longer reach your phone… Run: pigeonhub login"; raw FCM error never shown | PASS |
| 33 | Recipe file corruption | `test_corrupt_file_raises_and_is_never_wiped`: unparsable JSON → clear CliError, file bytes identical (no silent erase, no crash); repair path = fix or re-add with `--replace` | PASS |
| 34 | Recipe executable missing | `test_missing_executable_leaves_no_running_job`: "Command not found…" before any publish; zero events | PASS |
| 35 | Installer upgrade | beta.1→beta.2 on the live install (and the BETA-001B full matrix): credentials sha256 identical, recipes preserved, PATH single segment | PASS |
| 36 | Uninstall → reinstall | real run this campaign: binary+PATH removed, `%USERPROFILE%\.pigeonhub\` untouched; reinstall restores PATH/version/status/recipes | PASS |

Android reliability (PART J) — honest scope:

- Process death/restore: force-stop → relaunch on the clean emulator returns
  to the same (pre-registration) state without crash; durable-Inbox restore
  itself needs a registered device — covered by unchanged MVP-019 unit tests
  (`InboxItemsTest`, `JobAttentionPolicyTest`, `PushPayloadValidatorTest`) and
  the owner device pass.
- Job display states and notification policy (no pushes for RUNNING/PROGRESS;
  normal for DONE; high for FAILED/NEEDS_ACTION; soft stale marker) are
  unchanged code paths guarded by those same unit tests.
