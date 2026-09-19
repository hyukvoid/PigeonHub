import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

from pigeonhub import __version__, core, cli


class _Handler(BaseHTTPRequestHandler):
    requests = []
    fail_running = False
    login_polls = 0
    publish_mode = "ok"  # ok | stored_failed | flaky_503 | drop_terminal
    idempotency_keys: list[str | None] = []
    flaky_count = 0
    dropped_keys: list[str] = []

    def log_message(self, *_args):
        pass

    def _json(self, status, body):
        encoded = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self):
        if self.path == "/health":
            self._json(200, {"ok": True, "service": "test"})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        self.__class__.requests.append((self.path, body))
        if self.path == "/v1/pairing/requests":
            self._json(200, {
                "ok": True,
                "request_id": "plr_test",
                "challenge": "phc_test",
                "poll_secret": "phs_test",
                "expires_at": "2099-01-01T00:00:00+00:00",
            })
            return
        if self.path == "/v1/pairing/requests/poll":
            self.__class__.login_polls += 1
            if self.__class__.login_polls == 1:
                self._json(200, {"ok": True, "status": "pending"})
            else:
                endpoint = f"http://{self.server.server_address[0]}:{self.server.server_address[1]}/v1/channels/ch_test/messages"
                self._json(200, {"ok": True, "status": "approved", "endpoint": endpoint, "write_token": "pct_test", "channel_id": "ch_test"})
            return
        if self.path == "/v1/pairing/redeem":
            self._json(200, {"ok": True, "endpoint": f"http://{self.server.server_address[0]}:{self.server.server_address[1]}/v1/channels/ch_test/messages", "write_token": "pct_test", "channel_id": "ch_test"})
            return
        if self.path == "/v1/channels/ch_test/messages":
            self.__class__.idempotency_keys.append(self.headers.get("Idempotency-Key"))
            job = body.get("job") or {}
            if self.__class__.fail_running and job.get("state") == "RUNNING":
                self._json(503, {"error": "injected running failure"})
            elif self.__class__.publish_mode == "drop_terminal" and job.get("state") != "RUNNING":
                # Simulate the network vanishing right after RUNNING: accept the
                # first attempt, then silently drop the connection so the CLI
                # sees a transport error and must retry with the same key.
                if self.__class__.idempotency_keys[-1] not in self.__class__.dropped_keys:
                    self.__class__.dropped_keys.append(self.__class__.idempotency_keys[-1])
                    self.close_connection = True
                    return
                self._json(201, {"stored": True, "message_id": f"m{len(self.__class__.requests)}"})
            elif self.__class__.publish_mode == "stored_failed":
                self._json(200, {
                    "stored": True,
                    "message_id": f"m{len(self.__class__.requests)}",
                    "push_status": "failed",
                    "error": "fcm send failed: HTTP 404 NotRegistered",
                    "delivery": {"retryable": False},
                })
            elif self.__class__.publish_mode == "flaky_503":
                self.__class__.flaky_count += 1
                if self.__class__.flaky_count % 2 == 1:
                    self._json(503, {"stored": False, "error": "injected"})
                else:
                    self._json(201, {"stored": True, "message_id": f"m{len(self.__class__.requests)}"})
            else:
                self._json(201, {"stored": True, "message_id": f"m{len(self.__class__.requests)}"})
            return
        self._json(404, {"error": "not found"})


class CliCoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.credentials = Path(self.temp.name) / "credentials.json"
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        _Handler.requests = []
        _Handler.fail_running = False
        _Handler.login_polls = 0
        _Handler.publish_mode = "ok"
        _Handler.idempotency_keys = []
        _Handler.flaky_count = 0
        _Handler.dropped_keys = []
        self.env = patch.dict(
            os.environ,
            {"PIGEONHUB_CREDENTIALS": str(self.credentials)},
            clear=False,
        )
        self.env.start()
        os.environ.pop("PIGEONHUB_STATE", None)
        core._save_credentials(
            {
                "endpoint": self.endpoint,
                "write_token": "pct_test",
                "channel_id": "ch_test",
                "paired_at": "now",
            },
            self.credentials,
        )

    @property
    def endpoint(self):
        host, port = self.server.server_address
        return f"http://{host}:{port}/v1/channels/ch_test/messages"

    def tearDown(self):
        self.env.stop()
        self.server.shutdown()
        self.server.server_close()
        self.temp.cleanup()

    def _events(self):
        return [body.get("job", {}) for path, body in _Handler.requests if path.endswith("/messages") and body.get("job")]

    def test_publish_sends_stable_idempotency_key_and_stops_on_stored_failed(self):
        _Handler.publish_mode = "stored_failed"
        result = core.publish_job_detailed("cli", "job-mvp019", "DONE", job_name="x")
        self.assertTrue(result.ok)
        self.assertFalse(result.delivered)
        self.assertTrue(result.stale_pairing)
        # stored-but-failed delivery is durable: exactly one request, no retries.
        publish_calls = [p for p in _Handler.requests if p[0].endswith("/messages")]
        self.assertEqual(len(publish_calls), 1)
        key = _Handler.idempotency_keys[0]
        self.assertIsInstance(key, str) and self.assertTrue(32 <= len(key) <= 64)

        # The same logical event (identical payload) reuses the identical key.
        core.publish_payload(
            core._payload("cli", "job-mvp019", "DONE", job_name="x"),
            "DONE",
        )
        self.assertEqual(_Handler.idempotency_keys[0], _Handler.idempotency_keys[1])
        # A different event carries a different key.
        core.publish_payload(
            core._payload("cli", "job-mvp019", "RUNNING", job_name="x"),
            "RUNNING",
        )
        self.assertNotEqual(_Handler.idempotency_keys[1], _Handler.idempotency_keys[2])

    def test_publish_retries_flaky_503_with_the_same_key(self):
        _Handler.publish_mode = "flaky_503"
        result = core.publish_job_detailed("cli", "job-flaky", "DONE", job_name="x")
        self.assertTrue(result.ok)
        self.assertTrue(result.delivered)
        self.assertGreaterEqual(len(_Handler.idempotency_keys), 2)
        self.assertEqual(len(set(_Handler.idempotency_keys)), 1)

    def test_terminal_publish_survives_network_drop_without_duplication(self):
        # RUNNING is accepted; the network dies on the first DONE attempt and
        # the retry lands. One row per event, same key on the retry.
        _Handler.publish_mode = "drop_terminal"
        with patch.object(core.time, "sleep"):
            result = core.publish_job_detailed("cli", "job-drop", "RUNNING", job_name="x")
            self.assertTrue(result.ok)
            done = core.publish_job_detailed("cli", "job-drop", "DONE", job_name="x")
        self.assertTrue(done.ok)
        self.assertTrue(done.delivered)
        running_keys = _Handler.idempotency_keys[:1]
        done_keys = [k for k in _Handler.idempotency_keys[1:] if k]
        self.assertEqual(len(set(done_keys)), 1, "retry must reuse the same idempotency key")
        self.assertNotEqual(running_keys[0], done_keys[0])
        # Two DONE requests reach the server (the dropped attempt + the retry),
        # but they share one key, so the worker stores exactly one row — the
        # idempotency behavior asserted in the flaky-503 test above.
        done_attempts = [b for _, b in _Handler.requests if (b.get("job") or {}).get("state") == "DONE"]
        self.assertEqual(len(done_attempts), 2)

    def test_stale_pairing_prints_recovery_guidance(self):
        _Handler.publish_mode = "stored_failed"
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            result = core.publish_job_detailed("cli", "job-dead", "DONE", job_name="x")
        self.assertTrue(result.ok)  # durable: stored, delivery failed
        self.assertTrue(result.stale_pairing)
        self.assertIn("pairing can no longer reach your phone", stderr.getvalue())
        self.assertIn("pigeonhub login", stderr.getvalue())

    def test_version_flag_and_command_match_single_source(self):
        for argv in (["--version"], ["version"]):
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                self.assertEqual(cli.main(argv), 0)
            self.assertEqual(buffer.getvalue().strip(), f"PigeonHub CLI {__version__}")

    def test_bare_command_prints_first_run_guidance_not_argparse_help(self):
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            self.assertEqual(cli.main([]), 0)
        out = buffer.getvalue()
        self.assertIn("PigeonHub", out)
        self.assertIn("pigeonhub run", out)
        self.assertNotIn("usage:", out)
        logged_out = patch.dict(os.environ, {"PIGEONHUB_CREDENTIALS": str(Path(self.temp.name) / "absent.json")})
        logged_out.start()
        try:
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                self.assertEqual(cli.main([]), 0)
            out = buffer.getvalue()
            self.assertIn("You're not connected yet.", out)
            self.assertIn("pigeonhub login", out)
            self.assertNotIn("usage:", out)
        finally:
            logged_out.stop()

    def test_run_injects_job_id_and_preserves_child_exit(self):
        child_marker = Path(self.temp.name) / "child.txt"
        command = [
            sys.executable,
            "-c",
            "import os, pathlib, sys; pathlib.Path(os.environ['CHILD_MARKER']).write_text(os.environ['PIGEONHUB_JOB_ID']); print('child stdout'); print('child stderr', file=sys.stderr); sys.exit(7)",
        ]
        with patch.dict(os.environ, {"CHILD_MARKER": str(child_marker)}):
            result = core.run_job(command, name="exit test")
        self.assertEqual(result, 7)
        self.assertEqual(child_marker.read_text(), self._events()[0]["job_id"])
        states = [event["state"] for event in self._events()]
        self.assertEqual(states, ["RUNNING", "FAILED"])
        self.assertEqual({event["job_id"] for event in self._events()}, {self._events()[0]["job_id"]})

    def test_run_starts_windows_batch_shims_via_cmd(self):
        if os.name != "nt":
            self.skipTest("Windows .cmd shim behavior")
        marker = Path(self.temp.name) / "shim-marker.txt"
        shim = Path(self.temp.name) / "phshim.cmd"
        shim.write_text(f"@echo off\r\ntype nul > \"{marker}\"\r\nexit /b 0\r\n", encoding="utf-8")
        with patch.dict(os.environ, {"PATH": os.environ["PATH"] + os.pathsep + str(self.temp.name)}):
            result = core.run_job([shim.stem, "ignored-arg"], name="batch shim test")
        self.assertEqual(result, 0)
        self.assertTrue(marker.exists())
        self.assertEqual([event["state"] for event in self._events()], ["RUNNING", "DONE"])

    def test_codex_wrapper_does_not_publish_command_or_prompt(self):
        if os.name != "nt":
            self.skipTest("Windows command shim behavior")
        secret = "PRIVATE_PROMPT_MARKER_XYZ"
        shim = Path(self.temp.name) / "fake-codex.cmd"
        shim.write_text("@echo off\r\nexit /b 0\r\n", encoding="utf-8")
        result = core.run_job([str(shim), "exec", secret], name=None)
        self.assertEqual(result, 0)
        events = self._events()
        self.assertEqual(events[0]["job_name"], "Codex session")
        self.assertNotIn(secret, json.dumps(_Handler.requests))

    def test_progress_uses_same_lifecycle_job_id(self):
        command = [
            sys.executable,
            "-c",
            "import subprocess, sys; raise SystemExit(subprocess.call([sys.executable, '-m', 'pigeonhub', 'progress', '1', '2']))",
        ]
        self.assertEqual(core.run_job(command, name="progress test"), 0)
        events = self._events()
        self.assertEqual([event["state"] for event in events], ["RUNNING", "PROGRESS", "DONE"])
        self.assertEqual(len({event["job_id"] for event in events}), 1)
        self.assertEqual(events[1]["progress_current"], 1)

    def test_running_publish_failure_does_not_start_command(self):
        _Handler.fail_running = True
        marker = Path(self.temp.name) / "must-not-exist"
        command = [sys.executable, "-c", f"from pathlib import Path; Path(r'{marker}').write_text('started')"]
        self.assertEqual(core.run_job(command, name="blocked"), core.TRACKING_START_FAILURE_EXIT)
        self.assertFalse(marker.exists())
        self.assertEqual([event["state"] for event in self._events()], ["RUNNING"])

    def test_login_status_logout(self):
        self.credentials.unlink()
        worker = f"http://{self.server.server_address[0]}:{self.server.server_address[1]}"
        self.assertEqual(core.login(code="PHC-AAAAA-BBBBB-CCCCC", worker_url=worker), self.credentials)
        value = core.status()
        self.assertTrue(value["logged_in"])
        self.assertTrue(value["worker_reachable"])
        self.assertTrue(core.logout())
        self.assertFalse(self.credentials.exists())

    def test_auto_connect_failure_does_not_rollback_saved_pairing(self):
        body = {
            "endpoint": self.endpoint,
            "write_token": "pct_auto",
            "channel_id": "ch_auto",
        }
        with patch("pigeonhub.codex_integration.connect_codex", side_effect=RuntimeError("injected")):
            self.assertEqual(core._save_redeemed_login(body, auto_connect=True), self.credentials)
        saved = json.loads(self.credentials.read_text(encoding="utf-8"))
        self.assertEqual(saved["write_token"], "pct_auto")
        self.assertEqual(saved["channel_id"], "ch_auto")

    def test_pc_first_login_displays_request_and_polls_after_approval(self):
        self.credentials.unlink()
        worker = f"http://{self.server.server_address[0]}:{self.server.server_address[1]}"
        qr_png = Path(self.temp.name) / "login-qr.png"
        qr_png.write_bytes(b"png")
        with patch.object(core, "_render_login_qr_png", return_value=qr_png), \
                patch.object(core, "_open_qr_image", return_value=True), \
                patch.object(core.time, "sleep"):
            self.assertEqual(core.login(worker_url=worker), self.credentials)
        self.assertEqual(_Handler.login_polls, 2)
        self.assertTrue(self.credentials.exists())
        # BETA-001A: the temporary QR PNG is deleted once approval lands.
        self.assertFalse(qr_png.exists())

    def test_pc_login_removes_temporary_qr_on_error_and_cancel(self):
        self.credentials.unlink()
        worker = f"http://{self.server.server_address[0]}:{self.server.server_address[1]}"
        qr_png = Path(self.temp.name) / "login-qr.png"
        qr_png.write_bytes(b"png")
        created = (200, {"ok": True, "request_id": "plr_t", "challenge": "phc_t", "poll_secret": "phs_t", "expires_at": "2099-01-01T00:00:00+00:00"})
        # A poll failure (e.g. expired request) still removes the QR file.
        with patch.object(core, "_render_login_qr_png", return_value=qr_png), \
                patch.object(core, "_open_qr_image", return_value=True), \
                patch.object(core, "http_json", side_effect=[created, (200, {"ok": True, "status": "error", "error": "login request expired"})]):
            with self.assertRaises(core.CliError):
                core.login(worker_url=worker)
        self.assertFalse(qr_png.exists())

        qr_png.write_bytes(b"png")
        # Ctrl+C before approval cancels cleanly and removes the QR file.
        def interrupt_during_poll(url, payload=None, **kwargs):
            if payload and "poll_secret" in payload:
                raise KeyboardInterrupt
            return created

        with patch.object(core, "_render_login_qr_png", return_value=qr_png), \
                patch.object(core, "_open_qr_image", return_value=True), \
                patch.object(core, "http_json", side_effect=interrupt_during_poll):
            with self.assertRaises(core.CliError) as cancelled:
                core.login(worker_url=worker)
        self.assertIn("cancelled", str(cancelled.exception))
        self.assertFalse(qr_png.exists())
        self.assertFalse(self.credentials.exists())

    def test_pc_login_falls_back_to_terminal_qr_when_raster_fails(self):
        self.credentials.unlink()
        worker = f"http://{self.server.server_address[0]}:{self.server.server_address[1]}"
        with patch.object(core, "_render_login_qr_png", side_effect=OSError("no image stack")), \
                patch.object(core, "_print_login_qr") as terminal_qr, \
                patch.object(core.time, "sleep"):
            self.assertEqual(core.login(worker_url=worker), self.credentials)
        terminal_qr.assert_called_once()
        self.assertTrue(self.credentials.exists())

    def test_login_qr_png_is_large_pure_black_and_white(self):
        payload = "pigeonhub://login?request_id=plr_" + "a" * 32 + "&challenge=phc_" + "b" * 32
        try:
            qr_png = core._render_login_qr_png(payload)
        except ImportError:
            self.skipTest("qrcode[pil] not installed")
        try:
            from PIL import Image
            with Image.open(qr_png) as image:
                width, height = image.size
                colors = image.convert("RGB").getcolors(maxcolors=8)
        finally:
            core._remove_temp_qr(qr_png)
        self.assertGreaterEqual(width, 512)
        self.assertGreaterEqual(height, 512)
        self.assertEqual(width, height)
        self.assertEqual(len(colors), 2)
        self.assertEqual({color for _, color in colors}, {(0, 0, 0), (255, 255, 255)})


if __name__ == "__main__":
    unittest.main()
