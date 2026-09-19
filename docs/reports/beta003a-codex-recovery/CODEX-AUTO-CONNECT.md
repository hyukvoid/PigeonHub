# CODEX AUTO-CONNECT — BETA-003A

Zero-command Codex connection, reusing the BETA-003 Setup Center consent
semantics. Implementation: `pigeonhub/codex_integration.py`.

## Trigger points (all consent-gated, all failure-isolated)

1. **Setup Center pairing approval** — the phone step's "지원되는 도구 자동
   연결" checkbox (BETA-003 consent, unchanged semantics) → on `approved`,
   `onboard._start_auto_connect()` runs `connect_codex()` on a background
   thread. Pairing is already durable; this can only add, never roll back
   (`test_pairing_approval_survives_codex_auto_connect_attention`).
2. **CLI login** — `pigeonhub login` shows the same disclosure; Enter
   defaults to yes; `--no-auto-connect` opts out
   (`_pairing_auto_connect_consent`). Non-TTY stdin defaults to yes.
3. **Setup Center tools screen** — the Codex card's 연결 button (`SETUP_CODEX`)
   routes to the notify engine. The hook-based full lifecycle remains a
   deliberate `pigeonhub setup codex` CLI choice because it requires Codex's
   own trust flow.

## What a connect actually writes

A text-level edit of `~/.codex/config.toml` — one managed assignment:

```toml
# PigeonHub: Codex completion notifications
notify = ["C:\…\pigeonhub.exe", "internal-codex-notify"]
```

Guarantees (`tests/test_beta003a_codex_autoconnect.py`):

- comments, model config, profiles, MCP settings, unknown keys preserved
  byte-for-byte outside the managed block;
- timestamped backup before any change to an existing file;
- atomic write (tempfile + os.replace + fsync);
- post-write re-verification with automatic rollback on mismatch;
- idempotent (second run changes nothing);
- existing non-PigeonHub `notify` → **NEEDS_ATTENTION, never overwritten**
  (safe chaining does not exist upstream);
- malformed TOML → refused, file untouched;
- remove = only the managed block (`REMOVE_CODEX` proceeds with hook cleanup
  even when the notify slot holds a foreign program).

## The callback

`pigeonhub internal-codex-notify [payload]` (also stdin) — a payload parser,
not a command runner. It is fail-closed end to end: any surprise exits 0
silently so the Codex turn is never disturbed.

## State accuracy (Setup Center)

`onboard._codex_local_state()` derives the Codex card read-only:

- notify installed → `연결됨 ✓ (notify)`
- notify slot foreign + ≥1 PigeonHub-managed hook → `연결됨 ✓ (hooks)`
- notify slot foreign + no hooks → `확인 필요 (existing notify preserved)`
- no Codex → 미감지

Measured on the owner's machine right now: `{"state": "connected",
"detail": "hooks"}` — honest, because that is the live integration source.
