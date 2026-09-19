# E2E EVIDENCE — BETA-003A

## Real Codex notify payload (this machine, captured pre-campaign)

`tools/agent-e2e-sandbox/notify_probe.log` — a real `codex exec` (0.152.1)
invoking a notify program with argv[1] as JSON:

```json
{"type":"agent-turn-complete",
 "thread-id":"01a0b86f-f546-7653-af37-9d0dbc0470e6",
 "turn-id":"01a0b86f-fb62-7162-9176-02cbd25e307e",
 "cwd":"C:\\PigeonHub",
 "client":"codex_exec",
 "input-messages":["Reply with exactly OK and make no file changes."],
 "last-assistant-message":"OK"}
```

This single artifact grounded the whole campaign: the notify contract is
real, fires in exec mode too, and carries exactly the private fields the
parser must drop.

## Packaged CLI run tonight (fake worker, no phone)

See FRESH-USER-QA.md for the table; raw verdict line from the run:

```
event: codex DONE | Codex: done | Codex session
publish_requests: 1      (replay → still 1)
marker_leaked: False
PACKAGED_E2E: PASS
```

## Real-machine Setup Center state derivation

```
$ python -c "from pigeonhub.onboard import _codex_local_state; print(_codex_local_state())"
{"state": "connected", "detail": "hooks"}
```

Honest output for a machine where the notify slot is occupied by Codex's
own computer-use bridge while 5 user-trusted PigeonHub hooks remain active.

## Real Galaxy (ZCode) — carried evidence, not re-run

ZCode's real-phone E2E (RUNNING/PROGRESS/DONE cards, Inbox, push shade) was
verified by the owner in BETA-003 and is untouched by this campaign:
`pigeonhub/agents.py` (the ZCode engine) has no behavior change in BETA-003A
— only the shared `run_job` gained a codex-specific safe-name/marker guard
whose non-codex path is byte-identical, proven by the 103-test regression
including all agent attach-to-parent semantics tests.

## Build artifacts

- `dist/pigeonhub.exe` + `dist/PigeonHub-Setup-0.20.0-beta.2.exe`, SHA256SUMS
  verified; Defender custom scan 0 threats (real-time protection on).
- Android `app-debug.apk` / `app-release.apk` at versionCode 4 /
  0.3.0-beta003a (assembleDebug + assembleRelease green).
