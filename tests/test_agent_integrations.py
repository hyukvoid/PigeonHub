import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from pigeonhub import agents


class AgentNormalizationTests(unittest.TestCase):
    def test_post_tool_failure_is_recoverable_progress(self):
        event = agents.normalize_event(
            "zcode",
            {"hook_event_name": "PostToolUseFailure", "session_id": "s1", "error": "private source details"},
        )
        self.assertIsNotNone(event)
        self.assertEqual(event.state, "PROGRESS")
        self.assertIsNone(event.attention_reason)
        self.assertIsNone(event.result_summary)

    def test_permission_request_is_needs_action_but_notification_is_not_by_default(self):
        permission = agents.normalize_event("codex", {"type": "PermissionRequest", "session_id": "s1"})
        notification = agents.normalize_event("claude", {"type": "Notification", "session_id": "s1", "message": "working"})
        self.assertEqual(permission.state, "NEEDS_ACTION")
        self.assertIsNone(notification)

    def test_sensitive_fields_are_not_used_as_summary_or_deep_link_query(self):
        event = agents.normalize_event(
            "grok",
            {
                "type": "Stop",
                "session_id": "s1",
                "last_assistant_message": "full transcript and source code",
                "deep_link": "https://example.test/jobs/1?token=secret",
            },
        )
        self.assertEqual(event.state, "DONE")
        self.assertEqual(event.result_summary, "Agent session completed")
        self.assertEqual(event.deep_link, "https://example.test/jobs/1")
        self.assertIsNone(agents.normalize_event("codex", {"type": "Stop", "session_id": "s2", "deep_link": "https://user:secret@example.test/jobs/2"}).deep_link)


class AgentSetupTests(unittest.TestCase):
    def _which(self, name):
        return f"C:/fake/{name}.exe" if name in {"codex", "claude", "grok", "zcode"} else None

    def test_codex_setup_preserves_user_hooks_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            target = home / ".codex" / "hooks.json"
            target.parent.mkdir(parents=True)
            target.write_text(json.dumps({"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "user-hook"}]}]}}), encoding="utf-8")
            with patch.object(agents.shutil, "which", side_effect=self._which):
                first = agents.build_setup_plan("codex", home=home)
                backup = agents.apply_setup(first)
                second = agents.build_setup_plan("codex", home=home)
                self.assertTrue(first.changed)
                self.assertIsNotNone(backup)
                self.assertFalse(second.changed)
                value = json.loads(target.read_text(encoding="utf-8"))
                stop_hooks = [hook for group in value["hooks"]["Stop"] for hook in group["hooks"]]
                self.assertEqual(sum(h.get("command") == "pigeonhub agent-event codex" for h in stop_hooks), 1)
                self.assertIn({"type": "command", "command": "user-hook"}, stop_hooks)
                removed = agents.build_setup_plan("codex", home=home, remove=True)
                agents.remove_setup(removed)
                after = json.loads(target.read_text(encoding="utf-8"))
                remaining = [hook for group in after["hooks"]["Stop"] for hook in group["hooks"]]
                self.assertEqual(remaining, [{"type": "command", "command": "user-hook"}])

    def test_zcode_uses_user_level_events_and_restores_enabled_flag(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            target = home / ".zcode" / "cli" / "config.json"
            target.parent.mkdir(parents=True)
            target.write_text(json.dumps({"hooks": {"enabled": False, "events": {}}}), encoding="utf-8")
            with patch.object(agents.shutil, "which", side_effect=self._which):
                plan = agents.build_setup_plan("zcode", home=home)
                agents.apply_setup(plan)
                value = json.loads(target.read_text(encoding="utf-8"))
                self.assertTrue(value["hooks"]["enabled"])
                self.assertEqual(agents._managed_count(value, "zcode"), len(agents._hook_events("zcode")))
                remove = agents.build_setup_plan("zcode", home=home, remove=True)
                agents.remove_setup(remove)
                restored = json.loads(target.read_text(encoding="utf-8"))
                self.assertFalse(restored["hooks"]["enabled"])
                self.assertEqual(agents._managed_count(restored, "zcode"), 0)

    def test_zcode_handler_matches_strict_process_hook_schema(self):
        # ZCode validates hook entries with a strict schema that allows only
        # type/command/args/timeoutMs on a process hook; an extra key such as
        # "enabled" makes the runner drop the hook silently.
        allowed = {"type", "command", "args", "timeoutMs"}
        handler = agents._hook_handler("zcode")
        self.assertEqual(set(handler), allowed)
        self.assertEqual(handler["type"], "process")
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            target = home / ".zcode" / "cli" / "config.json"
            target.parent.mkdir(parents=True)
            with patch.object(agents.shutil, "which", side_effect=self._which):
                agents.apply_setup(agents.build_setup_plan("zcode", home=home))
                value = json.loads(target.read_text(encoding="utf-8"))
                for groups in value["hooks"]["events"].values():
                    for group in groups:
                        for hook in group["hooks"]:
                            self.assertTrue(set(hook) <= allowed, hook)


if __name__ == "__main__":
    unittest.main()
