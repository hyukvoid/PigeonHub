# SECURITY — BETA-003 Setup Center

## Attack surface

A loopback HTTP server that can, by design, mutate local agent configs and
store pairing credentials. The controls below are enforced in
`pigeonhub/onboard.py` and pinned by `tests/test_pigeonhub_onboard.py`
(16 tests).

| Threat | Control | Test |
|---|---|---|
| LAN/remote access to the setup server | bind `127.0.0.1` only, random port; Host header must start `127.0.0.1:` | `test_binds_loopback_only_on_random_port` |
| Drive-by/browser CSRF | all mutations POST-only + Origin/Referer host must equal Host header | `test_cross_origin_post_rejected` |
| Session guessing/fixation | `secrets.token_urlsafe(24)` token; `compare_digest`; 45-min TTL; invalid/expired → 404 (existence never confirmed) | `test_bad_session_token_is_404_everywhere`, `test_expired_session_is_rejected` |
| GET-side effects | GET routes are `/setup`, `/api/status`, `/api/qr` only — all read-only; `/api/action` is POST-only | `test_get_offers_only_read_routes` |
| Arbitrary command execution | action allowlist; no parameter ever reaches a shell; setup work goes through `agents.build/apply/remove` | `test_unknown_action_rejected` (RUN_COMMAND/EXEC/SHELL/EVAL…) |
| Accidental mutation | `SETUP_*`/`REMOVE_*` require `confirm: true` | `test_mutations_require_confirm_flag` |
| Secret disclosure | responses carry statuses/labels only; poll secret asserted absent; QR served as PNG bytes; token never in HTML/logs/disk | `test_status_never_contains_secrets`, pairing-flow test, page test (`assertNotIn(session, page)`) |
| Lingering server | FINISH action / TTL / console close → shutdown | `test_finish_stops_the_server` |

## Privacy boundary inherited, not restated

The Connect dialog lists exactly what the existing integration receives
(lifecycle only) and what it never receives (full prompt, source code, full
transcript) — the allowlist enforced by `pigeonhub/agents.py`. The Setup
Center cannot weaken it: it calls the same engine.

## Codex trust

The Setup Center never bypasses Codex's own trust flow. After a Codex connect
it shows "마지막 한 단계 / One last step" with Open Codex / [연결 확인]
(re-detect). Trust state is not guessed.

## Ops hygiene this campaign

- No secret material in reports/tests: the smoke pairing uses placeholder
  values (`plr_x`, `phs_secret`, `pct_t`).
- The packaged-exe smoke hit the server with a **tokenless** request and
  asserted 404 — the negative contract — and the process was terminated,
  which also ends the server (ephemeral by construction).
- `~/.codex/hooks.json` was NOT touched by this campaign.
