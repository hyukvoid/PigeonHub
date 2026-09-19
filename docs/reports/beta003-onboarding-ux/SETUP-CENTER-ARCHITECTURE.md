# SETUP-CENTER-ARCHITECTURE — BETA-003

```
pigeonhub onboard (or installer/Start-Menu shortcut → same exe, same mode)
   │
   ├─ console window minimized (SW_MINIMIZE; user can close it to cancel)
   ├─ SetupCenter()          session token (secrets.token_urlsafe(24)),
   │                         lang, PairingState, agent_events, stop event
   ├─ ThreadingHTTPServer(("127.0.0.1", 0), SetupHandler)   ← loopback, random port
   ├─ webbrowser.open("http://127.0.0.1:<port>/setup?session=<token>")
   └─ serve_forever()        ends on FINISH action, console close, or Ctrl+C
```

## Modules

- `pigeonhub/onboard.py` — server, session gate, pairing poller, action
  dispatcher. Delegates to `pigeonhub.core` (pairing request/poll, QR PNG,
  credential save, test notification) and `pigeonhub.agents`
  (build_setup_plan / apply_setup / remove_setup / detect_agent).
- `pigeonhub/onboard_pages.py` — the page (KO/EN strings, inline CSS/JS, no
  external assets, no brand re-draws).
- `pigeonhub/cli.py` — `onboard` subcommand only.

## Routes

| Method | Path | Gate | Purpose |
|---|---|---|---|
| GET | `/setup` (and `/`) | session | page HTML (token NOT embedded; page reads `location.search`) |
| GET | `/api/status` | session | read model: pairing status/timer, agent states, last test |
| GET | `/api/qr` | session | current QR PNG (only while `waiting`) |
| POST | `/api/action` | session + same-origin + payload | the only mutation path |

Everything else — and every tokenless request — is `404`, so a port scan
cannot even confirm that a Setup Center is running.

## Actions (fixed allowlist)

`START_PAIRING`, `SETUP_CODEX|CLAUDE|ZCODE|GROK`, `REMOVE_CODEX|CLAUDE|ZCODE|GROK`,
`TEST_NOTIFICATION`, `SET_LANG`, `FINISH`. `SETUP_*`/`REMOVE_*` require
`confirm: true`. There is no endpoint that accepts a command, a path, or a
payload of arbitrary shape — unknown actions are `400`.

## Pairing poller

A daemon thread owns the pairing status machine
(`waiting → approved | expired | error`) and polls the existing worker
endpoint every 2 s; `/api/status` only reads memory. Transient failures
(5xx, malformed body, network) keep waiting until the request's own TTL —
the QR banner then offers "새 QR 코드 만들기". The poll secret stays in
process memory and is never serialized.

## Session lifetime

45-minute TTL (`SESSION_TTL_SECONDS`), FINISH action, console close, or
Ctrl+C — whichever first. There is no persistence: restart creates a fresh
token, port, and state.

## Why no launcher app

The onboard mode is a flag of the shipped exe. The installer creates
shortcuts to it; the console is minimized at start, and closing that window
is an honest kill switch. A hidden background process with no visible
handle would violate the campaign's own no-daemon rule.
