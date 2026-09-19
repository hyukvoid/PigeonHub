import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from pigeonhub import codex_integration


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
                "input-messages": ["PRIVATE_PROMPT_MARKER_XYZ"],
                "last-assistant-message": "PRIVATE_PROMPT_MARKER_XYZ",
                "cwd": "C:/PRIVATE_PROMPT_MARKER_XYZ",
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
            self.assertNotIn("PRIVATE_PROMPT_MARKER_XYZ", json.dumps(calls))
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


if __name__ == "__main__":
    unittest.main()
