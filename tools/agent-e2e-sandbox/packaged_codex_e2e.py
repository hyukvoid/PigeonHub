"""BETA-003A packaged-CLI Codex E2E (no phone, no real worker).

Drives the REAL production path against the PyInstaller exe:
  login (QR flow, fake worker) → approval → credential saved
    → codex auto-connect installs the notify line into a sandboxed Codex home
  → the frozen exe's internal-codex-notify callback parses a real-shape
    0.152.1 payload and publishes ONLY allowlisted fields
  → a replayed callback produces no second request (one event, one card)
  → prompt/assistant/cwd markers never leave the process.

Run:  python tools/agent-e2e-sandbox/packaged_codex_e2e.py
"""

import json
import os
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from subprocess import run

REPO = Path(__file__).resolve().parents[2]
EXE = REPO / "dist" / "pigeonhub.exe"
PROMPT_MARKER = "PRIVATE_CODEX_PROMPT_MARKER_003A"
REQUESTS = {"created": 0, "poll": 0, "events": []}


class FakeWorker(BaseHTTPRequestHandler):
    def _reply(self, body, code=200):
        payload = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        path = self.path.split("?")[0]
        if path == "/v1/pairing/requests":
            REQUESTS["created"] += 1
            self._reply(
                {
                    "ok": True,
                    "request_id": "plr_e2e",
                    "challenge": "phc_e2e",
                    "poll_secret": "phs_e2e",
                    "qr_payload": "pigeonhub://login?request_id=plr_e2e&challenge=phc_e2e",
                }
            )
        elif path == "/v1/pairing/requests/poll":
            REQUESTS["poll"] += 1
            self._reply(
                {
                    "ok": True,
                    "status": "approved",
                    "endpoint": f"http://127.0.0.1:{self.server.server_address[1]}/events",
                    "write_token": "pct_e2e",
                    "channel_id": "ch_e2e",
                }
            )
        elif path == "/events":
            REQUESTS["events"].append(json.loads(raw))
            self._reply({"stored": True, "push_status": "fcm_accepted"})
        else:
            self._reply({"error": "not_found"}, 404)

    def log_message(self, *_a):
        pass


def main() -> int:
    if not EXE.exists():
        print(f"FAIL: {EXE} missing; run packaging/windows/build.ps1 first")
        return 1
    print("exe_version:", run([str(EXE), "--version"], capture_output=True).stdout.decode("utf-8", "replace").strip())

    worker = HTTPServer(("127.0.0.1", 0), FakeWorker)
    threading.Thread(target=worker.serve_forever, daemon=True).start()
    worker_url = f"http://127.0.0.1:{worker.server_address[1]}"

    sandbox = tempfile.TemporaryDirectory()
    home = Path(sandbox.name)
    env = dict(os.environ)
    env.update(
        {
            "PIGEONHUB_AGENT_HOME": str(home),
            "PIGEONHUB_CREDENTIALS": str(home / ".pigeonhub" / "credentials.json"),
            "PIGEONHUB_NO_OPEN": "1",
        }
    )

    # 1. the real pairing path: QR login → (phone approves) → save → auto-connect.
    #    Non-TTY stdin means the auto-connect consent defaults to yes.
    login = run(
        [str(EXE), "login", "--worker-url", worker_url],
        capture_output=True, env=env, timeout=120,
        input=b"\n",  # the user presses Enter on the auto-connect consent (default: yes)
    )
    print("login_rc:", login.returncode)
    combined = (login.stdout or b"") + (login.stderr or b"")
    tail = [line for line in combined.decode("utf-8", "replace").splitlines() if line.strip()]
    print("login_tail:", tail[-3:])
    config = home / ".codex" / "config.toml"
    credentials = home / ".pigeonhub" / "credentials.json"
    text = config.read_text(encoding="utf-8") if config.exists() else ""
    notify_installed = 'internal-codex-notify"' in text and "pigeonhub.exe" in text
    print("credentials_saved:", credentials.exists())
    print("codex_config_created:", config.exists())
    print("notify_installed:", notify_installed)

    # 2. real-shape notify payload (see tools/agent-e2e-sandbox/notify_probe.log)
    payload = json.dumps(
        {
            "type": "agent-turn-complete",
            "thread-id": "01a0b86f-f546-7653-af37-9d0dbc0470e6",
            "turn-id": "01a0b86f-fb62-7162-9176-02cbd25e307e",
            "cwd": f"C:\\Users\\x\\{PROMPT_MARKER}",
            "client": "codex_exec",
            "input-messages": [PROMPT_MARKER],
            "last-assistant-message": PROMPT_MARKER,
        }
    )
    notify = run([str(EXE), "internal-codex-notify", payload], capture_output=True, env=env)
    replay = run([str(EXE), "internal-codex-notify", payload], capture_output=True, env=env)
    print("notify_rc:", notify.returncode, "replay_rc:", replay.returncode)
    worker.shutdown()

    events = REQUESTS["events"]
    print("pairing_requests_created:", REQUESTS["created"], "polled:", REQUESTS["poll"])
    print("publish_requests:", len(events))
    marker_leaked = PROMPT_MARKER in json.dumps(events, ensure_ascii=False)
    print("marker_leaked:", marker_leaked)
    if events:
        job = events[0].get("job", {})
        print("event:", job.get("source"), job.get("state"), "|", events[0].get("title"), "|", job.get("job_name"))

    ok = (
        login.returncode == 0
        and credentials.exists()
        and config.exists()
        and notify_installed
        and notify.returncode == 0
        and replay.returncode == 0
        and len(events) == 1
        and events[0].get("job", {}).get("state") == "DONE"
        and events[0].get("job", {}).get("source") == "codex"
        and not marker_leaked
        and REQUESTS["created"] == 1
    )
    print("PACKAGED_E2E:", "PASS" if ok else "FAIL")
    sandbox.cleanup()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
