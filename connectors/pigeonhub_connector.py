#!/usr/bin/env python3
"""PigeonHub connector — pair a local machine and publish Job events.

MVP-007/008 reference implementation. Zero dependencies (stdlib only).

Pairing (one-time, 10-minute code from the phone):
    python pigeonhub_connector.py pair

ComfyUI connector POC (simulates a ComfyUI queue watcher until a real
ComfyUI instance is available — real integration DEFERRED):
    python pigeonhub_connector.py comfyui-demo

Generic CLI job wrapper (MVP-008):
    python pigeonhub_connector.py run --name "Product crawler" -- python crawl.py

Progress from inside the job:
    python pigeonhub_connector.py progress 18431 50000

Credentials live in ~/.pigeonhub/credentials.json (created by `pair`).
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

CREDENTIALS_PATH = Path.home() / ".pigeonhub" / "credentials.json"


def load_credentials():
    if not CREDENTIALS_PATH.exists():
        sys.exit("Not paired. Run: python pigeonhub_connector.py pair")
    return json.loads(CREDENTIALS_PATH.read_text(encoding="utf-8"))


def http_json(url, payload=None, bearer=None, method=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method or ("POST" if data else "GET"))
    req.add_header("Content-Type", "application/json")
    # Cloudflare Bot Fight Mode rejects the default Python-urllib signature.
    req.add_header("User-Agent", "PigeonHubConnector/1.0")
    if bearer:
        req.add_header("Authorization", f"Bearer {bearer}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body[:300]}


def cmd_pair(args):
    code = input("Pairing code from the PigeonHub app (e.g. PHC-XXXXX-XXXXX-XXXXX): ").strip()
    status, body = http_json(
        "https://pigeonhub-push.pigeonhub.workers.dev/v1/pairing/redeem", {"code": code}
    )
    if status != 200 or not body.get("ok"):
        sys.exit(f"Pairing failed: {body.get('error', status)}")
    CREDENTIALS_PATH.parent.mkdir(parents=True, exist_ok=True)
    CREDENTIALS_PATH.write_text(
        json.dumps(
            {
                "endpoint": body["endpoint"],
                "write_token": body["write_token"],
                "channel_id": body["channel_id"],
                "paired_at": datetime.now(timezone.utc).isoformat(),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    try:
        os.chmod(CREDENTIALS_PATH, 0o600)
    except OSError:
        pass
    print(f"Paired with channel {body['channel_id'][:12]}… — credentials saved to {CREDENTIALS_PATH}")


def publish_job(source, job_id, state, *, job_name=None, progress=None, total=None,
                 attention=None, result=None, started_at=None, finished_at=None,
                 title=None, message=None, priority="normal"):
    creds = load_credentials()
    job = {"source": source, "job_id": job_id, "state": state}
    if job_name:
        job["job_name"] = job_name
    if progress is not None:
        job["progress_current"] = progress
    if total is not None:
        job["progress_total"] = total
    if attention:
        job["attention_reason"] = attention
    if result:
        job["result_summary"] = result
    if started_at:
        job["started_at"] = started_at
    if finished_at:
        job["finished_at"] = finished_at
    if title is None:
        title = f"{job_name or job_id}: {state.lower()}"
    if message is None:
        message = state
    payload = {"title": title[:500], "message": str(message)[:4000], "priority": priority, "job": job}
    # Terminal states MUST land: retry through transient (5xx) and quota (429)
    # rejections with backoff, or the user never learns the job finished.
    attempts = 8 if state in ("DONE", "FAILED", "NEEDS_ACTION") else 1
    ok = False
    for attempt in range(1, attempts + 1):
        status, body = http_json(creds["endpoint"], payload, bearer=creds["write_token"])
        ok = status in (200, 201) and bool(body.get("stored"))
        if ok:
            break
        retryable = status == 429 or status >= 500
        if not retryable or attempt == attempts:
            break
        wait = min(15, 2 ** attempt) + (0.3 * attempt)
        print(f"[pigeonhub] {state} HTTP {status}, retrying in {wait:.0f}s ({attempt}/{attempts})")
        time.sleep(wait)
    print(f"[pigeonhub] {state}" + (f" {progress}/{total}" if progress is not None else "")
          + (" ✓" if ok else f" ✗ HTTP {status}: {body}"))
    return ok


def cmd_comfyui_demo(args):
    """ComfyUI connector POC: mirrors the real queue watcher's event stream.

    Real ComfyUI integration is DEFERRED (no local ComfyUI install); this demo
    drives the identical connector contract against the production worker so
    the pairing + Job Inbox pipeline is exercised end-to-end.
    """
    job_id = f"comfy-{int(time.time())}"
    started = datetime.now(timezone.utc).isoformat()
    total_steps = 20
    publish_job("comfyui", job_id, "RUNNING", job_name="Video generation",
                started_at=started, title="ComfyUI: video generation",
                message="workflow queued")
    for step in range(1, total_steps + 1):
        time.sleep(1.5)
        publish_job("comfyui", job_id, "PROGRESS", job_name="Video generation",
                    progress=step, total=total_steps, started_at=started,
                    title="ComfyUI: video generation", message="rendering")
    finished = datetime.now(timezone.utc).isoformat()
    elapsed = round(time.mktime(time.strptime(started[:19], "%Y-%m-%dT%H:%M:%S")))
    took = int(time.time()) - elapsed
    publish_job("comfyui", job_id, "DONE", job_name="Video generation",
                started_at=started, finished_at=finished,
                result=f"1 video · {total_steps} steps · {took}s",
                title="ComfyUI: video generation", message="render complete", priority="high")
    print("Done — check the Job Inbox on your phone.")


def cmd_run(args):
    """MVP-008: wrap a local process as a PigeonHub job."""
    job_name = args.name or " ".join(args.cmd)
    job_id = f"cli-{int(time.time())}-{os.getpid()}"
    started = datetime.now(timezone.utc).isoformat()
    publish_job("cli", job_id, "RUNNING", job_name=job_name, started_at=started,
                title=f"{job_name}: started", message=" ".join(args.cmd))
    proc = subprocess.run(args.cmd)
    finished = datetime.now(timezone.utc).isoformat()
    if proc.returncode == 0:
        publish_job("cli", job_id, "DONE", job_name=job_name, started_at=started,
                    finished_at=finished, result=f"exit 0",
                    title=f"{job_name}: done", message="exit code 0", priority="high")
    else:
        publish_job("cli", job_id, "FAILED", job_name=job_name, started_at=started,
                    finished_at=finished, attention=f"exit code {proc.returncode}",
                    title=f"{job_name}: failed", message=f"exit code {proc.returncode}",
                    priority="high")
    sys.exit(proc.returncode)


def cmd_progress(args):
    """Report progress for the most recent RUNNING job (same session contract:
    the wrapper stores the job id in an env var it re-exports)."""
    env_path = Path(os.environ.get("PIGEONHUB_STATE", str(CREDENTIALS_PATH.parent / "state.json")))
    if not env_path.exists():
        sys.exit("No RUNNING job in this shell. Use `run` first.")
    state = json.loads(env_path.read_text(encoding="utf-8"))
    publish_job(state["source"], state["job_id"], "PROGRESS", job_name=state.get("job_name"),
                started_at=state.get("started_at"), progress=args.current, total=args.total)


def cmd_attention(args):
    state_path = Path(os.environ.get("PIGEONHUB_STATE", str(CREDENTIALS_PATH.parent / "state.json")))
    if not state_path.exists():
        sys.exit("No RUNNING job in this shell. Use `run` first.")
    state = json.loads(state_path.read_text(encoding="utf-8"))
    publish_job(state["source"], state["job_id"], "NEEDS_ACTION", job_name=state.get("job_name"),
                started_at=state.get("started_at"), attention=args.reason,
                title=f"{state.get('job_name')}: needs your action",
                message=args.reason, priority="high")


def main():
    parser = argparse.ArgumentParser(description="PigeonHub connector")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("pair", help="pair with the phone using a one-time code")
    sub.add_parser("comfyui-demo", help="ComfyUI connector POC (simulated queue)")
    run = sub.add_parser("run", help="wrap a local command as a job")
    run.add_argument("--name", help="human job name")
    run.add_argument("cmd", nargs="+", help="command to run")
    prog = sub.add_parser("progress", help="report progress for the running job")
    prog.add_argument("current", type=int)
    prog.add_argument("total", type=int)
    att = sub.add_parser("attention", help="raise a NEEDS_ACTION for the running job")
    att.add_argument("reason")
    args = parser.parse_args()
    if args.cmd == "pair":
        cmd_pair(args)
    elif args.cmd == "comfyui-demo":
        cmd_comfyui_demo(args)
    elif args.cmd == "run":
        cmd_run(args)
    elif args.cmd == "progress":
        cmd_progress(args)
    elif args.cmd == "attention":
        cmd_attention(args)


if __name__ == "__main__":
    main()
