#!/usr/bin/env python3
"""MVP-017: real Codex CLI agent → normalized PigeonHub job bridge.

Wraps a real ``codex exec --json`` session and maps public JSONL lifecycle
events onto the four-state PigeonHub Job Model. Prompts, source files, full
tool input, and agent messages are deliberately not forwarded.

BETA-002 compatibility note (verified against codex-cli 0.152.1): the public
event stream is ``thread.started``/``turn.started``/``item.completed``
(item = {id, text, type})/``turn.completed`` (usage = token counters), and
there is no terminal ``stop`` event in the stream — the bridge publishes the
terminal event itself from the process exit code.
"""

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from pigeonhub.agents import publish_agent_event  # noqa: E402

# Public event types the bridge understands; anything else is ignored.
LIFECYCLE_EVENT_TYPES = ("thread.started", "item.completed", "turn.completed", "error")


def lifecycle_from_line(line: str) -> dict | None:
    """One public JSONL line → an adapter-safe raw event, or None.

    Only the event type (and the thread id for ``thread.started``) is
    extracted; ``item`` payloads (assistant text) and ``usage`` counters are
    dropped here, so the allowlist privacy boundary is structural, not a
    matter of downstream discipline.
    """
    try:
        event = json.loads(line)
    except json.JSONDecodeError:
        return None  # non-JSON noise (banners, hook prints)
    if not isinstance(event, dict):
        return None
    etype = event.get("type")
    if etype == "thread.started":
        return {"type": etype, "thread_id": str(event.get("thread_id") or "session")}
    if etype in {"item.completed", "turn.completed", "error"}:
        return {"type": etype}
    return None


def main():
    parser = argparse.ArgumentParser(description="Report a real Codex exec session as a PigeonHub job")
    parser.add_argument("--name", default="Codex agent", help="human job name")
    parser.add_argument("--model", default=None, help="pass through to codex -m")
    parser.add_argument("task", help="task prompt for codex exec")
    args = parser.parse_args()

    now = lambda: datetime.now(timezone.utc).isoformat()  # noqa: E731
    started = now()
    job_id = None
    session_id = None
    turns = 0

    cmd = ["codex", "exec", "--json", "--skip-git-repo-check"]
    if args.model:
        cmd += ["-m", args.model]
    cmd.append(args.task)
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
    )
    assert proc.stdout is not None
    for line in proc.stdout:
        event = lifecycle_from_line(line.strip())
        if event is None:
            continue
        etype = event["type"]
        if etype == "thread.started":
            session_id = event["thread_id"]
            job_id = f"agent-codex-{session_id[:48]}"
            publish_agent_event(
                "codex",
                {"type": etype, "thread_id": session_id, "job_id": job_id, "started_at": started, "job_name": args.name},
                job_name=args.name,
            )
        elif etype in {"item.completed", "turn.completed"}:
            turns += 1
            if job_id and session_id:
                publish_agent_event(
                    "codex",
                    {"type": etype, "thread_id": session_id, "job_id": job_id, "started_at": started, "job_name": args.name, "progress_current": turns},
                    job_name=args.name,
                )
        elif etype == "error":
            if job_id and session_id:
                publish_agent_event(
                    "codex",
                    {"type": etype, "thread_id": session_id, "job_id": job_id, "started_at": started, "job_name": args.name, "terminal": True},
                    job_name=args.name,
                )
            proc.wait()
            sys.exit(3)

    code = proc.wait()
    if job_id and session_id:
        publish_agent_event(
            "codex",
            {"type": "stop", "thread_id": session_id, "job_id": job_id, "started_at": started, "job_name": args.name, "exit_code": code, "success": code == 0, "safe_summary": f"{turns} lifecycle update(s)"},
            job_name=args.name,
        )
    else:
        # The session died before the first event — nothing anchored on the phone.
        sys.exit(f"Codex exited {code} before producing a session id")
    sys.exit(code)


if __name__ == "__main__":
    main()
