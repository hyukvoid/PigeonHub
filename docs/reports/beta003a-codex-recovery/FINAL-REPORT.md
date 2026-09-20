# FINAL REPORT — BETA-003A Codex Zero-Command Auto-Connect + Recovery Hardening

Date: 2026-09-20 · Branch `autonomous/beta003a-codex-recovery-20260920`
(base `ee6308f`) · CLI 0.20.0-beta.2 · Android 0.3.0-beta003a (versionCode 4)

## P1 addendum (owner-reported, fixed same night)

The owner's real-browser test exposed a P1: the tool-connection modal was
empty and the page appeared frozen. Root cause: an inline `display:flex` on
`#modal` defeated the `.hidden` rule, so the modal was always visible, its
backdrop swallowed every click, and it could never be closed. Fixed
(class-driven visibility + busy/success/error/timeout states + double-click
guard) and re-verified at three levels — page-structure tests, real-browser
E2E (headless Edge/CDP, 12 checks), and a packaged-exe browser E2E against
the exe's own Setup Center server (12 checks). Details:
P1-SETUP-CENTER-MODAL.md. Regression: Python 109/109.

## Verdict: **CONDITIONAL_PRIVATE_BETA_READY**

The zero-command Codex journey is implemented, packaged, and proven end to
end against the packaged exe (pair → detect → auto-connect → single
sanitized completion event → dedup). What keeps the CONDITIONAL prefix is
exactly two things, both physical-world:

1. on-device confirmation on the real Galaxy (new APK: pairing-state card,
   immediate refresh, real Codex Desktop completion → push), and
2. a real Windows reboot (no-re-pairing proof; every non-reboot aspect is
   already proven).

Additionally, an upstream reality is now precisely documented: the `notify`
slot is single, and on the owner's machine Codex Desktop's own
computer-use bridge occupies it — PigeonHub preserves it and reports an
honest state while the Codex connection rides the user-trusted hooks
there. Fresh installs get the notify path cleanly (packaged E2E PASS).

## Scoreboard

| Item | Status |
| --- | --- |
| CODEX_DETECTED | VERIFIED — codex-cli 0.152.1, PATH + home, real machine |
| CODEX_AUTO_CONNECT | VERIFIED — packaged-exe real path (login→approval→notify installed); owner machine honestly NEEDS_ATTENTION for notify / connected via hooks |
| NO_MANUAL_SETUP_CODEX | VERIFIED — no `pigeonhub setup codex`, no `/hooks`, no config editing in the user journey |
| CODEX_DESKTOP_COMPLETION_PATH | IMPLEMENTED — notify mechanism proven with real 0.152.1 payload + packaged E2E; the literal Desktop-app run awaits the physical Galaxy (owner action) |
| CODEX_PRIVACY_BOUNDARY | VERIFIED — structural parser drop, allowlist-only |
| PROMPT_NOT_TRANSMITTED | VERIFIED — `PRIVATE_CODEX_PROMPT_MARKER_003A` absent everywhere |
| ASSISTANT_TEXT_NOT_TRANSMITTED | VERIFIED — same |
| CWD_NOT_TRANSMITTED | VERIFIED — same |
| CODEX_CONFIG_PRESERVED | VERIFIED — comments/profiles/unknown keys byte-intact, backup+rollback, idempotent |
| EXISTING_NOTIFY_PRESERVED | VERIFIED — fixture test + the real machine is the living case |
| CODEX_EXEC_BRIDGE | VERIFIED — BETA-002 fixtures + lifecycle_from_line regression 5/5 |
| NO_DUPLICATE_JOB | VERIFIED — bridge marker chain test: exactly RUNNING+DONE |
| ZCODE_REGRESSION | VERIFIED (automated) — shared engine untouched on the non-codex path; 103-test regression green. Real-phone ZCode smoke: carried BETA-003 evidence, not re-run |
| PHONE_PAIRING_PERSISTENCE | VERIFIED — credential survives new processes and uninstall/reinstall (byte-identical) |
| PROCESS_RESTART_PERSISTENCE | VERIFIED — new CLI process, same identity, worker reachable; new Setup Center session reports already_paired |
| REAL_WINDOWS_REBOOT | DEFERRED_OWNER_ACTION — automation cannot power-cycle; everything except the power cycle is proven |
| ANDROID_NOT_PAIRED | IMPLEMENTED — state + [PC 연결] CTA, logic unit-tested |
| ANDROID_PAIRED | IMPLEMENTED — connected pill, primary CTA removed; on-device look = owner action |
| ANDROID_STALE | IMPLEMENTED — [다시 연결]; never guessed from QR errors |
| PAIR_SUCCESS_IMMEDIATE_REFRESH | IMPLEMENTED — state written in the approval coroutine, card recomposes without restart; on-device confirmation = owner action |
| RETURNING_SETUP_CENTER | VERIFIED — already_paired → "이미 연결돼 있어요 ✓ / 다음" (unit test; no fresh wizard forced) |
| FRESH_INSTALL | VERIFIED — fresh-Codex-home packaged E2E PASS; fresh-Windows track carried from BETA-002/003 (installer logic unchanged apart from version) |
| UPGRADE | VERIFIED — 0.19.0-beta.2 → 0.20.0-beta.2 silent; credentials/recipes/hooks byte-identical; foreign notify untouched |
| UNINSTALL_REINSTALL | VERIFIED — dir/key/PATH removed, user data intact; reinstall restores all, logged in |
| PACKAGED_EXE | VERIFIED — all Codex E2E evidence is from dist/pigeonhub.exe |
| INSTALLER | VERIFIED — silent compile + upgrade + uninstall/reinstall runs |
| KO_EN_PARITY | VERIFIED — Android 234=234 names; Setup Center strings mirrored |
| SECRET_SCAN | VERIFIED — no markers/tokens in sources or diffs |
| DEFENDER | VERIFIED — custom scan of exe + installer, 0 threats |
| FULL_REGRESSION | VERIFIED — Python 103/103; Android unit + assembleDebug/Release; worker tsc clean |

## Campaign questions (Q1–Q10)

| Q | Question | Answer |
| --- | --- | --- |
| Q1 | Must the user run `pigeonhub setup codex`? | **NO** — auto-connect after pairing |
| Q2 | Must the user open Codex config? | **NO** — text-level managed edit |
| Q3 | Is Codex auto-discovered after phone pairing? | **YES** — detection then consent-gated connect |
| Q4 | Is the integration applied automatically and safely? | **YES** — with the upstream limit reported: a pre-existing notify (e.g. Codex's own computer-use bridge) is preserved, not replaced |
| Q5 | Was Codex trust bypassed? | **NO** |
| Q6 | Do prompt/assistant/cwd reach the server? | **NO** — marker-proven |
| Q7 | Does one execution create two cards? | **NO** — proven one-card chain |
| Q8 | Does pairing disappear on power-off/process exit? | **NO** — process-restart + reinstall proven; real reboot = owner action |
| Q9 | Does [PC 연결] remain after success? | **NO** — PAIRED removes the primary CTA |
| Q10 | Does reopening Setup Center re-demand QR? | **NO** — already_paired fast path |

## Bonus fixes forced on the way

- `_delivery_note` printed "OK" for a failed publish (a 401 carries no
  push_status) — now honest, with regression test.
- `RealSubprocessInjectionTests` had been publishing its malicious-prompt
  fixture to the real worker since BETA-001B (test isolation defect) —
  now sandboxed; exposed tonight by a worker rejection.
- `REMOVE_CODEX` could deadlock removal when the notify slot held a
  foreign program even though nothing of ours was in config.toml — now
  proceeds with hook cleanup.

## Owner actions (all that remain)

1. Install the new APK (0.3.0-beta003a) on the Galaxy; run Setup Center →
   QR pairing; confirm the PC card flips to 연결됨 and the [PC 연결] CTA is
   gone without restarting the app.
2. Reopen the Setup Center later — confirm "이미 연결됨" fast path (no QR).
3. Complete a real Codex Desktop task → confirm the Galaxy notification.
   (On this PC the connection rides the trusted hooks; fresh machines will
   use the notify path automatically.)
4. Reboot Windows once → confirm no re-pairing is demanded and Codex
   notifications still work.
