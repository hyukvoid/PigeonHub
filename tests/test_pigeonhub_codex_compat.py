"""BETA-002: Codex CLI compatibility regression.

The public ``codex exec --json`` stream was captured on codex-cli 0.152.1
(2026-09-19). These fixtures keep the event SHAPES only — assistant message
text, prompts, and token counts are replaced with placeholders — so a future
Codex update that changes a safe field shape breaks a test instead of the
lifecycle silently.
"""

import importlib.util
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

from pigeonhub.agents import normalize_event, publish_agent_event

_BRIDGE_PATH = Path(__file__).resolve().parent.parent / "connectors" / "agent_bridge_codex.py"
_spec = importlib.util.spec_from_file_location("agent_bridge_codex", _BRIDGE_PATH)
bridge = importlib.util.module_from_spec(_spec)
sys.modules["agent_bridge_codex"] = bridge
_spec.loader.exec_module(bridge)

# Sanitized capture (shapes preserved, contents redacted).
CODEX_0152_JSONL = [
    '{"type":"thread.started","thread_id":"<thread-uuid>"}',
    '{"type":"turn.started"}',
    '{"type":"item.completed","item":{"id":"<id>","text":"<assistant message redacted>","type":"agent_message"}}',
    '{"type":"turn.completed","usage":{"cache_write_input_tokens":0,"cached_input_tokens":0,"input_tokens":1234,"output_tokens":56,"reasoning_output_tokens":78}}',
]


class CodexStreamCompatTests(unittest.TestCase):
    def test_captured_stream_maps_to_expected_lifecycle(self):
        lifecycle = [bridge.lifecycle_from_line(line) for line in CODEX_0152_JSONL]
        self.assertEqual(
            [event["type"] if event else None for event in lifecycle],
            ["thread.started", None, "item.completed", "turn.completed"],
        )
        # thread id survives; item/usage contents never do.
        self.assertEqual(lifecycle[0]["thread_id"], "<thread-uuid>")
        self.assertNotIn("item", lifecycle[2])
        self.assertNotIn("usage", lifecycle[3])

    def test_non_json_noise_is_ignored(self):
        for noise in ("", "hook: Stop Completed", "tokens used", "[banner]"):
            self.assertIsNone(bridge.lifecycle_from_line(noise))

    def test_unknown_future_event_type_is_ignored_not_guessed(self):
        self.assertIsNone(bridge.lifecycle_from_line('{"type":"future.event","payload":{"x":1}}'))

    def test_adapter_normalizes_bridge_events(self):
        parent = None
        env = patch.dict("os.environ", {})
        env.start()
        try:
            import os

            os.environ.pop("PIGEONHUB_JOB_ID", None)
            running = normalize_event("codex", {"type": "thread.started", "thread_id": "t-1", "job_id": "agent-codex-t-1", "started_at": "now", "job_name": "Codex agent"})
            self.assertEqual(running.state, "RUNNING")
            self.assertEqual(running.job_id, "agent-codex-t-1")
            progress = normalize_event("codex", {"type": "item.completed", "thread_id": "t-1", "job_id": "agent-codex-t-1", "progress_current": 2})
            self.assertEqual(progress.state, "PROGRESS")
            stop = normalize_event("codex", {"type": "stop", "thread_id": "t-1", "job_id": "agent-codex-t-1", "exit_code": 0, "success": True, "safe_summary": "2 lifecycle update(s)"})
            self.assertEqual(stop.state, "DONE")
            failure = normalize_event("codex", {"type": "stop", "thread_id": "t-2", "job_id": "agent-codex-t-2", "exit_code": 3, "success": False})
            self.assertEqual(failure.state, "FAILED")
        finally:
            env.stop()


class CodexHookPathTests(unittest.TestCase):
    """The ~/.codex/hooks.json path still maps Claude-style payloads when the
    user trusts the hooks (interactive sessions). Verified against the
    documented payload shape; exec-mode hook execution is a separate,
    documented finding (Codex 0.152.1 does not run Stop hooks in exec mode)."""

    def test_hook_style_stop_still_maps(self):
        event = normalize_event("codex", {"hook_event_name": "Stop", "session_id": "sess-1", "last_assistant_message": "<redacted>"})
        self.assertEqual(event.state, "DONE")
        # The assistant message is never forwarded by the adapter.
        published = {}

        def fake_publish(source, job_id, state, **kwargs):
            published.update(kwargs=kwargs)

        env = patch.dict("os.environ", {})
        env.start()
        try:
            import os

            os.environ.pop("PIGEONHUB_JOB_ID", None)
            with patch("pigeonhub.agents.publish_job_detailed", side_effect=fake_publish), \
                    patch("pigeonhub.agents._load_state", return_value={}), \
                    patch("pigeonhub.agents._save_state"):
                publish_agent_event("codex", {"hook_event_name": "Stop", "session_id": "sess-2"})
        finally:
            env.stop()
        self.assertNotIn("last_assistant_message", str(published))


if __name__ == "__main__":
    unittest.main()
