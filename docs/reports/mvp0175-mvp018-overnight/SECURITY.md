# SECURITY — MVP-017.5 + MVP-018

## Secret handling during the campaign

- No `.env`, API key, service-account JSON, private key, connector credential,
  or pairing secret was printed, logged, or copied into reports. Existence
  checks only (e.g., "credentials exist: True").
- Wrangler OAuth state was only probed via `wrangler whoami` (account name +
  scope list, no token material).
- The minted test token (below) never appears in any log or report; only its
  SHA-256 hash prefix was ever printed (`2764e1dc…`), and that hash is stored
  server-side anyway.

## Deliberate production changes (all additive, all reversible)

| Change | Reversal |
|---|---|
| D1: `pairing_requests` table + index (schema_013) | none needed (empty, TTL'd rows only) |
| Worker deploy `72805dc3` (adds MVP-016 pairing routes) | `wrangler rollback` or redeploy previous commit |
| D1: one `connector_tokens` row for `ch_d7c527c2…`, version 1, `created_at 2026-09-17T16:45:00.000Z` | owner: `wrangler d1 execute pigeonhub-messages --remote --command "DELETE FROM connector_tokens WHERE channel_id='ch_d7c527c2ab5e43cfa6d1' AND created_at='2026-09-17T16:45:00.000Z'"` (after re-pairing the PC properly) |

The minted token exists because the only sanctioned pairing path (phone scans
the PC QR) requires a physical camera; the emulator cannot. It grants exactly
what a properly paired connector would: publish rights to one channel. The
honest label: **the pairing UX itself was NOT exercised end-to-end** (owner
action), while the delivery path it protects was.

- `%USERPROFILE%\.pigeonhub\credentials.json` pre-campaign content is backed up
  at `<temp>/credentials-backup.json` (pointed at the dead channel; kept only
  for forensic completeness).

## Agent privacy boundary (re-verified)

- ZCode hook payloads sent through the real entry point carried marker
  strings in `toolInput`, `toolResponse`, `responseText`, `cwd`; none reached
  D1, the Android UI, or the notification shade.
- The captured real Codex Stop payload contains `last_assistant_message` and
  `transcript_path`; neither is forwarded by the allowlist normalizer.
- ZCode/Codex hook handlers keep local state in
  `~/.pigeonhub/agent-state.json` containing only job name/state/progress
  counters — no prompts, transcripts, or tool input.

## Windows packaging security posture

- No code signing certificate exists → the installer and exe are unsigned.
  SmartScreen may warn ("More info → Run anyway"). Documented in README and
  FINAL-REPORT; **not** a completion blocker per campaign rules.
- Windows Defender local scan of `dist/`: no threats; no new detections
  logged. No external multi-engine scanning service was used (binaries were
  not uploaded anywhere).
- The installer modifies only: its install dir, `HKCU\Environment\Path`
  (exact-segment add/remove), its own uninstall registry key. It never reads
  or writes `%USERPROFILE%\.pigeonhub`.
- SHA256SUMS.txt covers both distributed binaries; self-hash line removed.
