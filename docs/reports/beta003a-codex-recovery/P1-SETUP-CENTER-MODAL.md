# P1 — Setup Center confirmation modal: empty body, dead screen — ROOT CAUSE + FIX

Reported by the owner in real-browser testing on 2026-09-20. Not caught by
any automated test — the modal contract is now pinned at three levels
(page-structure tests, real-browser E2E, packaged-exe browser E2E).

## Symptom

Opening a tool's confirmation modal showed an empty body with only
[취소] [연결]; after 연결 there was no loading/success/error feedback and the
page appeared frozen.

## Root cause (CSS specificity, one line)

```html
<div id="modal" class="hidden" style="…;display:flex;align-items:center;…">
```

The inline `display:flex` always beat the `.hidden { display:none }` class
rule. Consequences, all from that one line:

1. the modal overlay was **visible from page load** — an empty card with
   only the two buttons (exactly what the owner saw as an "empty modal");
2. the `position:fixed; inset:0` backdrop **swallowed every click** on the
   page, so a real user's tap on a card's 연결 never reached
   `confirmConnect` — the body stayed empty forever;
3. the modal's own [연결] button had no handler until `confirmConnect` ran
   (which the backdrop prevented), so clicking it did nothing;
4. [취소] → `closeModal()` re-added `.hidden`, which the inline style
   defeated, so the modal **never closed** — the "frozen screen".

Reproduced in a real Chromium (headless Edge, CDP) against the unfixed
page: `FAIL modal_hidden_on_load`, `FAIL backdrop_does_not_swallow_clicks`,
`FAIL busy_feedback`.

## Fix (page-only; no backend contract change)

- `#modal { display:none … }` + `#modal.open { display:flex }`; the modal is
  toggled exclusively through `showModal()/closeModal()` class helpers
  (a stale duplicate `closeModal` definition that re-introduced the broken
  class toggle was removed).
- `post(action, extra, timeoutMs)` gained an `AbortController` hard timeout
  (20 s default; 45 s for the test notification) — a hung backend surfaces
  as an error, never a frozen page.
- Connect/Remove share `runAgentAction`: double-click guard (`disabled`
  first), busy copy (처리 중…/제거 중…), success → modal auto-closes +
  card re-renders (연결됨 ✓), failure → recoverable copy (연결하지 못했어요.
  다시 시도해 주세요. / 제거하지 못했어요…) with the button switching to
  다시 시도/제거 — raw exceptions never reach the user. KO/EN mirrored
  (65 = 65 keys).
- The modal body is built from static KO/EN strings synchronously — it can
  never depend on a server round-trip to have content.
- Codex success no longer pops a second informational dialog; the card's
  state is the feedback (the trust guidance remains on the 연결 확인 button).

## Extra hardening found while investigating

- `SetupServer.handle_error` now quietly ignores Windows loopback
  `ConnectionAbortedError` (WinError 10053) — a client dropping a request
  mid-flight is routine on Windows and was printing tracebacks and, under
  full-suite load, could even surface as a flaky API test.
- `pigeonhub onboard` gained the opt-in `PIGEONHUB_ONBOARD_URL_FILE` env
  hook: when set, the session URL is written to that caller-specified local
  file instead of opening a browser (default behavior unchanged; the token
  is still never printed or logged). This is what lets the packaged-exe
  browser E2E drive the real server without hijacking the desktop browser.

## Verification (all green)

| Level | What | Result |
| --- | --- | --- |
| Page structure (pytest, 6 tests) | class-driven modal, single closeModal, timeout/busy/guard markers, KO/EN key parity 65=65, static body strings | PASS (suite 109/109, onboard file 28/28 ×5 stable) |
| Real browser (headless Edge + CDP, source server) | 12 checks: hidden on load, no click-swallowing (`elementFromPoint`), KO body renders, busy, success closes + card 연결됨 ✓, SETUP_CODEX via UI writes the notify line into the sandboxed Codex config, failure copy, cancel after failure, 1 ms timeout aborts | `BROWSER_E2E: PASS` |
| Packaged exe browser E2E | the same 12 checks against `dist/pigeonhub.exe onboard`'s own server (URL via the env hook; headless Edge; `PIGEONHUB_AGENT_HOME` sandbox so the real `~/.codex` is never touched) | `BROWSER_E2E: PASS` |

Screenshot after a real connect flow (KO, modal closed, Codex card
연결됨 ✓ · notify): `setup-center-browser-e2e.png` next to this file.
E2E driver: `tools/agent-e2e-sandbox/setup_center_browser_e2e.py`
(`--exe dist/pigeonhub.exe` for the packaged mode).

## Non-destruction guarantees

- The E2E sandboxes `PIGEONHUB_AGENT_HOME` — all Codex config writes during
  tests land in a temp home; the real `~/.codex`, pairing credentials, and
  ZCode/agent hooks were never touched.
- No pairing/credential/recipe file was modified by this fix; the diff is
  `pigeonhub/onboard_pages.py`, `pigeonhub/onboard.py` (socket-noise +
  URL hook), `pigeonhub/cli.py` (URL hook), and tests/E2E tooling.
