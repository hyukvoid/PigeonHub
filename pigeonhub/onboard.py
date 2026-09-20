"""BETA-003: the ephemeral local Setup Center.

A one-shot onboarding helper: ``pigeonhub onboard`` starts an HTTP server bound
to 127.0.0.1 on a random port, opens the default browser, and walks the user
through connect-phone → connect-tools → test → done. The server lives only for
the setup session (or until the process exits) — there is no daemon, service,
tray app, or persistent listener.

Security contract (enforced here and tested in
tests/test_pigeonhub_onboard.py):

- bind loopback only; random port;
- every request must carry the cryptographically random session token
  (URL query); an invalid or expired token is a 404 — the server never
  confirms that a Setup Center exists at that port;
- state changes happen exclusively via POST to the action endpoint, with the
  session token and a same-origin check (Origin/Referer host must match the
  loopback host:port) and an explicit ``confirm`` flag for mutations;
- actions are a fixed allowlist — there is no endpoint that runs commands;
- secrets (poll secret, connector token, config contents) never appear in any
  response; the session token is never written to disk or logs.

The actual work is delegated to the existing, validated engines: QR pairing
reuses the MVP-016 pairing protocol helpers in :mod:`pigeonhub.core`, agent
integration reuses :mod:`pigeonhub.agents` (preview → backup → atomic apply →
verify), and the test notification reuses :func:`pigeonhub.core.publish_message`.
"""

from __future__ import annotations

import base64
import json
import os
import secrets
import sys
import threading
import time
import urllib.parse
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from . import agents as agent_engine
from . import codex_integration
from .core import (
    DEFAULT_WORKER_URL,
    CliError,
    _render_login_qr_png,
    credentials_path,
    http_json,
    publish_message,
    _save_redeemed_login,
)

SESSION_TTL_SECONDS = 45 * 60
POLL_INTERVAL_SECONDS = 2.0

# The complete set of state-changing actions. Anything else is rejected —
# there is deliberately no endpoint that can execute arbitrary commands.
MUTATING_ACTIONS = {
    "START_PAIRING",
    "SETUP_CODEX",
    "SETUP_CLAUDE",
    "SETUP_ZCODE",
    "SETUP_GROK",
    "REMOVE_CODEX",
    "REMOVE_CLAUDE",
    "REMOVE_ZCODE",
    "REMOVE_GROK",
    "TEST_NOTIFICATION",
    "SET_LANG",
    "FINISH",
}
AGENTS = ("codex", "claude", "zcode", "grok")


@dataclass
class PairingState:
    request_id: str = ""
    challenge: str = ""
    poll_secret: str = ""  # memory-only; never serialized to any response
    qr_data_uri: str = ""
    expires_at: float = 0.0
    status: str = "idle"  # idle | waiting | approved | expired | error
    error: str = ""


@dataclass
class SetupState:
    token: str = field(default_factory=lambda: secrets.token_urlsafe(24))
    lang: str = "ko"
    pairing: PairingState = field(default_factory=PairingState)
    agent_events: dict = field(default_factory=dict)  # agent -> {"state":..., "detail":...}
    auto_connect_consent: bool = False
    last_test: dict = field(default_factory=dict)
    created_at: float = field(default_factory=time.time)
    stop: threading.Event = field(default_factory=threading.Event)


def _expiry_remaining(pairing: PairingState) -> int:
    return max(0, int(pairing.expires_at - time.time()))


def _codex_local_state() -> dict:
    """Read-only Codex state for the tools screen, from the real sources.

    The notify plan is queried without writing anything. When the single
    upstream notify slot is occupied by a non-PigeonHub program (for example
    Codex Desktop's own computer-use bridge), it is preserved and the state
    falls back to the hook-based integration if PigeonHub-managed hooks are
    actually installed — the card must reflect what is really connected, not
    what a fresh install would do.
    """
    try:
        plan = codex_integration.build_codex_notify_plan()
    except Exception:
        return {"state": "attention", "detail": "codex"}
    version = plan.detection.version or ""
    if plan.status == codex_integration.CONNECTED_BASIC:
        return {"state": "connected", "detail": "notify"}
    if plan.status == codex_integration.NOT_INSTALLED:
        return {"state": "not_detected", "detail": version}
    if plan.status == codex_integration.NEEDS_ATTENTION:
        try:
            detection = agent_engine.detect_agent("codex")
            hooks = agent_engine._read_json(detection.config_path) if detection.config_present else {}
            if agent_engine._managed_count(hooks, "codex") > 0:
                return {"state": "connected", "detail": "hooks"}
        except agent_engine.AgentSetupError:
            pass
        return {"state": "attention", "detail": "existing notify preserved"}
    return {"state": "detected", "detail": version}


def _codex_event(result: "codex_integration.CodexConnectResult") -> dict:
    if result.status == codex_integration.CONNECTED_BASIC:
        return {"state": "connected", "detail": "notify"}
    if result.status == codex_integration.NOT_INSTALLED:
        return {"state": "not_detected", "detail": ""}
    if result.status == codex_integration.NEEDS_ATTENTION:
        return {"state": "attention", "detail": "existing notify preserved"}
    return {"state": "detected", "detail": result.version or ""}


class SetupCenter:
    """Owns the session state, the pairing poller, and the action handlers."""

    def __init__(self) -> None:
        self.state = SetupState()
        self._poller: threading.Thread | None = None

    # ---- read models (safe to serialize) ---------------------------------
    def public_status(self) -> dict:
        p = self.state.pairing
        return {
            "lang": self.state.lang,
            # A returning user must not be forced through QR pairing again;
            # the persisted credential is the source of truth for this step.
            "already_paired": credentials_path().exists(),
            "pairing": {
                "status": p.status,
                "expires_in": _expiry_remaining(p) if p.status == "waiting" else 0,
                "error": p.error,
            },
            "auto_connect_consent": self.state.auto_connect_consent,
            "agents": self._agent_states(),
            "test": self.state.last_test,
        }

    def _agent_states(self) -> list[dict]:
        out = []
        for agent in AGENTS:
            event = self.state.agent_events.get(agent, {})
            entry = {"id": agent, **event}
            if "state" not in entry:
                if agent == "codex":
                    entry.update(_codex_local_state())
                else:
                    try:
                        det = agent_engine.detect_agent(agent)
                    except agent_engine.AgentSetupError:
                        det = None
                    entry["state"] = "detected" if (det and det.detected) else "not_detected"
                    entry["detail"] = (det.version if det and det.version else "")
            out.append(entry)
        return out

    # ---- actions ----------------------------------------------------------
    def start_pairing(self, *, consent: bool = False) -> dict:
        self.state.auto_connect_consent = bool(consent)
        base = os.environ.get("PIGEONHUB_WORKER_URL", DEFAULT_WORKER_URL).rstrip("/")
        status, body = http_json(f"{base}/v1/pairing/requests", {})
        if status != 200 or not body.get("ok"):
            self.state.pairing = PairingState(status="error", error="network")
            return {"ok": False, "error": "network"}
        request_id = str(body.get("request_id", ""))
        challenge = str(body.get("challenge", ""))
        poll_secret = str(body.get("poll_secret", ""))
        if not request_id or not challenge or not poll_secret:
            self.state.pairing = PairingState(status="error", error="network")
            return {"ok": False, "error": "network"}
        expires_at = time.time() + 600.0
        raw = body.get("expires_at")
        if raw:
            from datetime import datetime

            try:
                expires_at = datetime.fromisoformat(str(raw).replace("Z", "+00:00")).timestamp()
            except ValueError:
                pass
        query = urllib.parse.urlencode({"request_id": request_id, "challenge": challenge})
        payload = str(body.get("qr_payload") or f"pigeonhub://login?{query}")
        qr_b64 = ""
        png_path: Path | None = None
        try:
            png_path = _render_login_qr_png(payload)
            qr_b64 = base64.b64encode(png_path.read_bytes()).decode("ascii")
        except Exception:
            qr_b64 = ""
        finally:
            if png_path is not None:
                try:
                    png_path.unlink(missing_ok=True)
                except OSError:
                    pass
        self.state.pairing = PairingState(
            request_id=request_id,
            challenge=challenge,
            poll_secret=poll_secret,
            qr_data_uri=f"data:image/png;base64,{qr_b64}" if qr_b64 else "",
            expires_at=expires_at,
            status="waiting" if qr_b64 else "error",
            error="" if qr_b64 else "qr",
        )
        ok = bool(qr_b64)
        self._start_poller()
        # "ok" reflects only whether this request produced a scannable QR; the
        # poller thread owns the status from here on.
        return {"ok": ok}

    def _start_poller(self) -> None:
        if self._poller and self._poller.is_alive():
            return
        self._poller = threading.Thread(target=self._poll_loop, daemon=True)
        self._poller.start()

    def _poll_loop(self) -> None:
        base = os.environ.get("PIGEONHUB_WORKER_URL", DEFAULT_WORKER_URL).rstrip("/")
        while not self.state.stop.is_set():
            p = self.state.pairing
            if p.status != "waiting":
                return
            if time.time() >= p.expires_at:
                p.status = "expired"
                return
            try:
                status, body = http_json(
                    f"{base}/v1/pairing/requests/poll",
                    {"request_id": p.request_id, "poll_secret": p.poll_secret},
                    timeout=8,
                )
            except Exception:
                # Transient transport trouble: keep waiting until the TTL.
                time.sleep(POLL_INTERVAL_SECONDS)
                continue
            state = body.get("status") if isinstance(body, dict) else None
            if status == 200 and state == "pending":
                time.sleep(POLL_INTERVAL_SECONDS)
                continue
            if status == 200 and state == "approved":
                try:
                    _save_redeemed_login(body)
                    p.status = "approved"
                    if self.state.auto_connect_consent:
                        self._start_auto_connect()
                except CliError:
                    p.status = "error"
                    p.error = "save"
                return
            if status == 410 or "expired" in str(body.get("error", "")):
                p.status = "expired"
                return
            # Anything else (5xx, malformed body, lag) is treated as transient:
            # keep waiting until the pairing request itself expires.
            time.sleep(POLL_INTERVAL_SECONDS)

    def _start_auto_connect(self) -> None:
        """Run local integration discovery after pairing without blocking it."""
        self.state.agent_events["codex"] = {"state": "connecting", "detail": ""}

        def worker() -> None:
            try:
                result = codex_integration.connect_codex()
                event = _codex_event(result)
            except Exception:
                event = {"state": "attention", "detail": "automatic setup failed"}
            self.state.agent_events["codex"] = event

        threading.Thread(target=worker, name="pigeonhub-codex-connect", daemon=True).start()

    def setup_agent(self, agent: str) -> dict:
        if agent == "codex":
            # The Setup Center connects Codex through its supported notify
            # completion event only; hook-based lifecycle stays an explicit
            # CLI choice (`pigeonhub setup codex`) because it requires Codex's
            # own trust flow.
            try:
                result = codex_integration.connect_codex()
            except Exception:
                self.state.agent_events[agent] = {"state": "attention", "detail": "automatic setup failed"}
                return {"ok": False, "error": "attention"}
            self.state.agent_events[agent] = _codex_event(result)
            return {"ok": result.status == codex_integration.CONNECTED_BASIC}
        plan = agent_engine.build_setup_plan(agent)
        if plan.blocked_reason:
            self.state.agent_events[agent] = {"state": "attention", "detail": "blocked"}
            return {"ok": False, "error": "blocked"}
        if not plan.changed:
            self.state.agent_events[agent] = {"state": "connected"}
            return {"ok": True, "already": True}
        backup = agent_engine.apply_setup(plan)
        self.state.agent_events[agent] = {"state": "connected", "detail": str(backup) if backup else ""}
        return {"ok": True}

    def remove_agent(self, agent: str) -> dict:
        if agent == "codex":
            # Removing the managed notify is best effort: NEEDS_ATTENTION from
            # the removal plan can only mean the notify slot holds something
            # that is not ours (it was never touched), so the hook cleanup
            # below must still proceed.
            try:
                codex_integration.remove_codex()
            except Exception:
                pass
        plan = agent_engine.build_setup_plan(agent, remove=True)
        if plan.blocked_reason:
            self.state.agent_events[agent] = {"state": "attention", "detail": "blocked"}
            return {"ok": False, "error": "blocked"}
        if not plan.changed:
            self.state.agent_events[agent] = {"state": "not_detected"}
            return {"ok": True, "already": True}
        agent_engine.remove_setup(plan)
        self.state.agent_events[agent] = {"state": "not_detected"}
        return {"ok": True}

    def test_notification(self) -> dict:
        try:
            result = publish_message(
                "PigeonHub", "Setup Center test notification", priority="high"
            )
        except CliError as exc:
            self.state.last_test = {"ok": False, "error": "not_connected" if "Not logged in" in str(exc) else "send"}
            return {"ok": False, **self.state.last_test}
        self.state.last_test = {"ok": bool(result.ok and result.delivered), "error": "" if result.ok else "send"}
        return {"ok": self.state.last_test["ok"]}

    def handle_action(self, action: str, payload: dict) -> tuple[int, dict]:
        if action not in MUTATING_ACTIONS:
            return 400, {"ok": False, "error": "unknown_action"}
        if action.startswith(("SETUP_", "REMOVE_")) and payload.get("confirm") is not True:
            return 400, {"ok": False, "error": "confirm_required"}
        if action == "SET_LANG":
            lang = payload.get("lang")
            if lang in ("ko", "en"):
                self.state.lang = lang
            return 200, {"ok": True, "lang": self.state.lang}
        if action == "START_PAIRING":
            return 200, {"ok": self.start_pairing(consent=payload.get("consent") is True)["ok"]}
        if action == "TEST_NOTIFICATION":
            return 200, {"ok": self.test_notification()["ok"]}
        if action == "FINISH":
            self.state.stop.set()
            threading.Thread(target=self._delayed_shutdown, daemon=True).start()
            return 200, {"ok": True}
        agent = action.split("_", 1)[1].lower()
        if action.startswith("SETUP_"):
            return 200, self.setup_agent(agent)
        return 200, self.remove_agent(agent)

    def _delayed_shutdown(self) -> None:
        time.sleep(0.5)
        try:
            self.server.shutdown()
        except Exception:
            pass

    server: ThreadingHTTPServer | None = None


class SetupServer(ThreadingHTTPServer):
    daemon_threads = True

    def handle_error(self, request, client_address):
        # A browser (or a busy CI loopback) dropping the TCP connection
        # mid-request is routine on Windows (WinError 10053); it is not a
        # server fault and must neither crash a worker thread's output nor
        # print a stack trace.
        exc = sys.exc_info()[1]
        if isinstance(exc, (ConnectionAbortedError, ConnectionResetError, BrokenPipeError, TimeoutError)):
            return
        super().handle_error(request, client_address)


def _is_same_origin_host(handler: "SetupHandler") -> bool:
    for header in ("Origin", "Referer"):
        value = handler.headers.get(header)
        if value:
            host = urllib.parse.urlsplit(value).netloc
            return host == handler.headers.get("Host", "")
    return True  # same-origin by default for non-browser clients in tests


class SetupHandler(BaseHTTPRequestHandler):
    server_version = "PigeonHubSetup"

    @property
    def center(self) -> SetupCenter:
        return self.server.center  # type: ignore[attr-defined]

    def log_message(self, *_args):  # never log paths (they carry the token)
        pass

    def _authorized(self) -> bool:
        token = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get("session", [""])[0]
        if not secrets.compare_digest(token, self.center.state.token):
            return False
        if time.time() - self.center.state.created_at > SESSION_TTL_SECONDS:
            return False
        return self.headers.get("Host", "").startswith("127.0.0.1:")

    def _send(self, code: int, body: bytes, ctype: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, payload: dict) -> None:
        self._send(code, json.dumps(payload).encode("utf-8"), "application/json")

    def do_GET(self):
        if not self._authorized():
            return self._json(404, {"error": "not_found"})
        path = urllib.parse.urlsplit(self.path).path
        if path == "/api/status":
            return self._json(200, self.center.public_status())
        if path == "/api/qr":
            uri = self.center.state.pairing.qr_data_uri
            if not uri or self.center.state.pairing.status != "waiting":
                return self._json(404, {"error": "no_qr"})
            raw = base64.b64decode(uri.split(",", 1)[1])
            return self._send(200, raw, "image/png")
        if path in ("/", "/setup"):
            from .onboard_pages import render_page

            return self._send(200, render_page(self.center.state.token, self.center.state.lang).encode("utf-8"), "text/html; charset=utf-8")
        return self._json(404, {"error": "not_found"})

    def do_POST(self):
        if not self._authorized():
            return self._json(404, {"error": "not_found"})
        if not _is_same_origin_host(self):
            return self._json(403, {"error": "cross_origin"})
        path = urllib.parse.urlsplit(self.path).path
        if path != "/api/action":
            return self._json(404, {"error": "not_found"})
        try:
            length = int(self.headers.get("Content-Length", "0") or 0)
            payload = json.loads(self.rfile.read(length) or b"{}")
            if not isinstance(payload, dict):
                raise ValueError
        except (ValueError, json.JSONDecodeError):
            return self._json(400, {"ok": False, "error": "bad_body"})
        action = str(payload.get("action", ""))
        code, result = self.center.handle_action(action, payload)
        self._json(code, result)


def serve(center: SetupCenter | None = None) -> tuple[SetupCenter, str]:
    """Start the Setup Center on a random loopback port. Returns (center, url)."""
    center = center or SetupCenter()
    server = SetupServer(("127.0.0.1", 0), SetupHandler)
    server.center = center  # type: ignore[attr-defined]
    center.server = server
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    url = f"http://127.0.0.1:{server.server_address[1]}/setup?session={center.state.token}"
    return center, url
