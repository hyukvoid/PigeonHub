# FINAL REPORT — BETA-001B Local Recipes

Branch: `autonomous/beta001a-qr-pairing-locale-20260918` (continuation commit;
BETA-001A landed first on the same branch, exactly as the coordination rule
allows — no two agents shared the working tree).

CLI **0.19.0-beta.1** ships `pigeonhub recipe add|list|show|run|remove`:
save a command once, run it forever after as a first-class PigeonHub job —
with the command, prompt, and working directory never leaving the PC.

## Scoreboard

| Gate | Status |
|---|---|
| RECIPE_ADD | VERIFIED — flags + interactive wizard + EOF-safe, packaged exe too |
| RECIPE_LIST | VERIFIED — id + display name only; empty-state hint |
| RECIPE_SHOW | VERIFIED — structure + "Uses prompt", default prompt presence-only |
| RECIPE_RUN | VERIFIED — local worker E2E + packaged exe + real Codex run |
| RECIPE_REMOVE | VERIFIED — confirmation, `--yes`, recipe-only scope message |
| DUPLICATE_PROTECTION | VERIFIED — refuses without `--replace`; replace keeps created_at |
| OPTIONAL_PROMPT | VERIFIED — plain commands (crawler/ffmpeg/blender shape) run prompt-free |
| DEFAULT_PROMPT | VERIFIED — used when no runtime prompt; presence shown in show |
| RUNTIME_PROMPT | VERIFIED — overrides default; proven by argv-marker child |
| INTERACTIVE_PROMPT | IMPLEMENTED — TTY-only `Prompt:` input (unit-tested with mock TTY; no TTY in this harness) |
| PROMPT_FILE | VERIFIED — `--prompt-file` avoids shell history |
| ARGV_SAFE_INTERPOLATION | VERIFIED — `{prompt}` replaced inside tokens; spawn is argv-based |
| PROMPT_INJECTION_TEST | VERIFIED — `& whoami`, `| dir`, `> file`, `&& exit 99`, `%PATH%`, `!VAR!`, quotes, Korean: one argument, no side effects, child exit preserved |
| LOCAL_ONLY_STORAGE | VERIFIED — `%USERPROFILE%\.pigeonhub\recipes.v1.json`; nothing else written |
| ATOMIC_STORAGE | VERIFIED — temp+fsync+replace; corrupt file → loud error, bytes untouched |
| NO_PROMPT_SERVER_LEAK | VERIFIED — every worker-bound event deserialized in tests; prompt strings absent |
| NO_COMMAND_SERVER_LEAK | VERIFIED — same; RUNNING message is a fixed safe string |
| RUN_LIFECYCLE_REUSE | VERIFIED — recipes call `core.run_job`; zero new subprocess/lifecycle code |
| SUCCESS_JOB | VERIFIED — RUNNING→DONE (local worker + packaged exe) |
| FAILED_JOB | VERIFIED — RUNNING→FAILED on child failure |
| EXIT_CODE_PRESERVED | VERIFIED — child 42 → CLI 42 (unit + E2E) |
| LONG_JOB | VERIFIED — 68.7 s real run with PROGRESS×3 → DONE |
| CTRL_C | VERIFIED — wrapper+child stop promptly; no DONE published |
| WINDOWS_CMD_SHIM | VERIFIED — recipe-run `.cmd` routed through existing `cmd /c` path |
| OFFICIAL_AGENT_SINGLE_JOB | VERIFIED — hook events attach to parent `PIGEONHUB_JOB_ID`, no rename (unit + hook replay); the real recipe run created exactly one job |
| ZCODE_OR_CODEX_RECIPE_E2E | VERIFIED — real `codex exec` via recipe (exit 0, `BETA001B-OK`); caveat: installed Codex's own hook payload no longer maps to the MVP-017 adapter (pre-existing; owner follow-up) |
| PACKAGED_EXE | VERIFIED — all five verbs + version inside onefile exe |
| INSTALLER_UPGRADE | VERIFIED — 0.18→0.19 silent upgrade on the real install |
| CREDENTIAL_PRESERVED | VERIFIED — credentials.json sha256 identical across upgrade |
| RECIPES_PRESERVED | VERIFIED — recipes.v1.json survives upgrade; usable from installed exe |
| ANDROID_JOB_CARD | DEFERRED_OWNER_ACTION — card pipeline unchanged; emulator was cold-booted (no registered install/invite code); owner sees `Nightly crawler`-style cards on next real run |
| SECRET_SCAN | VERIFIED — clean (diff-scoped pattern scan); add-time credential warning shipped |
| REGRESSION | VERIFIED — Python 56/56; Android unit + assembleDebug + assembleRelease successful; `git diff --check` clean; Worker untouched |

## Final questions

**Q1 — 한 번 등록 후 `pigeonhub recipe run <name>` 만으로 실행되는가?** YES.
`add` once; `run` with no arguments beyond the id (prompt recipes resolve the
prompt from default/file/interactive without re-typing the command).

**Q2 — 프롬프트 또는 전체 command가 PigeonHub server로 전송되는가?** NO.
Tests deserialize every event a worker receives and assert the command,
prompt, and cwd strings never appear; the RUNNING message is a fixed safe
string and the card title is the recipe display name.

**Q3 — prompt에 shell 특수문자가 들어가도 추가 command가 실행될 수 있는가?** NO.
Real-process probes (`&`, `|`, `>`, `&&`, `%PATH%`, `!VAR!`, quotes, Korean)
arrive as one argv token; no side command ran, no file created, child exit
code unchanged. (Residual: `.cmd` shim recipes still run inside cmd —
documented in SECURITY.md.)

**Q4 — Recipe가 별도의 Job engine을 구현했는가?** NO.
`run_recipe` resolves prompt/argv/cwd and calls the existing `run_job`;
durability, retry, silent-failure, and shim handling are literally the same
code path.

**Q5 — Codex/ZCode 같은 native Agent를 Recipe로 실행하면 Job Card가 두 개
생기는가?** NO. Hooks treat an inherited `PIGEONHUB_JOB_ID` as the owning job
and attach to it without renaming the card. Verified by unit tests + hook
replay; the real Codex recipe run produced exactly one job. (Separate honest
finding: today's installed Codex hook payload fails MVP-017 event mapping, so
native enrichment didn't fire — a pre-existing adapter gap, not double-cards.)

**Q6 — Android에서 PC 명령을 원격 실행할 수 있는가?** NO. No Android changes;
the phone displays job cards only. No remote launch surface exists.

**Q7 — 새 CLI 도구가 나올 때마다 전용 connector가 필요한가?** NO. Generic tools
use `pigeonhub run` directly or a one-line recipe; official agents keep their
native integrations. Recipes exist precisely so the connector count stays at
four.

## Owner follow-ups

1. Android job-card screenshot on a registered device for the next real
   recipe job (card name = recipe display name).
2. Re-audit the installed Codex CLI's Stop-hook payload vs the MVP-017
   adapter (`pigeonhub/agents.py:_event_name`) — native enrichment currently
   no-ops for codex sessions.
3. Optional nice-to-have: `pigeonhub recipe run` for prompt recipes could
   offer `--prompt -` (stdin) in a future beta.
