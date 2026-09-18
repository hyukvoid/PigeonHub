"""BETA-001B real-process E2E matrix against a local worker.

Every test drives the real `pigeonhub` CLI in a subprocess — recipes resolve
locally, publish through the existing run lifecycle, and never transmit the
command or prompt.
"""

import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


class Handler(BaseHTTPRequestHandler):
    events: list[dict] = []
    publish_mode = "ok"

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
            self._json(200, {"ok": True})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        if self.path.endswith("/messages"):
            Handler.events.append(body)
            if Handler.publish_mode == "reject":
                self._json(500, {"error": "injected outage"})
            else:
                self._json(201, {"stored": True, "message_id": f"m{len(Handler.events)}"})
            return
        self._json(404, {"error": "not found"})


class RecipeE2E(unittest.TestCase):
    def setUp(self):
        Handler.events = []
        Handler.publish_mode = "ok"
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        host, port = self.server.server_address
        (self.base / "cred.json").write_text(json.dumps({
            "endpoint": f"http://{host}:{port}/v1/channels/ch_e2e/messages",
            "write_token": "pct_e2e",
            "channel_id": "ch_e2e",
            "paired_at": "now",
        }), encoding="utf-8")
        self.env = os.environ.copy()
        self.env.update({
            "PIGEONHUB_CREDENTIALS": str(self.base / "cred.json"),
            "PIGEONHUB_RECIPES": str(self.base / "recipes.v1.json"),
            "PIGEONHUB_STATE": str(self.base / "state.json"),
        })

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.temp.cleanup()

    def pigeonhub(self, *args, timeout=120, input_text=None, extra_env=None):
        env = self.env.copy()
        if extra_env:
            env.update(extra_env)
        proc = subprocess.run(
            [sys.executable, "-m", "pigeonhub", *args],
            capture_output=True, text=True, timeout=timeout, env=env, cwd=str(REPO),
            input=input_text if input_text is not None else "",
        )
        return proc

    def states(self):
        return [event["job"]["state"] for event in Handler.events if "job" in event]

    def job_names(self):
        return {event["job"].get("job_name") for event in Handler.events if "job" in event}

    def test_generic_recipe_success_and_privacy(self):
        child = "import time; print('crawler ran'); time.sleep(1)"
        proc = self.pigeonhub("recipe", "add", "--name", "Nightly crawler", "--", sys.executable, "-c", child)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertIn("Recipe 'nightly-crawler' created.", proc.stdout)

        proc = self.pigeonhub("recipe", "run", "nightly-crawler")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertEqual(self.states(), ["RUNNING", "DONE"])
        # The card shows the recipe display name; the command/prompt never travel.
        self.assertIn("Nightly crawler", self.job_names())
        for event in Handler.events:
            serialized = json.dumps(event)
            self.assertNotIn("crawler ran", serialized)
            self.assertNotIn("python", serialized)
            self.assertNotIn("-c", serialized)

    def test_failure_preserves_child_exit_code(self):
        child = "import sys; print('boom'); raise SystemExit(42)"
        self.pigeonhub("recipe", "add", "--name", "Failing crawler", "--", sys.executable, "-c", child)
        proc = self.pigeonhub("recipe", "run", "failing-crawler")
        self.assertEqual(proc.returncode, 42)
        self.assertEqual(self.states(), ["RUNNING", "FAILED"])

    def test_runtime_prompt_beats_default_prompt(self):
        child = "import json, os, sys; open(os.environ['MARKER'], 'w', encoding='utf-8').write(sys.argv[1]); raise SystemExit(0)"
        self.env["MARKER"] = str(self.base / "prompt.txt")
        proc = self.pigeonhub(
            "recipe", "add",
            "--name", "Prompted tool", "--prompt", "stored default prompt",
            "--", sys.executable, "-c", child, "{prompt}",
        )
        self.assertEqual(proc.returncode, 0, proc.stderr)

        proc = self.pigeonhub("recipe", "run", "prompted-tool", "--prompt", "runtime prompt")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertEqual((self.base / "prompt.txt").read_text(encoding="utf-8"), "runtime prompt")
        for event in Handler.events:
            self.assertNotIn("runtime prompt", json.dumps(event))
            self.assertNotIn("stored default prompt", json.dumps(event))

        proc = self.pigeonhub("recipe", "run", "prompted-tool")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertEqual((self.base / "prompt.txt").read_text(encoding="utf-8"), "stored default prompt")

    def test_prompt_file_avoids_shell_history(self):
        child = "import os, sys; open(os.environ['MARKER'], 'w', encoding='utf-8').write(sys.argv[1])"
        self.env["MARKER"] = str(self.base / "prompt.txt")
        secret_file = self.base / "secret-prompt.txt"
        secret_file.write_text("quiet file prompt", encoding="utf-8")
        self.pigeonhub("recipe", "add", "--name", "File prompt", "--", sys.executable, "-c", child, "{prompt}")
        proc = self.pigeonhub("recipe", "run", "file-prompt", "--prompt-file", str(secret_file))
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertEqual((self.base / "prompt.txt").read_text(encoding="utf-8"), "quiet file prompt")
        self.assertNotIn("quiet file prompt", proc.stdout + proc.stderr)

    def test_missing_prompt_fails_without_starting_job(self):
        self.pigeonhub("recipe", "add", "--name", "Needs prompt", "--", sys.executable, "-c", "print(1)", "{prompt}")
        proc = self.pigeonhub("recipe", "run", "needs-prompt")
        self.assertEqual(proc.returncode, 1)
        self.assertIn("needs a prompt", proc.stdout + proc.stderr)
        self.assertEqual(Handler.events, [])

    def test_injection_prompt_is_data_only(self):
        child = (
            "import json, os, sys;"
            "open(os.environ['MARKER'], 'w', encoding='utf-8').write(json.dumps(sys.argv[1:]));"
            "raise SystemExit(0)"
        )
        self.env["MARKER"] = str(self.base / "argv.json")
        malicious = 'hello & whoami | dir > injected.txt && exit 99 " quoted " %PATH% !VAR! 한글 프롬프트 & echo PWNED'
        self.pigeonhub("recipe", "add", "--name", "Injection probe", "--", sys.executable, "-c", child, "{prompt}")
        proc = self.pigeonhub("recipe", "run", "injection-probe", "--prompt", malicious)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        combined = proc.stdout + proc.stderr
        self.assertNotIn("PWNED", combined, "a second command executed")
        self.assertNotIn("whoami", combined)
        received = json.loads((self.base / "argv.json").read_text(encoding="utf-8"))
        self.assertEqual(received, [malicious])
        self.assertFalse((self.base / "injected.txt").exists())

    def test_korean_name_and_spaces_in_cwd(self):
        workdir = self.base / "work dir with spaces"
        workdir.mkdir()
        script = workdir / "probe.py"
        script.write_text("import os, json; print(json.dumps({'cwd': os.getcwd()}))", encoding="utf-8")
        proc = self.pigeonhub("recipe", "add", "--name", "한국어 리서치", "--cwd", str(workdir), "--", sys.executable, str(script))
        self.assertEqual(proc.returncode, 0, proc.stderr)
        slug = proc.stdout.split("Recipe '")[1].split("'")[0]
        proc = self.pigeonhub("recipe", "run", slug)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertIn("work dir with spaces", proc.stdout)
        self.assertIn("한국어 리서치", " ".join(sorted(self.job_names())))

    def test_windows_cmd_shim_is_routed_through_cmd(self):
        if os.name != "nt":
            self.skipTest("Windows .cmd shim behavior")
        marker = self.base / "shim-marker.txt"
        shim = self.base / "ph-shim-recipe.cmd"
        shim.write_text(f"@echo off\r\ntype nul > \"{marker}\"\r\nexit /b 0\r\n", encoding="utf-8")
        proc = self.pigeonhub("recipe", "add", "--name", "Batch shim", "--", shim.stem, "ignored")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        env = self.env.copy()
        env["PATH"] = str(self.base) + os.pathsep + env["PATH"]
        proc = subprocess.run(
            [sys.executable, "-m", "pigeonhub", "recipe", "run", "batch-shim"],
            capture_output=True, text=True, timeout=60, env=env, cwd=str(REPO),
        )
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertTrue(marker.exists())
        self.assertEqual(self.states(), ["RUNNING", "DONE"])

    def test_missing_executable_leaves_no_running_job(self):
        self.pigeonhub("recipe", "add", "--name", "Ghost tool", "--", "pigeonhub-missing-e2e-9f3")
        proc = self.pigeonhub("recipe", "run", "ghost-tool")
        self.assertEqual(proc.returncode, 1)
        self.assertIn("Command not found", proc.stdout + proc.stderr)
        self.assertEqual(Handler.events, [])

    def test_publish_outage_never_starts_the_command(self):
        child = "print('MUST-NOT-RUN')"
        self.pigeonhub("recipe", "add", "--name", "Blocked job", "--", sys.executable, "-c", child)
        Handler.publish_mode = "reject"
        proc = self.pigeonhub("recipe", "run", "blocked-job")
        self.assertEqual(proc.returncode, 78)
        self.assertNotIn("MUST-NOT-RUN", proc.stdout + proc.stderr)

    def test_long_job_reports_progress(self):
        script = self.base / "long_child.py"
        script.write_text(
            "import sys, time\n"
            "from pigeonhub.core import report_progress\n"
            "for i in range(1, 4):\n"
            "    time.sleep(22)\n"
            "    assert report_progress(i, 3)\n",
            encoding="utf-8",
        )
        self.pigeonhub("recipe", "add", "--name", "Long job", "--", sys.executable, str(script))
        started = time.time()
        proc = self.pigeonhub("recipe", "run", "long-job", timeout=240)
        elapsed = time.time() - started
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertGreaterEqual(elapsed, 60, "the long job must really run over a minute")
        self.assertEqual(self.states(), ["RUNNING", "PROGRESS", "PROGRESS", "PROGRESS", "DONE"])

    def test_long_job_ctrl_c_stops_wrapper_and_child(self):
        if os.name != "nt":
            self.skipTest("Windows console control events")
        child = "import time, sys\nprint('child started', flush=True)\nopen(os.environ['CTRL_MARKER'], 'w').write('started')\n" if False else "import os, time\nprint('child started', flush=True)\nopen(os.environ['CTRL_MARKER'], 'w', encoding='utf-8').write('started')\ntime.sleep(120)\n"
        self.env["CTRL_MARKER"] = str(self.base / "ctrl-marker.txt")
        self.pigeonhub("recipe", "add", "--name", "Interruptible job", "--", sys.executable, "-c", child)
        env = self.env.copy()
        proc = subprocess.Popen(
            [sys.executable, "-m", "pigeonhub", "recipe", "run", "interruptible-job"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env, cwd=str(REPO),
            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP,
        )
        deadline = time.time() + 60
        while time.time() < deadline and not (self.base / "ctrl-marker.txt").exists():
            time.sleep(0.5)
        self.assertTrue((self.base / "ctrl-marker.txt").exists(), "child never started")
        proc.send_signal(subprocess.signal.CTRL_BREAK_EVENT if hasattr(proc, "send_signal") else 1)
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            proc.kill()
            self.fail("recipe wrapper hung after Ctrl+C")
        # The wrapper must exit non-zero without publishing DONE.
        self.assertEqual([s for s in self.states() if s == "RUNNING"].count("RUNNING"), 1)
        self.assertNotIn("DONE", self.states())


if __name__ == "__main__":
    unittest.main()
