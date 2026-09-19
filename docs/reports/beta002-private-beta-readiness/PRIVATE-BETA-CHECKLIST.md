# PRIVATE-BETA-CHECKLIST — BETA-002

Everything the owner still has to do, in order. At most five actions; every
completed item was removed.

## Before sending to anyone

1. **Real Galaxy pairing (6 steps, ~10 minutes)**
   1. Install `dist/beta-bundle/PigeonHub-0.2.0-beta001-release.apk` on the
      Galaxy and confirm version 0.2.0-beta001 in App info (older APKs cause
      the "different product" confusion).
   2. Install `dist/beta-bundle/PigeonHub-Setup-0.19.0-beta.2.exe` on the PC.
   3. PowerShell: `pigeonhub login` — the QR image opens by itself.
   4. Phone: PigeonHub → 연결 → PC 연결 → scan inside the frame → feel the
      buzz → 승인.
   5. PC shows `Logged in`; `pigeonhub status` says Connected / reachable.
   6. `pigeonhub run --name "Galaxy test" -- ping -n 6 127.0.0.1` → phone
      shows RUNNING → DONE.
2. **Revoke the old test-minted connector token** — only AFTER step 1's job
   succeeded, exactly per
   `docs/reports/mvp0175-mvp018-overnight/SECURITY.md`
   (`DELETE FROM connector_tokens WHERE channel_id='ch_d7c527c2…' AND
   created_at='2026-09-17T16:45:00.000Z'`). Never earlier — it is the only
   credential the PC currently holds if something goes wrong.

## During dogfood (5–7 days, 20+ real jobs)

3. Keep the dogfood journal (`docs/dogfood/DOGFOOD-TEMPLATE.md`); file P0/P1
   immediately, rest at wrap-up.

## Before 3–5 external beta users

4. If Codex interactive sessions should enrich cards: trust the hooks in
   Codex via `/hooks` (exec-mode sessions don't run hooks — see
   CODEX-COMPAT.md; not a blocker for beta).
5. Claude quota if Claude E2E should be verified (not a blocker).

Code signing is **post-beta** (before broad distribution), tracked in
KNOWN-REGRESSIONS.md.
