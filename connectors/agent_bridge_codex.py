#!/usr/bin/env python3
"""MVP-014: real Codex CLI agent → PigeonHub job bridge.

Wraps a real `codex exec --json` session and maps its public JSONL event
stream onto the PigeonHub job contract (no vendor-private APIs):

    thread.started   -> RUNNING  (job id anchored to the real thread id)
    item.completed   -> PROGRESS (each finished turn item: message/command)
    turn.completed   -> PROGRESS (turn k, token usage)
    exit 0           -> DONE     (final message snippet)
    exit != 0 / error-> FAILED   (error output)

    python agent_bridge_codex.py [--name "My agent task"] <task prompt>

The stable job id (agent-<thread_id>) makes retries/continuations collapse
into ONE Job Card. Credentials come from `pigeonhub_connector.py pair`.
"""

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pigeonhub_connector import publish_job  # noqa: E402


def main():
    p = argparse.ArgumentParser(description="Report a real Codex exec session as a PigeonHub job")
    p.add_argument("--name", default="Codex agent", help="human job name")
    p.add_argument("--model", default=None, help="pass through to codex -m (also lets you test the FAILED path)")
    p.add_argument("task", help="task prompt for codex exec")
    args = p.parse_args()

    now = lambda: datetime.now(timezone.utc).isoformat()  # noqa: E731
    started = now()
    job_id = None
    turns = 0
    last_text = ""

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
        line = line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue  # non-JSON noise (banners, hook prints)
        etype = event.get("type")
        if etype == "thread.started":
            job_id = f"agent-{str(event.get('thread_id'))[:24]}"
            publish_job("agent", job_id, "RUNNING", job_name=args.name,
                        started_at=started, title=f"{args.name}: started",
                        message="agent session started")
        elif etype == "item.completed":
            item = event.get("item", {})
            if item.get("type") == "agent_message":
                last_text = str(item.get("text", ""))[:200]
            turns += 1
            if job_id:
                publish_job("agent", job_id, "PROGRESS", job_name=args.name,
                            progress=turns, started_at=started,
                            title=f"{args.name}: working",
                            message=f"step {turns}: {item.get('type', 'item')}")
        elif etype == "turn.completed":
            usage = event.get("usage", {}) or {}
            turns += 1
            if job_id:
                publish_job("agent", job_id, "PROGRESS", job_name=args.name,
                            progress=turns, started_at=started,
                            title=f"{args.name}: working",
                            message=f"turn {turns} done ({usage.get('total_tokens', '?')} tokens)")
        elif etype == "error":
            if job_id:
                publish_job("agent", job_id, "FAILED", job_name=args.name,
                            started_at=started, finished_at=now(),
                            attention=str(event.get("message", "agent error")),
                            title=f"{args.name}: failed", priority="high")
            proc.wait()
            sys.exit(3)

    code = proc.wait()
    if code == 0 and job_id:
        publish_job("agent", job_id, "DONE", job_name=args.name,
                    started_at=started, finished_at=now(),
                    result=f"{turns} step(s) · {last_text or 'finished'}",
                    title=f"{args.name}: done", message=last_text or "finished",
                    priority="high")
    elif job_id:
        publish_job("agent", job_id, "FAILED", job_name=args.name,
                    started_at=started, finished_at=now(),
                    attention=f"exit code {code}",
                    title=f"{args.name}: failed", message=f"exit code {code}",
                    priority="high")
    else:
        # The session died before the first event — nothing anchored on the phone.
        sys.exit(f"Codex exited {code} before producing a session id")
    sys.exit(code)


if __name__ == "__main__":
    main()
