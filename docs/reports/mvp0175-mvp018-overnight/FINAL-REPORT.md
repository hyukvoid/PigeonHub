# FINAL-REPORT — MVP-017.5 + MVP-018 Overnight (2026-09-18)

## MVP-017.5 AGENT VALIDATION

| Gate | CODEX | CLAUDE | GROK | ZCODE |
|---|---|---|---|---|
| SETUP_PREVIEW | VERIFIED | VERIFIED | DEFERRED_OWNER_ACTION | VERIFIED |
| CONFIRM | VERIFIED | VERIFIED | DEFERRED_OWNER_ACTION | VERIFIED |
| APPLY | VERIFIED | VERIFIED | DEFERRED_OWNER_ACTION | VERIFIED |
| IDEMPOTENT_REAPPLY | VERIFIED | VERIFIED | — | VERIFIED |
| REMOVE | VERIFIED | VERIFIED | — | VERIFIED |
| REINSTALL | VERIFIED | VERIFIED | — | VERIFIED |
| REAL_START | VERIFIED (real session) | DEFERRED_OWNER_ACTION (402 quota) | DEFERRED_OWNER_ACTION (not installed) | VERIFIED * (real hook chain, vendor payload simulated) |
| REAL_DONE | VERIFIED (real session) | DEFERRED_OWNER_ACTION | DEFERRED_OWNER_ACTION | VERIFIED * |
| REAL_FAILED | — (no terminal-failure event in contract) | DEFERRED_OWNER_ACTION | DEFERRED_OWNER_ACTION | — (same contract) |
| REAL_NEEDS_ACTION | IMPLEMENTED (hook registered; not reproducible headlessly) | DEFERRED_OWNER_ACTION | DEFERRED_OWNER_ACTION | VERIFIED * (real shade notification) |
| ANDROID_CARD | VERIFIED | IMPLEMENTED | — | VERIFIED |
| SYSTEM_PUSH | VERIFIED (fcm_accepted ×2) | IMPLEMENTED | — | VERIFIED (fcm_accepted ×4 + shade) |
| HEALTH | IMPLEMENTED | IMPLEMENTED | — | IMPLEMENTED |

`*` = every leg from the hook entry point onward (normalizer → worker → D1 →
FCM → Android card → system shade) is real production infrastructure; only the
vendor payload was fed programmatically because no standalone ZCode session
can run on this machine yet (owner action #3). Synthetic-only evidence would
not have been marked VERIFIED — the distinction is written per row.

- **AGENT_PRIVACY**: VERIFIED — real ZCode payloads with SECRET/PRIVATE marker
  strings and the captured real Codex Stop payload (containing
  `last_assistant_message`, `transcript_path`) produce zero leakage into D1,
  Android UI, or notifications; allowlist confirmed on the production wire.
- **AGENT_SETUP_REMOVE**: VERIFIED — preview/confirm/backup/atomic-write/
  verify/idempotent-reapply/remove/reinstall proven for codex, claude, zcode
  (grok blocked by detection, by design).

## MVP-018 WINDOWS DISTRIBUTION

| Gate | Status |
|---|---|
| STANDALONE_EXE | VERIFIED — PyInstaller onefile `pigeonhub.exe`, 8.9 MB |
| NO_PYTHON_REQUIRED | VERIFIED — runs with Python stripped from PATH |
| VERSION | VERIFIED — `--version`/`version` → `PigeonHub CLI 0.18.0-beta.1`, single-sourced from `pigeonhub/__init__.py` |
| INSTALLER | VERIFIED — Inno Setup 6, minimal wizard, finish page shows next commands |
| PER_USER_INSTALL | VERIFIED — `%LOCALAPPDATA%\Programs\PigeonHub`, no admin |
| PATH | VERIFIED — exact-segment add to HKCU Path; removed cleanly on uninstall; no duplicates after reinstall |
| FIRST_RUN | VERIFIED — bare `pigeonhub` guides to login/run; `--help` unchanged |
| LOGIN_FROM_PACKAGED_EXE | VERIFIED (PC legs) — QR rendered (45 rows), pairing request persisted in production D1; phone approval = owner action (physical camera) |
| RUN_FROM_PACKAGED_EXE | VERIFIED — success/failed/long/tool jobs all through the installed exe |
| SUCCESS_JOB | VERIFIED |
| FAILED_JOB | VERIFIED |
| EXIT_CODE | VERIFIED — `cmd /c exit 42` → process exit 42 |
| LONG_JOB | VERIFIED — 75 s ffmpeg, RUNNING→DONE |
| UPGRADE | VERIFIED — reinstall in place replaces exe safely, no PATH dup |
| UNINSTALL | VERIFIED — dir/PATH/key removed, unrelated files untouched |
| REINSTALL | VERIFIED — works after uninstall |
| CREDENTIAL_PRESERVED | VERIFIED — survives reinstall AND uninstall (by documented design; `logout` is the remover) |
| DEFENDER_SCAN | VERIFIED — no threats |
| SHA256 | VERIFIED — SHA256SUMS.txt matches both binaries |
| DOCUMENTATION | VERIFIED — README is user-first; maintainer build docs in packaging/windows/ |

## Verdict

**CONDITIONAL_WINDOWS_BETA_READY.**

A new Windows user needs no Python and no PATH edits: download → install →
open a fresh terminal → `pigeonhub login` → `pigeonhub run …` works, and every
step has automated or recorded evidence. The conditions are exactly:

1. The login approval requires the physical phone (camera QR) — the only
   product step that could not be exercised overnight; the QR, request,
   polling, and credential-save legs are all verified.
2. SmartScreen warning until code-signed (owner action, not a blocker).
3. Agent hooks for Claude/ZCode-standalone/Grok activate after the three
   owner actions in RELEASE-CHECKLIST; Codex is fully verified end-to-end
   except the one-time in-app trust step.

## Findings fixed tonight (reproduce → root cause → fix → regression test)

1. **ZCode hook handler invalid** (P1): extra `enabled` field violates ZCode's
   strict process-hook schema → hooks silently dropped. Fixed +
   `test_zcode_handler_matches_strict_process_hook_schema`.
2. **Windows `.cmd`/`.bat` tools failed under `pigeonhub run`** (P1): npm et
   al. are batch shims CreateProcess can't launch. Fixed via `cmd /c` +
   `test_run_starts_windows_batch_shims_via_cmd`; re-verified on the upgraded
   installed exe.
3. **SHA256SUMS self-hash line** (P2): excluded itself from itself.
4. **Worker duplicate-message behavior** (P2, not fixed here): on FCM failure
   the publish path stores the message then returns 502, so client retries
   create duplicates (17 rows across two dead-channel sessions tonight).
   Candidate follow-up: return stored-but-push-failed as a distinct status, or
   make the client treat `stored:true` as success before retry logic.

## P3 observations

- Config-file hooks are read once per ZCode process start; sessions started
  before `pigeonhub setup` never see them (documented; setup should be
  followed by a new session).
- Free-tier `QUOTA_MINUTE_LIMIT=5` surfaces 429s during rapid successive jobs;
  terminal publishes retry through it, RUNNING intentionally does not.
- The dev machine's stale credential (dead channel from a recreated emulator)
  was the root cause of the initial NotRegistered noise — a real user hitting
  this needs a clear "your pairing is stale, log in again" message; candidate
  UX improvement.

## Final questions (Section 46)

- **Q1 — Windows 신규 사용자는 Python을 설치해야 하는가?** NO (packaged exe).
- **Q2 — 사용자가 PATH를 직접 수정해야 하는가?** NO (installer manages HKCU
  Path; verified add/upgrade/uninstall).
- **Q3 — 설치 직후 사용자가 무엇을 해야 하는지 알 수 있는가?** YES — finish
  page + bare `pigeonhub` banner both say `pigeonhub login`.
- **Q4 — 첫 로그인 이후 범용 작업 추적은?** `pigeonhub run <command>` (실제
  설치 exe로 성공/실패/장기작업 검증).
- **Q5 — background daemon 또는 tray app을 추가했는가?** NO.
- **Q6 — 검증되지 않은 것을 VERIFIED라고 썼는가?** NO — Claude/Grok/물리 QR은
  DEFERRED_OWNER_ACTION으로 정확히 분리되어 있고, ZCode의 시뮬레이션 범위는
  각주로 명시했다.

## Owner actions (final list, ≤5)

1. Physical Galaxy camera QR E2E (proper re-pairing; replaces the
   test-minted token — SECURITY.md has the exact revocation command).
2. Claude quota top-up → one real `claude -p` session.
3. `zcode login` (or just restart the ZCode desktop app) for live ZCode
   sessions.
4. Codex `/hooks` trust (one-time, in the Codex TUI).
5. Code-signing certificate (optional, pre-wide-beta).
