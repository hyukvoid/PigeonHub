# MVP-017 — AI Agent Integrations v1

Date: 2026-09-17
Branch: `autonomous/mvp017-agent-integrations-codex-20260917`

## Goal

Let a PigeonHub user choose one of exactly four supported coding agents, run
one setup command, and receive safe long-running lifecycle updates in the
existing Job Inbox:

1. OpenAI Codex
2. Claude Code
3. Grok Build
4. ZCode · GLM

The product remains a Long-running Job Inbox. PigeonHub does not become a
remote agent controller, shell, prompt relay, conversation UI, or approval
proxy.

## Scope

| Area | In scope |
| --- | --- |
| Adapter | Native/user-level vendor hook → allowlisted normalized event |
| Lifecycle | `RUNNING`, `PROGRESS`, `DONE`, `FAILED`, `NEEDS_ACTION` |
| Setup | `pigeonhub setup <agent>` with preview, confirmation, backup, apply, verify |
| Removal | `pigeonhub setup <agent> --remove`, deleting only PigeonHub-managed entries |
| Android | Four official cards, safe descriptions, status, health line, setup action |
| Privacy | No prompts, transcripts, source files, raw tool input, env, API keys, or raw logs |

## Explicitly out of scope

- phone → agent commands or remote shell
- remote approval/deny execution
- prompt injection or transcript mirroring
- ChatGPT separate card, Antigravity, Gemini CLI, Cursor, Copilot, custom-agent marketplace
- a generic custom agent in the primary AI grid (the existing Advanced path remains)

## Stop rule

If Grok or ZCode is not installed or cannot run a real session in this
environment, record `DEFERRED_OWNER_ACTION` and continue the other agents.
Synthetic hook calls are never reported as real-agent E2E.

## Final product question

For installed and authenticated supported agents, the answer is **YES**: pick
one of the four cards, run setup once with confirmation, keep using the agent,
and receive lifecycle status without remembering lifecycle commands. Agents
that are absent on the PC remain visibly owner-action/deferred rather than
being falsely shown as connected.
