# GOAL — BETA-003 One-click Onboarding & Integration UX (2026-09-19)

Branch: `autonomous/beta003-onboarding-ux-20260919` (base: the BETA-002 HEAD
`9076e88`). RC after this campaign: **Windows CLI 0.20.0-beta.1**, Android
`versionCode 3 / 0.3.0-beta003`.

A first-time user should connect a phone and an AI agent without knowing what
a terminal command or a config file is — while Phone → PC arbitrary command
execution stays forever out of scope. PigeonHub remains a Long-running Job
Inbox; nothing here turns it into a remote controller.

## The shape of the solution

An **Ephemeral Local Setup Center**: `pigeonhub onboard` starts an HTTP server
on `127.0.0.1` (random port), opens the default browser, and walks the user
through Welcome → Connect phone → Connect tools → Test → Done. The server dies
with the session. No daemon, no service, no tray, no startup process, no
persistent listener.

- The installer's Finish page gains "Set up PigeonHub now" (fresh installs
  only — upgrades never auto-launch), and a Start Menu **PigeonHub Setup**
  shortcut runs the same onboard mode. No launcher app: it is the existing
  exe in an onboard mode, with its console minimized.
- Every action reuses the validated engines: QR pairing is the MVP-016
  protocol (short-lived request + challenge, poll secret memory-only), agent
  connect/remove is the existing `pigeonhub setup` engine (preview → backup →
  atomic apply → verify), test notification is `publish_message`. The Setup
  Center is a UI shell, not a second implementation.
- CLI fallback untouched: `pigeonhub login` / `pigeonhub setup <agent>` keep
  working and remain the documented power-user path.
- Android only gains guidance: agent details say the integration is set up on
  the PC ("이 도구는 PC에서 설정해요…"). No remote execution, no config
  mutation, no deep links.

## Security posture (tested)

Loopback-only bind; every request gated by a cryptographically random session
token (invalid/expired = 404 — the port's existence is never confirmed);
mutations are POST-only with same-origin (Origin/Referer vs Host) checks and
an explicit `confirm` flag; actions are a fixed allowlist
(START_PAIRING, SETUP_*, REMOVE_*, TEST_NOTIFICATION, SET_LANG, FINISH) —
there is no endpoint that can execute commands; the poll secret, connector
token, and config contents never appear in any response; the session token is
never logged, written to disk, or embedded in the page HTML (the page reads it
from its own URL at runtime).
