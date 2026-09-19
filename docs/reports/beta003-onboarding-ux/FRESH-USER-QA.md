# FRESH-USER-QA — BETA-003

## Measured flow (this machine, 0.20.0-beta.1 artifacts)

| Step | How | Result |
|---|---|---|
| Install | silent installer run during QA (and interactive layout compiled) | OK |
| Finish → Setup Center | `[Run]` entry "Set up PigeonHub now" (fresh installs only, `skipifsilent`) | wired; fresh/upgrade split via uninstall-key check |
| Start Menu | "PigeonHub Setup" → `pigeonhub.exe onboard` | shortcut compiled into the installer |
| Onboard entry (packaged exe) | `dist/pigeonhub.exe onboard` | process stays alive serving the Setup Center |
| Loopback binding | `netstat` on the running PID | `TCP 127.0.0.1:51283 LISTENING` — loopback only, random port |
| Session contract in packaged exe | tokenless `GET /setup` | **404** — existence never confirmed without the token |
| Ephemeral lifetime | process terminated | server gone with the process (no daemon/service/listener left) |
| Terminal commands a fresh user must type | installer → Finish path | **0** |
| Config files a fresh user must open | full phone+agent flow | **0** |

Interactive phone-pairing through the Setup Center needs a physical phone —
the pairing protocol, QR rendering, and status machine itself are covered by
the automated flow test (pending → approved → credential save) plus the
BETA-001A real-device checklist that remains with the owner.

## Functional matrix (automated unless noted)

| Case | Result |
|---|---|
| Fresh onboarding (server/page/status/QR) | PASS (tests) |
| Returning user | PASS — fresh token/port per run; nothing persisted |
| Phone already paired | PASS — `status()` shows connected; TEST_NOTIFICATION path works with existing credentials |
| Phone stale pairing | PASS — poll errors keep waiting until TTL → "새 QR 코드 만들기" recovery |
| Codex detected / absent | PASS — detect_agent states mapped to Detected / Not detected |
| Claude detected, ZCode detected | PASS — same engine path |
| Grok absent | PASS — "Not detected" + install-first hint, disabled button |
| Reconnect / remove | PASS — engine delegation + no-op safety (unit-tested) |
| Reinstall | PASS — installer upgrade tested in BETA-002; new installer compiles with onboarding entries |

## Language & scaling

- KO/EN: string tables only; identical section structure asserted in tests
  (same section count, both entry strings present).
- Language toggle = session preference; no account subsystem.
- Layout is a single centered column (max-width 760px, large CTAs, large
  cards) — readable at 1366×768 and 1920×1080; Windows 125/150% scaling only
  scales text (browser). Formal screenshot matrix on scaled displays remains
  an owner spot-check.
