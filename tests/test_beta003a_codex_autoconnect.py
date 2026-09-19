import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from pigeonhub import codex_integration, core


class CodexNotifyConfigTests(unittest.TestCase):
    def _which(self, name):
        return r"C:\fake\codex.exe" if name == "codex" else None

    def _home(self, text=None):
        directory = tempfile.TemporaryDirectory()
        home = Path(directory.name)
        config = home / ".codex" / "config.toml"
        if text is not None:
            config.parent.mkdir(parents=True)
            config.write_text(text, encoding="utf-8", newline="")
        return directory, home, config

    def test_install_is_minimal_backed_up_and_idempotent(self):
        directory, home, config = self._home(
            "# keep this comment\nmodel = \"gpt-5.6-sol\"\n\n[profiles.review]\nmodel = \"gpt-5.6-sol\"\n"
        )
        try:
            with patch.object(codex_integration.shutil, "which", side_effect=self._which), \
                    patch.dict("os.environ", {"PIGEONHUB_NOTIFY_EXECUTABLE": r"C:\fake\pigeonhub.exe"}, clear=False):
                plan = codex_integration.build_codex_notify_plan(home=home)
                self.assertEqual(plan.status, codex_integration.CONNECTED_BASIC)
                backup = codex_integration.apply_codex_notify_plan(plan)
                self.assertIsNotNone(backup)
                after = config.read_text(encoding="utf-8")
                self.assertIn("# keep this comment", after)
                self.assertIn('model = "gpt-5.6-sol"', after)
                self.assertIn("[profiles.review]", after)
                self.assertIn('notify = ["C:\\\\fake\\\\pigeonhub.exe", "internal-codex-notify"]', after)
                again = codex_integration.build_codex_notify_plan(home=home)
                self.assertFalse(again.changed)
                self.assertEqual(again.status, codex_integration.CONNECTED_BASIC)
        finally:
            directory.cleanup()

    def test_existing_unrelated_notify_is_preserved_and_needs_attention(self):
        directory, home, config = self._home('notify = ["existing-notifier.exe", "turn-ended"]\nmodel = "x"\n')
        before = config.read_text(encoding="utf-8")
        try:
            with patch.object(codex_integration.shutil, "which", side_effect=self._which):
                plan = codex_integration.build_codex_notify_plan(home=home)
                self.assertEqual(plan.status, codex_integration.NEEDS_ATTENTION)
                self.assertFalse(plan.changed)
                self.assertEqual(config.read_text(encoding="utf-8"), before)
        finally:
            directory.cleanup()

    def test_codex_home_without_executable_is_detected_without_config_write(self):
        directory, home, config = self._home('model = "x"\n')
        before = config.read_text(encoding="utf-8")
        try:
            with patch.object(codex_integration.shutil, "which", return_value=None):
                result = codex_integration.connect_codex(home=home)
            self.assertEqual(result.status, codex_integration.DETECTED)
            self.assertEqual(config.read_text(encoding="utf-8"), before)
        finally:
            directory.cleanup()

    def test_malformed_toml_is_not_overwritten(self):
        directory, home, config = self._home('model = "unterminated\n')
        before = config.read_text(encoding="utf-8")
        try:
            with patch.object(codex_integration.shutil, "which", side_effect=self._which):
                plan = codex_integration.build_codex_notify_plan(home=home)
                self.assertEqual(plan.status, codex_integration.NEEDS_ATTENTION)
                self.assertFalse(plan.changed)
                self.assertEqual(config.read_text(encoding="utf-8"), before)
        finally:
            directory.cleanup()

    def test_atomic_write_failure_keeps_original_config(self):
        directory, home, config = self._home('model = "x"\n')
        before = config.read_text(encoding="utf-8")
        try:
            with patch.object(codex_integration.shutil, "which", side_effect=self._which), \
                    patch.dict("os.environ", {"PIGEONHUB_NOTIFY_EXECUTABLE": r"C:\fake\pigeonhub.exe"}, clear=False), \
                    patch.object(codex_integration, "_atomic_write_text", side_effect=codex_integration.CodexIntegrationError("injected write failure")):
                result = codex_integration.connect_codex(home=home)
            self.assertEqual(result.status, codex_integration.NEEDS_ATTENTION)
            self.assertEqual(config.read_text(encoding="utf-8"), before)
        finally:
            directory.cleanup()

    def test_remove_only_managed_notify(self):
        directory, home, config = self._home("model = \"x\"\n")
        try:
            with patch.object(codex_integration.shutil, "which", side_effect=self._which), \
                    patch.dict("os.environ", {"PIGEONHUB_NOTIFY_EXECUTABLE": r"C:\fake\pigeonhub.exe"}, clear=False):
                codex_integration.apply_codex_notify_plan(codex_integration.build_codex_notify_plan(home=home))
                remove = codex_integration.build_codex_notify_plan(home=home, remove=True)
                self.assertTrue(remove.changed)
                codex_integration.apply_codex_notify_plan(remove)
                after = config.read_text(encoding="utf-8")
                self.assertNotIn("PigeonHub: Codex completion notifications", after)
                self.assertNotIn("notify =", after)
                self.assertIn('model = "x"', after)
        finally:
            directory.cleanup()


class CodexNotifyPrivacyTests(unittest.TestCase):
    def test_callback_allowlists_completion_ids_and_drops_private_fields(self):
        directory = tempfile.TemporaryDirectory()
        state = Path(directory.name) / "notify-state.json"
        payload = json.dumps(
            {
                "type": "agent-turn-complete",
                "thread-id": "thread-safe-1",
                "turn-id": "turn-safe-1",
                "input-messages": ["PRIVATE_CODEX_PROMPT_MARKER_003A"],
                "last-assistant-message": "PRIVATE_CODEX_PROMPT_MARKER_003A",
                "cwd": "C:/PRIVATE_CODEX_PROMPT_MARKER_003A",
                "transcript_path": "C:/private/transcript.jsonl",
                "usage": {"output_tokens": 99},
            }
        )
        calls = []

        def fake_publish(source, job_id, state_name, **kwargs):
            calls.append((source, job_id, state_name, kwargs))
            return SimpleNamespace(ok=True)

        try:
            with patch.dict("os.environ", {"PIGEONHUB_CODEX_NOTIFY_STATE": str(state)}, clear=False), \
                    patch("pigeonhub.core.publish_job_detailed", side_effect=fake_publish):
                self.assertEqual(codex_integration.handle_codex_notify(payload), 0)
                self.assertEqual(codex_integration.handle_codex_notify(payload), 0)
            self.assertEqual(len(calls), 1)
            source, job_id, state_name, kwargs = calls[0]
            self.assertEqual((source, state_name), ("codex", "DONE"))
            self.assertIn("thread-safe-1", job_id)
            self.assertIn("turn-safe-1", job_id)
            self.assertNotIn("PRIVATE_CODEX_PROMPT_MARKER_003A", json.dumps(calls))
            self.assertNotIn("input-messages", kwargs)
            self.assertNotIn("last-assistant-message", kwargs)
            self.assertNotIn("cwd", kwargs)
            self.assertNotIn("usage", kwargs)
        finally:
            directory.cleanup()

    def test_bridge_marker_drops_notify_without_publishing(self):
        payload = json.dumps({"type": "agent-turn-complete", "thread-id": "t", "turn-id": "u"})
        with patch.dict("os.environ", {"PIGEONHUB_CODEX_BRIDGED": "1"}, clear=False), \
                patch("pigeonhub.core.publish_job_detailed") as publish:
            self.assertEqual(codex_integration.handle_codex_notify(payload), 0)
            publish.assert_not_called()


class CodexNotifyNegativePathTests(unittest.TestCase):
    """The callback is fail-closed: anything unexpected is dropped, never published."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.state = Path(self.directory.name) / "notify-state.json"
        self.env = patch.dict(
            "os.environ",
            {"PIGEONHUB_CODEX_NOTIFY_STATE": str(self.state)},
            clear=False,
        )
        self.env.start()

    def tearDown(self):
        self.env.stop()
        self.directory.cleanup()

    def _run(self, raw, publish):
        with patch("pigeonhub.core.publish_job_detailed", side_effect=publish):
            return codex_integration.handle_codex_notify(raw)

    def test_malformed_json_is_dropped(self):
        for raw in ("", "not json", "{", "[]", "3", "null"):
            self.assertEqual(self._run(raw, self._boom), 0, raw)
        self.assertFalse(self.state.exists())

    def _boom(self, *args, **kwargs):
        raise AssertionError("publish must not be called")

    def test_unknown_event_type_is_dropped(self):
        payload = json.dumps({"type": "session.end", "thread-id": "t", "turn-id": "u"})
        self.assertEqual(self._run(payload, self._boom), 0)

    def test_oversized_payload_is_dropped_wholesale(self):
        big = json.dumps(
            {
                "type": "agent-turn-complete",
                "thread-id": "t" + "x" * (codex_integration.MAX_NOTIFY_PAYLOAD_BYTES),
                "turn-id": "u",
            }
        )
        self.assertGreater(len(big.encode()), codex_integration.MAX_NOTIFY_PAYLOAD_BYTES)
        self.assertEqual(self._run(big, self._boom), 0)

    def test_missing_identifiers_are_dropped(self):
        payload = json.dumps({"type": "agent-turn-complete"})
        self.assertEqual(self._run(payload, self._boom), 0)

    def test_hostile_identifier_text_is_sanitized_not_transmitted(self):
        calls = []

        def fake_publish(source, job_id, state_name, **kwargs):
            calls.append(job_id)
            return SimpleNamespace(ok=True)

        payload = json.dumps(
            {
                "type": "agent-turn-complete",
                "thread-id": "../../..\\..\\Windows\\system32 & del /q *",
                "turn-id": "u",
            }
        )
        self.assertEqual(self._run(payload, fake_publish), 0)
        self.assertEqual(len(calls), 1)
        self.assertNotIn("..", calls[0])
        self.assertNotIn("\\", calls[0])
        self.assertNotIn("&", calls[0])

    def test_failed_publish_keeps_replay_possible(self):
        """Network outage: nothing is recorded, so a later replay still notifies."""
        payload = json.dumps({"type": "agent-turn-complete", "thread-id": "t", "turn-id": "u"})
        calls = []

        def fake_publish(source, job_id, state_name, **kwargs):
            calls.append(state_name)
            return SimpleNamespace(ok=len(calls) > 2)

        self.assertEqual(self._run(payload, fake_publish), 0)  # outage
        self.assertEqual(self._run(payload, fake_publish), 0)  # retry still allowed
        self.assertEqual(self._run(payload, fake_publish), 0)  # success
        self.assertEqual(self._run(payload, self._boom), 0)  # replay after success: deduped
        self.assertEqual(calls, ["DONE", "DONE", "DONE"])
        self.assertTrue(self.state.exists())


class CodexBridgeDedupTests(unittest.TestCase):
    def test_wrapped_codex_run_is_single_card_and_notify_is_suppressed(self):
        if os.name != "nt":
            self.skipTest("Windows .cmd shim behavior")
        directory = tempfile.TemporaryDirectory()
        marker = Path(directory.name) / "marker.txt"
        shim = Path(directory.name) / "codex.cmd"
        shim.write_text(
            "@echo off\r\n"
            f"set PIGEONHUB_CODEX_BRIDGED > \"{marker}\"\r\n"
            "exit /b 0\r\n",
            encoding="utf-8",
        )
        publishes = []

        def fake_publish(source, job_id, state_name, **kwargs):
            publishes.append((state_name, kwargs.get("job_name")))
            return SimpleNamespace(ok=True)

        payload = json.dumps(
            {
                "type": "agent-turn-complete",
                "thread-id": "bridge-thread",
                "turn-id": "bridge-turn",
                "input-messages": ["PRIVATE_CODEX_PROMPT_MARKER_003A"],
            }
        )
        try:
            with patch("pigeonhub.core.publish_job_detailed", side_effect=fake_publish):
                exit_code = core.run_job([str(shim), "exec", payload], name=None)
                self.assertEqual(exit_code, 0)
                # The child inherited the bridge marker exactly like a real
                # codex exec child would, so its native notify is a no-op.
                self.assertIn(b"PIGEONHUB_CODEX_BRIDGED=1", marker.read_bytes())
                with patch.dict("os.environ", {"PIGEONHUB_CODEX_BRIDGED": "1"}, clear=False):
                    self.assertEqual(codex_integration.handle_codex_notify(payload), 0)
            self.assertEqual([state for state, _ in publishes], ["RUNNING", "DONE"])
            self.assertTrue(all(name == "Codex session" for _, name in publishes))
            for _, name in publishes:
                self.assertNotIn("PRIVATE_CODEX_PROMPT_MARKER_003A", name or "")
        finally:
            directory.cleanup()


if __name__ == "__main__":
    unittest.main()
