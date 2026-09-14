#!/usr/bin/env python3
"""MVP-012: real ComfyUI → PigeonHub job bridge.

Runs on the PC that hosts ComfyUI (same requirement set as ComfyUI itself:
aiohttp comes with the server's own dependencies — no extra installs).

    python comfyui_connector.py submit workflow.json [--name "My render"]
        Queue the workflow on the local ComfyUI and map its WebSocket
        lifecycle onto the PigeonHub job contract:
          queued            -> RUNNING  (queue position)
          execution_start   -> RUNNING  (elapsed anchor)
          progress          -> PROGRESS (sampler step k/N)
          executing         -> PROGRESS (node k/N when no sampler progress)
          execution_success -> DONE     (output count + elapsed)
          execution_error   -> FAILED   (node type + message)
        The WebSocket is opened BEFORE the workflow is queued and /history is
        polled as a fallback, so a job that finishes in milliseconds still
        reaches its terminal state. Events flow through the normal worker
        pipeline (coalescing, attention policy, FCM fan-out) — no
        ComfyUI-specific server logic.

    python comfyui_connector.py watch
        Watch the same WebSocket for ANY prompt queued from the ComfyUI web
        UI (or any other client) and report each as a PigeonHub job. The
        ComfyUI prompt_id is used as the job_id so retries coalesce.
"""

import argparse
import asyncio
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import aiohttp

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pigeonhub_connector import publish_job  # noqa: E402

COMFY_HOST = os.environ.get("PIGEONHUB_COMFY_HOST", "127.0.0.1:8188")
CLIENT_ID = f"pigeonhub-{int(time.time())}"
HISTORY_POLL_SEC = 3.0


def _count_outputs(history_outputs):
    """Count output artifacts from a /history entry's outputs — that shape is
    keyed by node id ({\"2\": {\"images\": [...]}}), unlike the WS `executed`
    event which carries the node's own output dict directly."""
    n = 0
    for node_out in (history_outputs or {}).values():
        if isinstance(node_out, dict):
            for v in node_out.values():
                if isinstance(v, list):
                    n += len(v)
    return n


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _publish_failed(prompt_id, job_name, started, reason):
    publish_job("comfyui", prompt_id, "FAILED", job_name=job_name,
                started_at=started, finished_at=_now_iso(), attention=reason[:300],
                title=f"{job_name}: failed", message=reason[:300], priority="high")


async def _history_terminal(session, prompt_id, job_name, started):
    """Publish DONE/FAILED from /history if the prompt already finished. True when terminal."""
    try:
        async with session.get(f"http://{COMFY_HOST}/history/{prompt_id}") as resp:
            hist = await resp.json()
    except Exception:
        return False
    entry = hist.get(prompt_id)
    if not entry:
        return False
    status = entry.get("status", {})
    out_count = _count_outputs(entry.get("outputs", {}))
    if status.get("status_str") == "error" or status.get("completed") is False:
        # Pull the exception message out of the status messages if we can.
        reason = "execution error"
        for m in status.get("messages", []):
            if m and m[0] == "execution_error":
                d = m[1] if len(m) > 1 else {}
                reason = f"{d.get('node_type', '?')}: {d.get('exception_message', 'execution error')}"
                break
        _publish_failed(prompt_id, job_name, started, reason)
        return True
    if status.get("status_str") == "success" or status.get("completed"):
        elapsed = ""
        if started:
            try:
                t0 = datetime.fromisoformat(started).timestamp()
                elapsed = f" · {time.time() - t0:.0f}s"
            except ValueError:
                pass
        publish_job("comfyui", prompt_id, "DONE", job_name=job_name,
                    started_at=started, finished_at=_now_iso(),
                    result=f"{out_count} output(s){elapsed}",
                    title=f"{job_name}: done", message="complete", priority="high")
        return True
    return False


async def _drain_prompt(session, ws, prompt_id, *, job_name, total_nodes, announce_start):
    """Consume WS frames for one prompt until it reaches a terminal state.

    Falls back to /history polling whenever the socket goes quiet, so a job
    that completes before the handshake (or races the first frames) is still
    reported. Returns after publishing a terminal state.
    """
    state = {"node": 0, "sampler_seen": False, "outputs": 0, "started": None}
    while True:
        try:
            msg = await asyncio.wait_for(ws.receive(), timeout=HISTORY_POLL_SEC)
        except asyncio.TimeoutError:
            if await _history_terminal(session, prompt_id, job_name, state["started"]):
                return True
            continue
        if msg.type != aiohttp.WSMsgType.TEXT:
            if msg.type in (aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
                if await _history_terminal(session, prompt_id, job_name, state["started"]):
                    return True
                _publish_failed(prompt_id, job_name, state["started"],
                                "ComfyUI connection lost")
                return True
            continue  # binary preview frame — skip
        data = json.loads(msg.data)
        mtype = data.get("type")
        payload = data.get("data", {})
        if payload.get("prompt_id") and payload["prompt_id"] != prompt_id:
            continue
        if mtype == "execution_start":
            state["started"] = _now_iso()
            if announce_start:
                publish_job("comfyui", prompt_id, "RUNNING", job_name=job_name,
                            started_at=state["started"], title=f"{job_name}: running",
                            message="execution started")
                announce_start = False
        elif mtype == "progress":
            if not state["sampler_seen"]:
                state["sampler_seen"] = True  # first sampler step switches the progress model
            v, m = payload.get("value", 0), payload.get("max", 0)
            publish_job("comfyui", prompt_id, "PROGRESS", job_name=job_name,
                        progress=v, total=m, started_at=state["started"],
                        title=f"{job_name}: rendering", message=f"step {v}/{m}")
        elif mtype == "executing":
            node = payload.get("node")
            if node is None:
                continue  # legacy end-of-execution signal; wait for the explicit event
            if not state["sampler_seen"] and total_nodes:
                state["node"] += 1
                publish_job("comfyui", prompt_id, "PROGRESS", job_name=job_name,
                            progress=state["node"], total=total_nodes,
                            started_at=state["started"], title=f"{job_name}: running",
                            message=f"node {state['node']}/{total_nodes} ({node})")
        elif mtype == "executed":
            out = payload.get("output", {})
            state["outputs"] += sum(len(v) for v in out.values() if isinstance(v, list))
        elif mtype == "execution_success":
            elapsed = ""
            if state["started"]:
                try:
                    t0 = datetime.fromisoformat(state["started"]).timestamp()
                    elapsed = f" · {time.time() - t0:.0f}s"
                except ValueError:
                    pass
            publish_job("comfyui", prompt_id, "DONE", job_name=job_name,
                        started_at=state["started"], finished_at=_now_iso(),
                        result=f"{state['outputs']} output(s){elapsed}",
                        title=f"{job_name}: done", message="complete", priority="high")
            return True
        elif mtype == "execution_error":
            _publish_failed(prompt_id, job_name, state["started"],
                            f"{payload.get('node_type', '?')}: "
                            f"{payload.get('exception_message', 'execution error')}")
            return True
        elif mtype == "execution_interrupted":
            _publish_failed(prompt_id, job_name, state["started"], "execution interrupted")
            return True


async def cmd_submit(workflow_path, name):
    workflow = json.loads(Path(workflow_path).read_text(encoding="utf-8"))
    job_name = name or Path(workflow_path).stem
    async with aiohttp.ClientSession() as session:
        # Socket FIRST: a workflow that fails (or finishes) within milliseconds
        # broadcasts its terminal frame before a post-queue handshake would
        # complete — that race is exactly how terminal events get lost.
        async with session.ws_connect(f"ws://{COMFY_HOST}/ws?clientId={CLIENT_ID}") as ws:
            async with session.post(
                f"http://{COMFY_HOST}/prompt",
                json={"prompt": workflow, "client_id": CLIENT_ID},
            ) as resp:
                body = await resp.json()
            if resp.status != 200 or "prompt_id" not in body:
                print(f"ComfyUI rejected the workflow: HTTP {resp.status}: {json.dumps(body)[:300]}")
                sys.exit(2)
            prompt_id = body["prompt_id"]
            queue_no = body.get("number")
            pos = f" (queued #{queue_no})" if queue_no is not None else ""
            publish_job("comfyui", prompt_id, "RUNNING", job_name=job_name,
                        started_at=_now_iso(), title=f"{job_name}: queued",
                        message=f"workflow queued{pos}")
            await _drain_prompt(session, ws, prompt_id, job_name=job_name,
                                total_nodes=len(workflow), announce_start=False)
    print("Terminal state published — check the Job Inbox on your phone.")


async def cmd_watch():
    """Report every prompt queued on this ComfyUI as a PigeonHub job.

    Polls /queue + /history rather than the WebSocket: ComfyUI targets WS
    lifecycle frames at the submitting client_id only, so an observer socket
    never sees other clients' prompts. Polling is client-agnostic — workflows
    queued from the ComfyUI web UI, the API, or anywhere else all get reported.
    """
    print(f"Polling http://{COMFY_HOST}/queue — every queued prompt becomes a PigeonHub job. Ctrl+C to stop.")
    watch_start_ms = int(time.time() * 1000)
    seen_running = set()
    seen_terminal = set()
    async with aiohttp.ClientSession() as session:
        async def report_history_entry(pid, entry):
            """Publish a terminal state for a /history entry. True when terminal."""
            status = (entry or {}).get("status", {})
            if status.get("status_str") not in ("success", "error") and not status.get("completed"):
                return False
            seen_terminal.add(pid)
            name = f"prompt {pid[:8]}"
            out_count = _count_outputs((entry or {}).get("outputs", {}))
            if status.get("status_str") == "error":
                reason = "execution error"
                for m in status.get("messages", []):
                    if m and m[0] == "execution_error":
                        d = m[1] if len(m) > 1 else {}
                        reason = f"{d.get('node_type', '?')}: {d.get('exception_message', 'execution error')}"
                        break
                _publish_failed(pid, name, None, reason)
            else:
                publish_job("comfyui", pid, "DONE", job_name=name,
                            finished_at=_now_iso(),
                            result=f"{out_count} output(s)",
                            title=f"{name}: done", message="complete",
                            priority="high")
            return True

        while True:
            try:
                async with session.get(f"http://{COMFY_HOST}/queue") as resp:
                    queue = await resp.json()
            except Exception as e:
                print(f"[pigeonhub] ComfyUI unreachable ({e}); retrying…")
                await asyncio.sleep(HISTORY_POLL_SEC)
                continue
            active = []
            for key in ("queue_running", "queue_pending"):
                for item in queue.get(key, []) or []:
                    pid = item[1] if isinstance(item, (list, tuple)) and len(item) > 1 else None
                    if pid:
                        active.append(pid)
            for pid in active:
                if pid in seen_running or pid in seen_terminal:
                    continue
                seen_running.add(pid)
                name = f"prompt {pid[:8]}"
                publish_job("comfyui", pid, "RUNNING", job_name=name,
                            started_at=_now_iso(), title=f"{name}: running",
                            message="execution started" if key == "queue_running" else "queued")
            # Anything that left the queue has a terminal state in /history.
            finished = seen_running - set(active)
            for pid in list(finished):
                entry = None
                try:
                    async with session.get(f"http://{COMFY_HOST}/history/{pid}") as resp:
                        entry = (await resp.json()).get(pid)
                except Exception:
                    entry = None
                if await report_history_entry(pid, entry):
                    seen_running.discard(pid)
            # Fast prompts can finish between two polls — they never appear in
            # /queue. Catch them from recent /history (only entries that started
            # after the watcher came up, so restarts don't replay old jobs).
            try:
                async with session.get(f"http://{COMFY_HOST}/history?max_items=8") as resp:
                    recent = await resp.json()
            except Exception:
                recent = {}
            for pid, entry in recent.items():
                if pid in seen_terminal or pid in seen_running:
                    continue
                started_ms = None
                for m in (entry or {}).get("status", {}).get("messages", []):
                    if m and m[0] == "execution_start":
                        started_ms = (m[1] or {}).get("timestamp")
                        break
                if started_ms and started_ms >= watch_start_ms:
                    await report_history_entry(pid, entry)
            await asyncio.sleep(HISTORY_POLL_SEC)


def main():
    p = argparse.ArgumentParser(description="Real ComfyUI → PigeonHub bridge")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("submit", help="queue a workflow JSON and report its lifecycle")
    s.add_argument("workflow", help="path to the workflow (API-format JSON)")
    s.add_argument("--name", help="human job name (default: file stem)")
    sub.add_parser("watch", help="report every prompt queued on this ComfyUI as a job")
    args = p.parse_args()
    if args.cmd == "submit":
        asyncio.run(cmd_submit(args.workflow, args.name))
    else:
        asyncio.run(cmd_watch())


if __name__ == "__main__":
    main()
