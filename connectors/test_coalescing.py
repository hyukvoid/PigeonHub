#!/usr/bin/env python3
"""MVP-011 coalescing E2E driver. Publishes scenario events; verification of
D1 rows / FCM deliveries happens from the outside (wrangler + adb)."""
import json
import sys
import time
import urllib.request
from pathlib import Path

CREDS = json.loads((Path.home() / ".pigeonhub" / "credentials.json").read_text(encoding="utf-8"))


def publish(job, title="coalesce test", message="m", priority="normal"):
    body = {"title": title[:500], "message": message, "priority": priority, "job": job}
    req = urllib.request.Request(CREDS["endpoint"], data=json.dumps(body).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "PigeonHubConnector/1.0")
    req.add_header("Authorization", "Bearer " + CREDS["write_token"])
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"ok": False, "http": e.code}


def job(source, job_id, state, cur=None, total=None, msg=None):
    j = {"source": source, "job_id": job_id, "state": state}
    if cur is not None:
        j["progress_current"] = cur
    if total is not None:
        j["progress_total"] = total
    return j


scenario = sys.argv[1] if len(sys.argv) > 1 else "all"

if scenario in ("all", "burst"):
    # S1: 100 progress in ~10s, 0..20% sweep in 0.2pp steps, fixed text.
    # Policy: no rule fires within the window -> ZERO progress emits.
    # Bounded outcome: RUNNING + DONE rows only.
    jid = "co-burst"
    print("RUNNING coalesced:", publish(job("cli", jid, "RUNNING"), message="start").get("coalesced", False))
    for i in range(1, 101):
        r = publish(job("cli", jid, "PROGRESS", cur=i, total=500), message="stepping")
        assert r.get("stored") or r.get("coalesced"), r
    time.sleep(1)
    print("DONE coalesced:", publish(job("cli", jid, "DONE", cur=100, total=500), message="finished",
                                     priority="high").get("coalesced", False))

if scenario in ("all", "tiny"):
    # S2: 1..6 of 1000 within 10s (0.1pp steps, same text) -> all coalesced.
    jid = "co-tiny"
    print("RUNNING coalesced:", publish(job("cli", jid, "RUNNING"), message="start").get("coalesced", False))
    for i in range(1, 7):
        r = publish(job("cli", jid, "PROGRESS", cur=i, total=1000), message="ticking")
        print(f"  {i}: coalesced={r.get('coalesced', False)}")
    print("DONE coalesced:", publish(job("cli", jid, "DONE"), message="done", priority="high").get("coalesced", False))

if scenario in ("all", "interleave"):
    # S3: two jobs interleaved; first progress of each must emit independently.
    for k in range(1, 4):
        for jid in ("co-A", "co-B"):
            r = publish(job("cli", jid, "PROGRESS", cur=k, total=100), message=f"job{k}")
            print(f"  {jid} {k}: coalesced={r.get('coalesced', False)}")

if scenario in ("all", "terminal"):
    # S4: PROGRESS then DONE immediately; DONE must never be coalesced.
    jid = "co-term2"
    print("RUNNING coalesced:", publish(job("cli", jid, "RUNNING"), message="start").get("coalesced", False))
    print("PROGRESS coalesced:", publish(job("cli", jid, "PROGRESS", cur=9, total=10), message="almost").get("coalesced", False))
    r = publish(job("cli", jid, "DONE"), message="done", priority="high")
    print("DONE:", {"coalesced": r.get("coalesced"), "stored": r.get("stored")})
    # S5: late stale progress after terminal must be dropped.
    time.sleep(2)
    r = publish(job("cli", jid, "PROGRESS", cur=5, total=10), message="late stale")
    print("LATE PROGRESS:", {"coalesced": r.get("coalesced"), "reason": r.get("reason")})
    # S6: FAILED and NEEDS_ACTION are immediate.
    print("FAILED coalesced:", publish(job("cli", "co-fail", "FAILED"), message="boom",
                                       priority="high").get("coalesced", False))
    print("ATTENTION coalesced:", publish(job("cli", "co-att", "NEEDS_ACTION"), message="approve?",
                                          priority="high").get("coalesced", False))

if scenario == "rules":
    # 10s-interval emit then 5pp emit then coalesce.
    jid = "co-rules"
    time.sleep(11)
    print("A1 coalesced:", publish(job("cli", jid, "PROGRESS", cur=50, total=100), message="half").get("coalesced", False))
    print("A2 coalesced:", publish(job("cli", jid, "PROGRESS", cur=56, total=100), message="half").get("coalesced", False))
    print("A3 coalesced:", publish(job("cli", jid, "PROGRESS", cur=57, total=100), message="half").get("coalesced", False))
