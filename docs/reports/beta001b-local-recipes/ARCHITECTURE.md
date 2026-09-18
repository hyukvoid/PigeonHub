# ARCHITECTURE — Local Recipes (BETA-001B)

```
pigeonhub recipe run <id>
   │
   ├─ load recipes.v1.json (local, atomic read)
   ├─ resolve prompt locally     --prompt  >  --prompt-file  >  default_prompt  >  interactive (tty)
   ├─ build argv                 {prompt} replaced INSIDE individual tokens only
   ├─ validate                   executable on PATH (fails BEFORE any job), cwd exists
   └─ delegate to existing run lifecycle (core.run_job)
          ├─ publish RUNNING (source="recipe", name=recipe display name,
          │                    message="Started from a PigeonHub recipe.")
          ├─ spawn child (existing Windows .cmd/.bat shim handling),
          │  env gets PIGEONHUB_JOB_ID / _JOB_NAME / _STATE as in `pigeonhub run`
          └─ publish DONE/FAILED, return the CHILD's exit code unchanged
```

## Why local-only?

A recipe is a personal muscle-memory shortcut. The moment definitions live on
a server you need accounts for sharing, sync conflicts, and a moderation
surface for arbitrary commands. Local-only keeps the feature one file wide and
removes an entire threat class (remote command templates = stored RCE for
anyone who gains write access).

## Why no remote launch (phone → PC)?

Remote launch converts the Job Inbox (a notification product) into a remote
execution product: attack surface (auth, command injection from the phone),
new failure modes, and a trust model change. The inbox tells you about work;
it never starts work.

## Why delegate to `pigeonhub run`?

One lifecycle to maintain: durability/idempotency, silent-failure policy
(RUNNING publish failure = command never starts), Windows shim handling,
child env injection, exit-code contract. A second engine would fork all of
that. Recipes add exactly two things: prompt resolution and template → argv.

## Why the prompt never reaches PigeonHub?

The prompt is frequently the most sensitive part of an AI command (it names
the target repo, the bug, sometimes credentials-adjacent context). The worker
allowlist already carries only lifecycle fields; for recipes the RUNNING
message is a fixed string instead of the command line, and the title is the
recipe's display name — so a card never shows or transmits command/prompt.

## Why argv templates instead of raw shell interpolation?

`template.replace("{prompt}", p)` + `shell=True` is command injection by
construction (`hello" & del *`). Recipes store an argv vector and the
substitution happens inside individual tokens; the prompt is argument DATA to
one program, and no shell ever parses it. Injection probes (`& whoami`,
`| dir`, `> file`, `&& exit 99`, `%PATH%`, `!VAR!`, quotes, Korean text)
arrive as one argument, byte-for-byte, with the child's exit code unchanged.

## Why this avoids N dedicated connectors?

Every new CLI tool does not need a PigeonHub connector; it needs one recipe.
Official agents keep their native integrations (richer events, NEEDS_ACTION);
everything else is `pigeonhub run` → optional Recipe. The product structure is:

```
Official Agents (Codex/Claude/Grok/ZCode) → native integration
Everything else                           → pigeonhub run → optional Recipe
```

## Agent collision (one execution = one card)

`pigeonhub run`/recipes inject `PIGEONHUB_JOB_ID` into the child. Native agent
hooks now treat an existing `PIGEONHUB_JOB_ID` as the owning job id: hook
events attach to the parent card (`stable_id = parent_job or raw job_id or
session`) and do NOT send a job_name, so the card keeps the recipe's display
name while gaining the agent's richer lifecycle (progress, NEEDS_ACTION).
Verified by unit tests and a manual hook replay; see E2E-EVIDENCE.md for the
real Codex run and one honest caveat about the current Codex hook payload.
