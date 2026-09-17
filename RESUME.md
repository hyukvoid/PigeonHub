# RESUME — MVP-017.5 + MVP-018 overnight campaign (2026-09-18)

Branch: `autonomous/mvp0175-mvp018-zcode-overnight-20260918` (from `36e2cd5`).
Previous RC state (MVP-017) is at `36e2cd5`; this branch adds production
validation + the Windows distribution.

## Verdicts

- **MVP-017.5: VERIFIED (with named deferrals).** schema_013 applied to
  production D1; worker redeployed (`72805dc3`); Codex real-session E2E
  (RUNNING+DONE→Android, `fcm_accepted`); ZCode hook-chain E2E (5-state
  lifecycle→card+shade, vendor payload simulated — standalone CLI login is an
  owner action); Claude setup gates verified, real session blocked by 402
  quota (owner); Grok not installed (owner). Two real bugs found+fixed:
  ZCode strict-schema hook field, Windows `.cmd` shim launches.
- **MVP-018: CONDITIONAL_WINDOWS_BETA_READY.** `0.18.0-beta.1`: PyInstaller
  onefile + Inno Setup per-user installer, PATH-safe, credential-preserving,
  Defender-clean, checksums published. Full QA matrix in
  `docs/reports/mvp0175-mvp018-overnight/INSTALLER-QA.md`.

## Current environment state (what a follow-up session walks into)

- Windows: pigeonhub CLI **installed** at
  `%LOCALAPPDATA%\Programs\PigeonHub` (0.18.0-beta.1 with the `.cmd` fix), on
  the user PATH; credentials bound to the **live device channel**
  `ch_d7c527c2…` via a **test-minted** connector token (revocation command in
  SECURITY.md; original dead-channel credential backup in
  `%TEMP%\credentials-backup.json`).
- Agent hooks **installed**: zcode (user config, strict-schema-conformant),
  codex (`~/.codex/hooks.json`, needs one-time `/hooks` trust),
  claude (`~/.claude/settings.json`, fires once quota returns). Backups:
  `*.pigeonhub.bak.*` next to each file; `pigeonhub setup <agent> --remove`
  reverses cleanly.
- Emulator `Medium_Phone_API_36.1` running the **current release APK**
  (installed with `adb install -r`, channel intact); Inbox shows the Codex ×2
  + Zcode evidence cards.
- Inno Setup 6.7.3 installed (user scope, winget); PyInstaller build venv at
  `%TEMP%\ph-build-venv`.
- `dist/` + `packaging/windows/{dist,installer}/` artifacts are gitignored;
  rebuild with `powershell -File packaging/windows/build.ps1`.

## Verified gates this campaign

Python 14/14 · worker typecheck · Android unit+assembleDebug+assembleRelease ·
`git diff --check` · scoped secret scan clean · Defender scan clean ·
SHA256SUMS verified.

## Owner actions (exact, ≤5)

1. Physical Galaxy camera QR E2E → proper PC re-pairing → revoke the
   test-minted token (command in SECURITY.md).
2. Claude quota → run one `claude -p` task (hooks already installed).
3. `zcode login` (browser OAuth) OR just restart the ZCode desktop app — then
   real ZCode sessions flow through the verified hooks.
4. Codex TUI `/hooks` → trust the PigeonHub handlers (one-time).
5. Code-signing certificate (optional before wide beta; SmartScreen otherwise).

## Next product steps (highest value first)

1. **Worker: fix stored-but-502 duplicate behavior** (FINAL-REPORT §Findings
   #4) — return a distinct "stored, push failed" status so client retries
   don't duplicate rows.
2. **CLI: stale-credential UX** — when FCM says NotRegistered for the paired
   channel, tell the user to `pigeonhub login` again instead of raw failures.
3. **Worker: stale RUNNING job sweeper** (carried from MVP-011.5 RESUME) —
   hard-killed jobs stay RUNNING forever tonight.
4. Winget manifest when the certificate lands (pre-repo PR can be prepared
   without publishing).

## Watch-outs

- ZCode reads hook config at process start — after any `pigeonhub setup
  zcode`, only NEW ZCode sessions fire hooks.
- Codex hooks silently no-op until trusted via `/hooks`; only
  `--dangerously-bypass-hook-trust` runs them per-invocation.
- Free-tier quota: `QUOTA_MINUTE_LIMIT=5` 429s under rapid jobs; terminal
  publishes retry (8×), RUNNING does not (by design).
- uiautomator dump paths in Git Bash need `//sdcard/ui.xml` (MSYS mangling);
  release-build nav has no Diagnostics tab; emulator clock skew ~40–60 s.

---

# Previous session resume (MVP-011.5→015, kept for history)

# RESUME — MVP-017 AI Agent Integrations v1 (2026-09-17)

Branch: `autonomous/mvp017-agent-integrations-codex-20260917`.
RC-001A Artemis UX was checkpointed at `eb2e65c` before this branch began.

## Current state

- `pigeonhub login/logout/status/notify/progress/needs-action/run` is implemented
  as the dependency-light `pigeonhub/` package with a `pyproject.toml` console
  entry point.
- PC-first login creates a short-lived request, displays a terminal QR, waits
  for Android approval, and atomically stores connector credentials.
- Android Connections has a generic PigeonHub CLI PC card, Camera2 + ZXing
  scanner, and explicit approval dialog. The old phone-issued `pair` path is
  retained for compatibility.
- `run` publishes RUNNING before process start, refuses to start on tracking
  failure, injects `PIGEONHUB_JOB_ID`, preserves child stdout/stderr/exit code,
  and reports terminal publish failures without masking that exit code.
- Worker PC-first pairing routes are typechecked; apply `worker/schema_013.sql`
  before using the flow against a deployed Worker. *(Superseded: applied +
  deployed in the MVP-017.5 campaign.)*
- `pigeonhub/agents.py` normalizes Codex, Claude Code, Grok Build, and
  ZCode lifecycle hooks into the existing Job Model with an allowlist privacy
  boundary. *(Superseded detail: the zcode handler had a strict-schema bug,
  fixed in MVP-017.5.)*
- `pigeonhub setup <agent>` provides preview, explicit confirmation, backup,
  atomic apply, verification, idempotency, and safe removal for vendor-native
  user hook/config files.
- Android's primary AI grid is exactly OpenAI Codex, Claude Code, Grok Build,
  and ZCode · GLM; Custom Agent remains Advanced only.

## Verified in that session

- Python CLI/adapter tests 10/10; worker typecheck; Android debug assemble +
  unit tests; release assemble; `git diff --check`.
- Codex `0.152.1` and Claude Code `2.1.88` detected. Grok not installed;
  ZCode had no enabled hook events → real-agent E2E deferred (largely closed
  in MVP-017.5; see that report).

# RESUME — MVP-011.5→015 campaign (2026-09-15, kept for history)

Branch: `autonomous/mvp0115-mvp015-predeploy-overnight-20260914` (from `b88b335`).

- **MVP-011.5, 012, 013, 014 (Codex), 015 — all VERIFIED.**
- Deployed worker then: `5e11002f`; D1 migrations schema_011/012 applied.
- Android release APK on emulator-5554 (`Medium_Phone_API_36.1`), channel
  `ch_b00fc82923b64b3399c4` (later recreated as `ch_d7c527c2…`).
- ComfyUI real install at `tools/comfyui` (gitignored) + venv.
- Watch-outs that still hold: AVD `PigeonHub-E2E-API36` wedged — use
  `Medium_Phone_API_36.1`; release builds log quietly (verify via UI/dumpsys);
  emulator clock ~40–60 s behind; one tap per adb command, re-dump before taps.
