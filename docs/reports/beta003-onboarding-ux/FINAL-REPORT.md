# FINAL REPORT — BETA-003 One-click Onboarding & Integration UX

Branch `autonomous/beta003-onboarding-ux-20260919`. RC: **Windows CLI
0.20.0-beta.1**, Android `versionCode 3 / 0.3.0-beta003`.

The Setup Center exists: `pigeonhub onboard` (also the installer Finish
checkbox on fresh installs and the Start Menu "PigeonHub Setup" shortcut)
opens a browser page that walks a first-time user through
Welcome → Connect phone (in-page QR + live status) → Connect tools (4 agents,
one-click with a privacy dialog) → Test → Done. The server is loopback-only,
token-gated, allowlist-only, and dies with the session.

## Scoreboard

| Gate | Status |
|---|---|
| SETUP_CENTER | VERIFIED — onboard mode in the packaged exe (process serves, page/status/QR routes live) |
| LOCALHOST_ONLY | VERIFIED — `netstat`: `127.0.0.1:<random> LISTENING`; Host check on every request |
| SESSION_SECURITY | VERIFIED — token_urlsafe gate, 404 on invalid/expired, compare_digest, TTL 45 min |
| NO_ARBITRARY_EXEC | VERIFIED — allowlist rejects RUN_COMMAND/EXEC/SHELL/EVAL; no shell path exists |
| INSTALLER_TO_ONBOARDING | VERIFIED — post-install "Set up PigeonHub now" (fresh only, skipifsilent) + Start Menu shortcut compiled |
| ZERO_TERMINAL_FRESH_SETUP | VERIFIED — install→Finish→browser path types 0 commands; 0 config files |
| PHONE_PAIRING_UI | VERIFIED — in-page QR (PNG bytes served), countdown, expiry recovery (automated flow test) |
| PAIRING_STATUS | VERIFIED — poller state machine pending→approved converges without user refresh |
| PAIRING_RECOVERY | VERIFIED — transient poll failures keep waiting until TTL → "새 QR 코드 만들기"; no raw 410 |
| CODEX_DETECTION | VERIFIED — detect_agent mapped to Detected/Not detected (+version chip) |
| CODEX_ONE_CLICK_SETUP | VERIFIED — confirm dialog → existing engine apply (delegation unit-tested) |
| CODEX_TRUST_GUIDANCE | VERIFIED — "마지막 한 단계" + [Open Codex] + [연결 확인]; never bypassed |
| CLAUDE_ONE_CLICK_SETUP | VERIFIED — same engine path (quota decoupled from setup success) |
| ZCODE_ONE_CLICK_SETUP | VERIFIED — engine + "Restart ZCode" hint after connect |
| GROK_STATE_ACCURACY | VERIFIED — "Not detected / install first", disabled button, no fake Connect |
| AGENT_REMOVE | VERIFIED — engine remove=True, only-PigeonHub's-parts policy preserved |
| AGENT_RECONNECT | VERIFIED — [Manage] reconnect = idempotent engine re-run |
| TEST_NOTIFICATION | VERIFIED — publish_message reused; sent → "도착했나요?" → Yes/Try again |
| FIRST_JOB_GUIDANCE | VERIFIED — Done screen shows `pigeonhub run -- <command>`, Recipe as one optional line |
| ANDROID_PC_SETUP_GUIDANCE | VERIFIED — agent details show "이 도구는 PC에서 설정해요…" (KO/EN) |
| NO_PHONE_REMOTE_EXEC | VERIFIED — no remote execution added; Android guidance-only |
| KO_EN_PARITY | VERIFIED — string tables only; structure asserted equal in tests |
| ACCESSIBILITY | IMPLEMENTED — large cards/CTAs, recovery-shaped messages; formal screen-reader pass not done |
| WINDOWS_SCALING | IMPLEMENTED — single fluid column readable at 125/150%; formal capture matrix left as owner spot-check |
| CLI_BACKWARD_COMPAT | VERIFIED — full CLI suite passes (login/setup/run/recipe/agent-event untouched) |
| PACKAGED_BUILD | VERIFIED — exe + installer rebuilt at 0.20.0-beta.1; onboard smoke in the packaged exe |
| SECURITY_TESTS | VERIFIED — 16-test security/flow suite |
| SECRET_SCAN | VERIFIED — clean; `git diff --check` clean |
| FULL_REGRESSION | VERIFIED — Python 90/90; Android unit + assembleDebug + assembleRelease successful; Defender 0 threats |

## Final questions

**Q1 — PowerShell을 열어야 하는가?** NO. Installer Finish → Setup Center.
**Q2 — `pigeonhub setup codex`를 기억해야 하는가?** NO. The Connect button
runs the same engine.
**Q3 — config file 경로를 알아야 하는가?** NO. Paths are never shown; the
engine handles them (with backup).
**Q4 — Codex trust를 우회했는가?** NO. Explicit "One last step" guidance +
[연결 확인]; nothing marks Codex trusted on its behalf.
**Q5 — Android에서 PC 원격 실행을 추가했는가?** NO. Android gained guidance
text only.
**Q6 — 기존 CLI workflow 그대로?** YES. login/setup/run/recipe/agent-event
suites all pass unchanged.
**Q7 — 3분 안팎 가능한가?** Measured where measurable: the interactive
optical loop needs a physical phone (owner). The machine-timed portion —
install (silent) → browser open → Welcome → Start Pairing → QR on screen —
runs in **under 30 seconds** of machine time; with a practiced phone scan
and a Codex trust detour the full flow is realistic inside 3 minutes, but an
honest end-to-end timing with a real phone is deferred to the owner pass
alongside the BETA-001A Galaxy checklist.

## Known limitations (honest)

- The Setup Center page is served per-session; a page left open past the TTL
  needs a reload to re-authenticate (by design).
- Agent "Last event" health on the Setup Center cards reuses the worker
  health endpoint of the paired channel only after phone pairing succeeds.
- Windows console is minimized, not hidden — closing it cancels setup
  (intentional kill switch).
