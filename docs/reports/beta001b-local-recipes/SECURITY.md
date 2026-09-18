# SECURITY — BETA-001B Local Recipes

## Threat model

Recipes store arbitrary local commands plus optional prompts on disk and run
them with the user's own privileges. The risks to control:

1. Prompt → command injection (`shell=True` style).
2. Secrets captured inside recipes (on disk, in plaintext).
3. Sensitive prompt/command content leaking to the PigeonHub worker/notifications.
4. Recipe file corruption turning into data loss.

## 1. Injection — argv is the boundary

- The template is stored as an argv vector; `{prompt}` is substituted inside
  individual tokens. The child is spawned with an argv list — no shell —
  except the pre-existing, intentional `.cmd`/`.bat` → `cmd /c` shim from
  MVP-018, where the prompt still travels as one quoted argument token.
- Real-process probes: `hello & whoami | dir > injected.txt && exit 99
  " quoted " %PATH% !VAR! 한글 프롬프트 & echo PWNED` arrives as ONE argument,
  `injected.txt` is never created, no `whoami`/`PWNED` output, child exit
  code preserved (unit + subprocess E2E tests).
- Known residual risk (documented, unchanged): `cmd /c` batch shims run in a
  shell context; a prompt containing `%` sequences could be subject to cmd's
  own variable expansion *inside that one argument* for `.cmd` recipes. The
  probe above includes `%PATH%` and showed no expansion in the argv-receiving
  child. Batch-file argument parsing remains cmd's, not ours.

## 2. Secrets

- Warning heuristic on `add` (interactive: explicit y/N confirm, default
  refuse): OpenAI `sk-`, GitHub `ghp_`/`github_pat_`, AWS `AKIA…`, Slack
  `xox…`, PEM headers, and `api[_-]?key|secret|password|token|bearer …=` —
  in both argv and default prompt.
- Honest limit: it is a pattern match, NOT a boundary. Recipes are plaintext;
  the CLI says so at warning time. Prefer env vars / the tool's own auth.

## 3. Server privacy boundary

Recipe jobs publish ONLY: `job_id` (`recipe-…`), safe display name, state,
timestamps, progress counters, and generic result metadata (`exit code N` /
"Started from a PigeonHub recipe."). NEVER: argv, prompt, default prompt,
cwd, env, stdout/stderr. Enforced by passing an explicit safe `message` into
`run_job` and keeping the card title = recipe display name. Verified by
tests that deserialize every event the local worker received and assert
absence of the command and prompt strings.

## 4. Durability

Atomic writes (temp → fsync → replace). Unreadable file → loud `CliError`,
file untouched (test asserts bytes are identical after the failed call).

## Ops hygiene during this campaign

- No real credentials, prompts, or token material copied into reports;
  the smoke tests use dummy values (`pct_x`, `ch_pkg`).
- Installer upgrade was exercised on the real install; `credentials.json`
  hash pre/post is identical; no local files were removed.
- The `qa-upgrade-probe` recipe created for the upgrade test was deleted
  through `recipe remove --yes` (also proving remove in the installed exe).
- Side observation (pre-existing, NOT a recipe regression): the installed
  Codex CLI's Stop-hook payload no longer maps to the MVP-017 adapter, so a
  native codex session does not publish a lifecycle event today. The
  parent-job convergence mechanism itself was verified by hook replay
  (`PIGEONHUB_JOB_ID` + `{"hook_event_name":"Stop"}` → DONE attached to the
  recipe job). Codex hook payload re-audit is filed as an owner follow-up.
