# E2E-EVIDENCE — BETA-001B (all runs on this machine, 2026-09-19)

## Automated matrix (`tests/test_pigeonhub_recipes_e2e.py` — real subprocesses,
## local worker; full suite `56 passed`)

| Matrix item | Test | Result |
|---|---|---|
| Generic recipe → RUNNING→DONE, card name = recipe name | `test_generic_recipe_success_and_privacy` | PASS |
| Server receives NO command/prompt strings | same (every event deserialized + asserted) | PASS |
| exit 42 → CLI exit 42, RUNNING→FAILED | `test_failure_preserves_child_exit_code` | PASS |
| Runtime prompt beats stored default; neither reaches server | `test_runtime_prompt_beats_default_prompt` | PASS |
| `--prompt-file` (shell-history avoidance) | `test_prompt_file_avoids_shell_history` | PASS |
| Missing prompt → clean error, zero events | `test_missing_prompt_fails_without_starting_job` | PASS |
| Injection probe (single-argv data, no side command, exit kept) | `test_injection_prompt_is_data_only` | PASS |
| Korean recipe name + cwd with spaces | `test_korean_name_and_spaces_in_cwd` | PASS |
| Windows `.cmd` shim via recipe | `test_windows_cmd_shim_is_routed_through_cmd` | PASS |
| Missing executable → error before any RUNNING | `test_missing_executable_leaves_no_running_job` | PASS |
| Worker outage → exit 78, command never starts | `test_publish_outage_never_starts_the_command` | PASS |
| Long job 65s+ with PROGRESS×3 → DONE | `test_long_job_reports_progress` (68.7 s real) | PASS |
| Ctrl+C → child+wrapper stop, no DONE, no hang | `test_long_job_ctrl_c_stops_wrapper_and_child` | PASS |

Unit-level (`tests/test_pigeonhub_recipes.py`, 33 tests): storage CRUD, slug
rules + generated ids for non-Latin names, duplicate/`--replace`, corrupt-file
never-wiped, secret warnings, prompt precedence/TTY rules, argv placeholder
safety, run delegation contract, agent-collision convergence (`hook event
joins parent card`, `no job_name rename`, standalone keeps own card), full CLI
surface incl. interactive wizard + secret refusal.

## Packaged exe (onefile, 0.19.0-beta.1)

```
$ ./dist/pigeonhub.exe recipe add --name "Packaged probe" -- python -c "print('...')"
$ ./dist/pigeonhub.exe recipe list / show / run / remove --yes
[pigeonhub] RUNNING OK
[pigeonhub] DONE OK        # run exit=0
```
All five verbs work inside the PyInstaller exe against a local worker.

## Installer upgrade on the real install

| Check | Before (0.18.0-beta.1) | After (0.19.0-beta.1) |
|---|---|---|
| Installed version (registry + exe `--version`) | 0.18.0-beta.1 | **0.19.0-beta.1** |
| User PATH entries containing PigeonHub | 1 | **1 (no duplicates)** |
| `~/.pigeonhub/credentials.json` sha256 | `40d0e22bf78eecd…` | **identical** |
| `recipes.v1.json` (probe recipe inside) | present | **preserved**, listed + removed by the installed exe |

## Official-agent single-job (real Codex run)

```
> python -m pigeonhub recipe run codex-probe
hook: Stop Completed
tokens used 6,213
BETA001B-OK
[pigeonhub] RUNNING OK
[pigeonhub] DONE OK            # exit 0
```
Exactly one job was created for the execution (the recipe wrapper's
`recipe-…` id; RUNNING+DONE are its only events). Hook convergence proof:

```
$ PIGEONHUB_JOB_ID=recipe-probe-manual \
  echo '{"hook_event_name":"Stop","session_id":"manual-probe-1"}' | pigeonhub agent-event codex
[pigeonhub] DONE OK            # attached to recipe-probe-manual, no job_name sent
$ agent-state.json contains key "recipe-probe-manual"
```

Honest caveat (pre-existing, not a recipe behavior): the currently installed
Codex CLI emitted a Stop hook whose payload no longer maps to the MVP-017
adapter (`"hook: Stop Completed"` printed, but no event published), so the
native enrichment did not fire on that run. The convergence mechanism is
verified by the replay above and by unit tests; re-auditing the Codex hook
payload is filed as an owner follow-up in SECURITY.md. ZCode is not installed
on this machine, so Codex served as the required one-of-two real agent.

## Android

No Android changes are required or made: recipe jobs are ordinary Job Inbox
rows keyed by `job_id` with the recipe display name as `job_name` (observed
arriving at the worker in the local-worker matrix). A registered-device
screenshot could not be taken this run: the emulator image was cold-booted
with `-no-snapshot`, so the app's onboarding (invite code) gates the inbox;
the delivery path itself is unchanged from MVP-019. Marked DEFERRED_OWNER_ACTION
with the one-line owner check.
