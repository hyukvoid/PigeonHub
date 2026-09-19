# FINAL REPORT — BETA-002 Private Beta Readiness + Dogfood Harness

Branch `autonomous/beta002-private-beta-readiness-20260919` (base `9b053d9`).
RC: **Windows CLI 0.19.0-beta.2**, Android unchanged (`versionCode 2`,
`0.2.0-beta001`).

## Verdict: **CONDITIONAL_PRIVATE_BETA_READY**

The product passes every automated gate and the fresh-user track on Windows;
the condition is the owner's real-Galaxy pairing pass (checklist item 1) —
the pairing UX is emulator- and packaged-exe-verified, but a physical phone
has not scanned the new QR in anger yet. Once that and the token revocation
(	item 2) are done, this becomes PRIVATE_BETA_READY without further code
changes unless the dogfood surfaces a P0/P1.

## Scoreboard

| Gate | Status |
|---|---|
| CODEX_CURRENT_PAYLOAD | VERIFIED — codex-cli 0.152.1 public `exec --json` stream captured and pinned as sanitized fixtures; hooks.json hooks proven NOT to execute in exec mode (marker experiment); interactive hook payload mapping still covered |
| CODEX_REAL_E2E | VERIFIED — real `codex exec` via bridge inside a recipe: RUNNING → PROGRESS×2 → DONE, exit 0 (production worker) |
| CODEX_RECIPE_SINGLE_JOB | VERIFIED — bridge events converged on the single recipe card (`recipe-…`, name preserved); two cards = FAIL did not happen |
| FRESH_WINDOWS_INSTALL | VERIFIED — silent install over beta.1, registry + exe agree on 0.19.0-beta.2 |
| NO_PYTHON | VERIFIED — Python segments stripped from PATH (Store stub only); everything runs |
| PATH | VERIFIED — `where pigeonhub` resolves to the per-user install; exactly one PATH segment after upgrade/reinstall |
| FIRST_RUN | VERIFIED — bare `pigeonhub` shows connected/not-connected guidance: login, run, one Recipe hint |
| PACKAGED_LOGIN | VERIFIED — installed exe: request → QR PNG rendered → poll → approved → credentials saved (local worker, exit 0) |
| PACKAGED_RUN | VERIFIED — installed exe: `run --name "Fresh user first job" -- ping -n 6 127.0.0.1` → RUNNING→DONE, exit 0 (production worker) |
| FRESH_ANDROID_INSTALL | VERIFIED — clean emulator, release APK, first launch onboarding KO + EN (screenshots); registered-inbox walkthrough is owner's |
| KO_EN_PARITY | VERIFIED — onboarding captures in both locales, identical structure; string keys 230=230 (BETA-001A scripted diff, unchanged) |
| QR_PAIRING_READY | VERIFIED (code/emulator/packaged-exe level) — real-device pass = owner checklist item 1 |
| RECIPE_ADD | VERIFIED — packaged exe smoke this campaign |
| RECIPE_RUN | VERIFIED — packaged exe (RUNNING→DONE) + source suite |
| RECIPE_UPGRADE_PRESERVED | VERIFIED — recipes survive beta.1→beta.2 upgrade and uninstall/reinstall |
| RECIPE_PRIVACY | VERIFIED — BETA-001B event-deserialization tests unchanged and passing; RUNNING message stays a fixed safe string |
| NETWORK_BEFORE_RUNNING | VERIFIED — no silent untracked execution (exit 78, zero events) |
| NETWORK_AFTER_RUNNING | VERIFIED ★ — connection drop on terminal publish → same-key retry → stored once |
| IDEMPOTENCY | VERIFIED — same-key retry tests (flaky 503, drop ★, stored-failed) all pass |
| DEAD_PAIRING_RECOVERY | VERIFIED ★ — clear "pairing can no longer reach your phone → pigeonhub login" guidance, raw FCM error suppressed |
| RECIPE_CORRUPTION_RECOVERY | VERIFIED — unparsable file → loud error, bytes untouched |
| PROCESS_DEATH | VERIFIED (pre-registration scope) — force-stop/relaunch stable on clean emulator; durable inbox restore is unchanged MVP-019 code + owner pass |
| DURABLE_INBOX | DEFERRED_OWNER_ACTION — needs a registered device (invite code); code and unit tests unchanged |
| ATTENTION_POLICY | VERIFIED — unchanged code paths guarded by unit tests (no pushes for RUNNING/PROGRESS; DONE normal; FAILED/NEEDS_ACTION high) |
| STALE_RUNNING | VERIFIED — soft marker unchanged (unit-tested) |
| CONNECTION_HEALTH | VERIFIED — per-source health unchanged (unit-tested; BETA-001B suite) |
| PRIVATE_BETA_BUNDLE | VERIFIED — `dist/beta-bundle/`: exe, installer, APK, QUICKSTART, KNOWN-ISSUES, SHA256SUMS (5/5 verify) |
| QUICKSTART | VERIFIED — 6 steps, OS-native first job (`ping`), Recipe optional |
| SHA256 | VERIFIED — generated + verified 5/5 |
| DEFENDER | VERIFIED — custom scan of the bundle: 0 threats |
| SECRET_SCAN | VERIFIED — clean; `git diff --check` clean |
| FULL_REGRESSION | VERIFIED — Python 63/63; worker typecheck clean; Android unit + assembleDebug + assembleRelease successful |

## Final product questions

**Q1 — clone 필요?** NO. Installer only; PATH registration included.
**Q2 — Python 필요?** NO. Packaged onefile runs with Python absent from PATH.
**Q3 — 설치 직후 다음 행동을 아는가?** YES. Bare `pigeonhub` says: start with
`pigeonhub login`, then `pigeonhub run -- <command>` (Recipe = one optional
hint line).
**Q4 — 첫 Job이 설명서 없이 가능?** YES. QUICKSTART's one-liner
(`ping -n 6 127.0.0.1`) ran on the installed exe: RUNNING → DONE, exit 0.
**Q5 — 반복 command를 Recipe로?** YES. `recipe add` once, `recipe run` after;
survives upgrades.
**Q6 — Codex를 Recipe로 실행 시 Job 두 개?** NO. Recipe+bridge events
converge on one card (verified with the real Codex this campaign).
**Q7 — prompt/command/cwd가 server에 저장?** NO. Recipe jobs publish display
name + lifecycle only; event-deserialization tests assert absence.
**Q8 — 남은 P0 reliability bug?** NONE known. Six documented non-P0 items in
KNOWN-REGRESSIONS.md.
**Q9 — 지금 3~5명에게 보내도 되는가?** **CONDITIONALLY YES** — after the
owner completes checklist items 1–2 (real Galaxy pairing + old-token
revocation), the bundle is sendable to 3–5 people with the SmartScreen caveat
(item 1 of KNOWN-REGRESSIONS) stated up front. Without the Galaxy pass: NO —
that pass is the exact thing private beta would otherwise discover the hard
way.

## After this campaign

No new feature work. Next: owner checklist → 5–7 day dogfood (20+ real jobs,
journal) → P0/P1 fixes only → 3–5 person private beta.
