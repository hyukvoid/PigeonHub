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
            job = body.get("job") or {}
            if self.__class__.fail_running and job.get("state") == "RUNNING":
                self._json(503, {"error": "injected running failure"})
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

    def test_pc_first_login_displays_request_and_polls_after_approval(self):
        self.credentials.unlink()
        worker = f"http://{self.server.server_address[0]}:{self.server.server_address[1]}"
        with patch.object(core, "_print_login_qr"), patch.object(core.time, "sleep"):
            self.assertEqual(core.login(worker_url=worker), self.credentials)
        self.assertEqual(_Handler.login_polls, 2)
        self.assertTrue(self.credentials.exists())


if __name__ == "__main__":
    unittest.main()
