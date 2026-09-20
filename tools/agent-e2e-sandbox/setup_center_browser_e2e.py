"""Setup Center real-browser E2E (headless Edge via CDP).

Drives the SAME page module the packaged exe serves, inside a real Chromium
rendering engine, and asserts the P1 modal contract:

  1. page load → the confirmation modal is NOT visible (no empty floating
     card with 취소/연결 only — the BETA-003 CSS-specificity bug);
  2. agent cards render with a Connect button;
  3. Connect opens the modal WITH body copy in the active language;
  4. clicking Connect shows busy state, prevents double-click, closes the
     modal on success, and the card flips to 연결됨 ✓;
  5. a failing action keeps the modal usable and shows recoverable copy
     (no raw exceptions), then Cancel still closes it.

Usage:
  python tools/agent-e2e-sandbox/setup_center_browser_e2e.py [--stage <json>]
"""

import json
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from pigeonhub.onboard import serve as serve_setup_center

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]
CDP_PORT = 9223


class StageWorker(BaseHTTPRequestHandler):
    """Fake connector worker; also answers the agent-detection env."""

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0") or 0)
        self.rfile.read(length)
        body = json.dumps({"ok": True, "status": "approved"}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_a):
        pass


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class Cdp:
    def __init__(self, ws):
        self.ws = ws
        self.next_id = 0

    def call(self, method, params=None):
        self.next_id += 1
        call_id = self.next_id
        import websocket

        self.ws.send(json.dumps({"id": call_id, "method": method, "params": params or {}}))
        deadline = time.time() + 30
        while time.time() < deadline:
            raw = self.ws.recv()
            message = json.loads(raw)
            if message.get("id") == call_id:
                if "error" in message:
                    raise RuntimeError(f"CDP error: {message['error']}")
                return message.get("result", {})
        raise TimeoutError(method)

    def eval_js(self, expression, await_promise=False):
        params = {"expression": expression, "returnByValue": True}
        if await_promise:
            params["awaitPromise"] = True
        result = self.call("Runtime.evaluate", params)
        value = result.get("result", {})
        if value.get("subtype") == "error":
            raise RuntimeError(f"JS error: {value.get('description', '')[:300]}")
        return value.get("value")

    def wait_js(self, expression, timeout=15):
        deadline = time.time() + timeout
        last = None
        while time.time() < deadline:
            last = self.eval_js(expression)
            if last:
                return last
            time.sleep(0.3)
        return last


def wait_for_cdp(timeout=40):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json/version", timeout=2) as r:
                return json.loads(r.read())
        except Exception:
            time.sleep(0.4)
    raise TimeoutError("Edge CDP did not come up")


def main() -> int:
    edge = next((p for p in EDGE_CANDIDATES if Path(p).exists()), None)
    if edge is None:
        print("SKIP: Edge not found")
        return 2

    import argparse
    import os

    parser = argparse.ArgumentParser()
    parser.add_argument("--exe", help="drive the packaged exe's own onboard server instead of the source one")
    args = parser.parse_args()

    # --- sandbox: all agent-config writes must land here, never in the real home
    sandbox = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
    agent_home = Path(sandbox.name) / "home"
    old_home = os.environ.get("PIGEONHUB_AGENT_HOME")
    os.environ["PIGEONHUB_AGENT_HOME"] = str(agent_home)

    setup_process = None
    if args.exe:
        # The packaged exe exposes its session URL only through the opt-in
        # PIGEONHUB_ONBOARD_URL_FILE hook (it never prints or logs the token);
        # the URL file lives inside the sandbox and no real browser opens.
        capture = Path(sandbox.name) / "url.txt"
        env = dict(os.environ)
        env["PIGEONHUB_ONBOARD_URL_FILE"] = str(capture)
        setup_process = subprocess.Popen(
            [args.exe, "onboard"],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            stdin=subprocess.DEVNULL,
        )
        deadline = time.time() + 60
        while time.time() < deadline and not capture.exists():
            time.sleep(0.4)
        if not capture.exists():
            print("FAIL: packaged exe did not expose a URL to the browser hook")
            return 1
        url = capture.read_text(encoding="utf-8").strip()
        print("setup_center: packaged exe", url.split("?session=")[0], "(token in-memory + sandbox file only)")
    else:
        from pigeonhub.onboard import serve as serve_setup_center

        center, url = serve_setup_center()
        print("setup_center:", url.split("?session=")[0], "(token in-memory only)")

    # --- launch headless Edge ----------------------------------------------
    user_data = Path(sandbox.name) / "edge-profile"
    edge_proc = subprocess.Popen(
        [
            edge,
            "--headless=new",
            f"--remote-debugging-port={CDP_PORT}",
            f"--user-data-dir={user_data}",
            "--no-first-run",
            "--disable-gpu",
            "about:blank",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        wait_for_cdp()
        import websocket

        targets = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json").read())
        page_ws = next(t["webSocketDebuggerUrl"] for t in targets if t.get("type") == "page")
        ws = websocket.create_connection(page_ws, timeout=30, suppress_origin=True)
        cdp = Cdp(ws)
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Page.navigate", {"url": url})
        time.sleep(2.5)

        checks = []

        def check(name, ok, detail=""):
            checks.append((name, bool(ok), detail))
            print(("PASS " if ok else "FAIL ") + name + (f" — {detail}" if detail else ""))

        # 1. the modal must be hidden on load (P1 regression) — computed CSS
        #    AND real hit-testing (the backdrop must not swallow clicks)
        visible = cdp.eval_js(
            "(()=>{const m=document.getElementById('modal');"
            "return m && getComputedStyle(m).display !== 'none';})()"
        )
        check("modal_hidden_on_load", not visible, "computed display")
        hit = cdp.eval_js(
            "(()=>{const el=document.elementFromPoint(window.innerWidth/2, window.innerHeight/2);"
            "return el ? !!el.closest('#modal') : false;})()"
        )
        check("backdrop_does_not_swallow_clicks", not hit, "elementFromPoint at center")
        body_text = cdp.eval_js(
            "document.body.innerText.includes('오래 걸리는 작업') || document.body.innerText.includes('Know when')"
        )
        check("page_renders", body_text)

        # 2. go to tools and find the connect button
        cdp.eval_js("go('tools')")
        has_connect = cdp.wait_js("!!document.querySelector('#agents button')", timeout=25)
        check("agent_cards_render", has_connect)

        # 3. open the connect modal — body must render
        cdp.eval_js(
            "const btn=[...document.querySelectorAll('#agents button')]"
            ".find(b=>b.getAttribute('onclick')&&b.getAttribute('onclick').includes('confirmConnect'));"
            "btn ? btn.click() : null"
        )
        time.sleep(0.4)
        modal_shown = cdp.eval_js("document.getElementById('modal').classList.contains('open')")
        body_len = cdp.eval_js("document.getElementById('mBody').innerText.trim().length")
        title_len = cdp.eval_js("document.getElementById('mTitle').innerText.trim().length")
        check("modal_opens_with_body", modal_shown and body_len > 20 and title_len > 0, f"title={title_len} body={body_len}")

        # 4. confirm the connect action — busy state (sampled synchronically,
        #    a local POST finishes in milliseconds) then success
        cdp.eval_js("document.getElementById('mOk').click()")
        busy = cdp.eval_js(
            "document.getElementById('mOk').disabled || document.getElementById('mStatus').innerText.includes('처리') || document.getElementById('mStatus').innerText.includes('제거')"
        )
        check("busy_feedback", busy)
        settled = cdp.wait_js(
            "!document.getElementById('modal').classList.contains('open')"
            " && [...document.querySelectorAll('#agents .state')].some(el=>el.className.includes('connected'))",
            timeout=25,
        )
        modal_closed = cdp.eval_js("!document.getElementById('modal').classList.contains('open')")
        connected_card = cdp.eval_js(
            "[...document.querySelectorAll('#agents .state')].some(el=>el.className.includes('connected'))"
        )
        check("success_closes_modal_and_card_connected", bool(settled) and modal_closed and connected_card,
              f"closed={modal_closed} connected_card={connected_card}")

        # 5. the real Codex success path through the UI: SETUP_CODEX must
        #    actually write the notify line into the sandboxed config.
        #    (The card alone is not ground truth — the read-only plan already
        #    reports "connected" for a fresh install — so poll the file.)
        cdp.wait_js(
            "!document.getElementById('mOk').disabled", timeout=30
        )  # the double-click guard must be released before firing the next action
        cdp.eval_js("runAgentAction('SETUP_CODEX', 'connect_fail', 'confirm')")
        sandbox_config = Path(sandbox.name) / "home" / ".codex" / "config.toml"
        config_written = False
        deadline = time.time() + 40
        while time.time() < deadline:
            if sandbox_config.exists() and "internal-codex-notify" in sandbox_config.read_text(encoding="utf-8"):
                config_written = True
                break
            time.sleep(0.3)
        check("codex_config_written_via_ui", config_written)
        settled = cdp.wait_js(
            "!document.getElementById('mOk').disabled && !document.getElementById('modal').classList.contains('open')",
            timeout=30,
        )
        check("codex_connect_via_ui", bool(settled))

        # 6. failure path — an unknown action must surface recoverable copy,
        #    never a raw exception, and Cancel must still close the modal.
        cdp.eval_js(
            "runAgentAction('SETUP_DOES_NOT_EXIST_003A', 'connect_fail', 'confirm')"
        )
        error_shown = cdp.wait_js(
            "document.getElementById('mStatus').innerText.includes('연결하지 못했어요')",
            timeout=10,
        )
        cancel_enabled = cdp.eval_js("!document.querySelector('#modal .ghost').disabled")
        check("failure_shows_recoverable_copy", bool(error_shown) and cancel_enabled)
        cdp.eval_js("closeModal()")
        time.sleep(0.3)
        closed_after_error = cdp.eval_js("!document.getElementById('modal').classList.contains('open')")
        check("cancel_works_after_failure", closed_after_error)

        # 7. timeout mechanism — a 1ms deadline must abort, not hang
        aborted = cdp.eval_js(
            "post('SET_LANG', {lang:'ko'}, 1).then(()=>'completed').catch((e)=>e.name === 'AbortError' ? 'aborted' : 'other')",
            await_promise=True,
        )
        check("timeout_aborts", aborted == "aborted", f"result={aborted}")

        # 8. screenshot evidence
        shot = cdp.call("Page.captureScreenshot", {"format": "png"})
        out = Path("docs/reports/beta003a-codex-recovery/setup-center-browser-e2e.png")
        out.parent.mkdir(parents=True, exist_ok=True)
        import base64

        out.write_bytes(base64.b64decode(shot["data"]))
        print("screenshot:", out)

        ws.close()
    finally:
        edge_proc.terminate()
        try:
            edge_proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            edge_proc.kill()
        if setup_process is not None:
            setup_process.terminate()
            try:
                setup_process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                setup_process.kill()
        else:
            center.state.stop.set()
            try:
                center.server.server_close()
            except Exception:
                pass
        if old_home is None:
            os.environ.pop("PIGEONHUB_AGENT_HOME", None)
        else:
            os.environ["PIGEONHUB_AGENT_HOME"] = old_home
        sandbox.cleanup()

    failed = [c for c in checks if not c[1]]
    print("BROWSER_E2E:", "PASS" if not failed else f"FAIL ({len(failed)})")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
