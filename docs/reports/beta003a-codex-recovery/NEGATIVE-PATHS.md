# NEGATIVE PATHS — BETA-003A

Campaign matrix → where each case is covered. "PASS" = automated test green
unless noted.

## Codex callback surface

| Case | Coverage | Result |
| --- | --- | --- |
| malformed Codex payload | `CodexNotifyNegativePathTests` (empty / non-JSON / `{` / array / number / null) | PASS (dropped, nothing published) |
| oversized payload | > 128 KiB dropped wholesale before parse | PASS |
| unknown event type | non-`agent-turn-complete` dropped | PASS |
| missing thread/turn ids | dropped | PASS |
| prompt marker leakage | `PRIVATE_CODEX_PROMPT_MARKER_003A` absent from all publishes (unit + packaged E2E) | PASS |
| assistant text leakage | same as above (`last-assistant-message`) | PASS |
| cwd leakage | same (`cwd` never read) | PASS |
| duplicate callback | same payload twice → one publish | PASS |
| replayed callback | success-marker state file; publish failure keeps replay possible | PASS |
| hostile identifier text | traversal/metachars sanitized out of job ids | PASS |

## Config mutation surface

| Case | Coverage | Result |
| --- | --- | --- |
| malformed config | `test_malformed_toml_is_not_overwritten` — refused, untouched | PASS |
| read-only config | write-failure injection keeps original bytes, NEEDS_ATTENTION (`test_atomic_write_failure_keeps_original_config`) | PASS (contract; not a real ACL mount) |
| existing third-party notify | `test_existing_unrelated_notify_is_preserved_and_needs_attention` + real machine case (Codex's own computer-use notify) | PASS |
| config preservation on write | backup + atomic + verify + rollback suite | PASS |
| idempotent install | second plan run changes nothing | PASS |
| remove only what is ours | managed-comment removal; unrelated keys survive; REMOVE proceeds when the slot is foreign | PASS |

## Pairing / phone / network surface

| Case | Coverage | Result |
| --- | --- | --- |
| network outage during notify publish | fail-closed exit 0, replay still possible, then deduped | PASS |
| stale phone credential (PC publish) | `stale_pairing` guidance path (existing, mvp019-era suite) | PASS (carried) |
| pairing approval survives Codex setup failure | `test_pairing_approval_survives_codex_auto_connect_attention` + `test_auto_connect_failure_does_not_rollback_saved_pairing` | PASS |
| Setup Center abuse | BETA-003 security suite (bad token 404, expired 404, cross-origin 403, unknown action, confirm-required) | PASS (carried; one socket-churn flake seen once under full-suite load, green in isolation/file/final run) |
| corrupted Android stored state | `PcPairingStateTest` unknown values → NOT_PAIRED, never fabricated | PASS |

## Honest limitations

- The "read-only config" case is proven by injected write failure, not by a
  read-only ACL mount (platform-flaky in CI); the code path is identical.
- The Setup Center cross-origin flake appeared exactly once under full-suite
  loopback socket churn; it did not reproduce in isolation, its own file, or
  the final full run (103/103).
