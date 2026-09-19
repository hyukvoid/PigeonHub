# KNOWN-ISSUES — Private Beta (0.19.0-beta.2 / Android 0.2.0-beta001)

Real, current, user-visible issues only. Resolved items are removed.

1. **Unsigned Windows binaries** — SmartScreen shows "Windows protected your
   PC" on installer/exe first run; click "More info → Run anyway". Code
   signing is planned before broad distribution.
2. **Codex hooks do not run in `codex exec` mode (codex-cli 0.152.1,
   verified by experiment 2026-09-19)** — a Stop/SessionStart hook in
   `~/.codex/hooks.json` is not executed by exec-mode sessions, so native
   lifecycle enrichment does not fire there. Interactive sessions are the
   hooks path (Codex may ask you to trust the hooks via `/hooks`). For
   `codex exec`, use the published bridge
   (`connectors/agent_bridge_codex.py`, verified this campaign) or accept
   the plain recipe wrapper card.
3. **The Codex bridge combination needs a repo checkout** —
   `connectors/agent_bridge_codex.py` is a repo script, so the packaged
   `pigeonhub.exe` cannot launch it by that path. Users running from the
   installer still get a normal recipe wrapper card (RUNNING→DONE) for
   Codex/other agents.
4. **Claude real-session E2E still unverified** — blocked on Claude quota
   (owner); the adapter and tests exist.
5. **Grok Build is not installed on the QA machine** — adapter and setup
   exist; no local real-session verification.
6. **Physical Galaxy QR pairing** — the pairing UX was verified on an
   emulator and the packaged exe; the final real-Galaxy pass is the owner's
   6-step checklist in `docs/reports/beta002-private-beta-readiness/
   PRIVATE-BETA-CHECKLIST.md`.
