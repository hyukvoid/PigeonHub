#!/usr/bin/env python3
"""PigeonHub AI-agent hook (MVP-009).

Vendor-neutral bridge for CLI coding agents (Claude Code, Codex CLI, z.ai …)
that support lifecycle hooks. Wire it into the agent's hook config:

  Claude Code (~/.claude/settings.json):
    {
      "hooks": {
        "SessionStart": [{"hooks": [{"type": "command",
          "command": "python pigeonhub_connector.py agent-start"}]}],
        "Notification": [{"hooks": [{"type": "command",
          "command": "python pigeonhub_connector.py agent-attention"}]}],
        "Stop":         [{"hooks": [{"type": "command",
          "command": "python pigeonhub_connector.py agent-done"}]}],
        "Stop (failure paths / manual aborts)": {"hooks": [{"type": "command",
          "command": "python pigeonhub_connector.py agent-failed"}]}
      }
    }

Hook events → PigeonHub job states:
    agent-start     → RUNNING        (inbox only)
    agent-attention → NEEDS_ACTION   (high-priority push; waiting on YOU)
    agent-done      → DONE           (push)
    agent-failed    → FAILED         (high-priority push)

The job id is stable per agent session, so start/attention/stop collapse into
ONE Job Card on the phone. Credentials come from `pair` (same as the CLI).
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pigeonhub_connector import load_credentials, publish_job  # noqa: E402

STATE = Path(__file__).resolve().parent / ".agent_session.json"


def read_hook_input():
    """Claude Code pipes a JSON object describing the hook event on stdin."""
    try:
        raw = sys.stdin.read()
        return json.loads(raw) if raw.strip() else {}
    except Exception:
        return {}


def session_key(hook):
    return (hook.get("session_id") or hook.get("tool_use_id") or "session")[:64]


def publish(state, reason=None, force=False):
    hook = read_hook_input()
    sid = session_key(hook)
    job_id = f"agent-{sid}"
    now = datetime.now(timezone.utc).isoformat()

    store = {}
    if STATE.exists():
        store = json.loads(STATE.read_text(encoding="utf-8"))
    if state == "RUNNING":
        store[job_id] = {"started_at": now}

    publish_job(
        "agent", job_id, state,
        job_name=hook.get("agent") or "Coding agent",
        started_at=store.get(job_id, {}).get("started_at"),
        finished_at=now if state in ("DONE", "FAILED") else None,
        attention=reason,
        result=reason if state == "DONE" else None,
        title=f"Coding agent: {reason or state.lower()}",
        message=reason or state,
        priority="high" if state in ("NEEDS_ACTION", "FAILED") else "normal",
    )
    STATE.write_text(json.dumps(store), encoding="utf-8")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "agent-start":
        publish("RUNNING")
    elif cmd == "agent-attention":
        publish("NEEDS_ACTION", reason=sys.argv[2] if len(sys.argv) > 2 else "waiting for your input")
    elif cmd == "agent-done":
        publish("DONE", reason=sys.argv[2] if len(sys.argv) > 2 else "task finished")
    elif cmd == "agent-failed":
        publish("FAILED", reason=sys.argv[2] if len(sys.argv) > 2 else "run failed")
    else:
        print(__doc__)
        sys.exit(1)
