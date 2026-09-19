# AGENT-UX — BETA-003 (Setup Center + Android guidance)

## Detection (read-only, simplified states)

`agents.detect_agent` already reads only: executable presence (`shutil.which`
+ `--version` probe) and the known, safe config location. The Setup Center
maps that to four user-facing states — Detected / Connected / Needs attention
/ Not detected (version shown as a small chip when available). Internal words
(hooks.json, adapter, JSONL, payload) are never shown.

Exactly four agents, fixed order: OpenAI Codex, Claude Code, ZCode · GLM,
Grok Build. No new agents.

## Connect dialog (per agent)

> **Connect OpenAI Codex**
> PigeonHub receives: ✓ task started ✓ progress ✓ task completed ✓ attention needed
> PigeonHub does NOT receive: ✗ your full prompt ✗ source code ✗ full transcript
> [Cancel] [Connect]

The dialog mirrors the real allowlist in `pigeonhub/agents.py` — the setup
engine cannot send more than the dialog promises.

## Engine reuse (no logic duplication)

A confirmed Connect calls `agents.build_setup_plan(agent)` → (blocked →
"Needs attention") → `apply_setup(plan)` with its existing backup/atomic/verify
behavior; Remove calls the same engine with `remove=True` and removes only
what PigeonHub added. No subprocess, no re-implemented config editing.
Unit tests pin the delegation and the no-op path (already-connected →
config untouched).

## Codex specifics

- **Trust**: never bypassed. After connect, the card offers "마지막 한 단계 /
  One last step — open Codex, review, choose Trust" with [Open Codex] and a
  [연결 확인] re-detect button. Trust cannot be verified from outside safely,
  so nothing claims Connected-trusted on its behalf.
- **exec mode**: the known fact that `codex exec` runs no hooks.json hooks is
  documented in Advanced/Help (`docs/reports/beta002-private-beta-readiness/
  CODEX-COMPAT.md` + KNOWN-REGRESSIONS), not in the main flow. Power users on
  exec keep the bridge; interactive users use the hooks integration.

## Claude

Connect runs the same engine; quota is deliberately out of scope — a missing
quota does not present as a failed integration.

## ZCode

Same strict schema fix as the CLI setup. After connect the card shows
"Connected ✓ — Restart ZCode to finish setup. [Open ZCode]" (restart hint
because a running ZCode reads hooks at process start). The user never sees
the config path.

## Grok

Not installed → "Not detected — Install Grok Build first." with no fake
Connect button. PigeonHub never installs agents itself.

## Connected state

"Connected ✓" uses the same source-specific health semantics as the CLI/Android
(HealthLine). No fake "Last event: just now" — an agent that has never
emitted an event shows none.

## Manage

Connected card → [Manage]: Test connection (re-runs the health fetch),
Reconnect (re-run the setup engine, idempotent), Remove integration (engine
remove). No raw config editor.

## Android (PART K)

Agent detail screens now say, above the advanced steps: "이 도구는 PC에서
설정해요 — 컴퓨터에서 PigeonHub Setup을 열고 <Agent>를 선택하세요." /
"Set this up on your PC — open PigeonHub Setup on your computer and choose
<Agent>." The Android app still cannot modify PC configs, has no remote
execution, and gains no deep links — guidance only (KO/EN strings added with
key parity).
