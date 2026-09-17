"""Command-line entry point for PigeonHub."""

from __future__ import annotations

import argparse
import json
import sys
from typing import Sequence

from .core import (
    CliError,
    comfyui_demo,
    login,
    logout,
    publish_message,
    report_needs_action,
    report_progress,
    run_job,
    status,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="pigeonhub",
        description="Track a declared long-running command in the PigeonHub Job Inbox.",
    )
    sub = parser.add_subparsers(dest="subcommand", required=True)

    login_parser = sub.add_parser("login", help="connect this PC with a one-time Android code")
    login_parser.add_argument("--code", help="one-time pairing code")
    login_parser.add_argument("--qr-image", help="screenshot/photo of the Android pairing QR")
    login_parser.add_argument("--worker-url", help="PigeonHub worker origin")

    pair_parser = sub.add_parser("pair", help=argparse.SUPPRESS)
    pair_parser.add_argument("--code", help=argparse.SUPPRESS)
    pair_parser.add_argument("--qr-image", help=argparse.SUPPRESS)
    pair_parser.add_argument("--worker-url", help=argparse.SUPPRESS)

    sub.add_parser("logout", help="remove this PC's locally stored connector credential")
    status_parser = sub.add_parser("status", help="show local login and worker reachability")
    status_parser.add_argument("--json", action="store_true", help="print machine-readable status")

    notify_parser = sub.add_parser("notify", help="send one ordinary notification")
    notify_parser.add_argument("title")
    notify_parser.add_argument("message", nargs="?", help="message; omit to read one line from stdin")
    notify_parser.add_argument("--priority", choices=("normal", "high"), default="normal")

    progress_parser = sub.add_parser("progress", help="report progress from inside a running job")
    progress_parser.add_argument("current", type=int)
    progress_parser.add_argument("total", type=int)
    progress_parser.add_argument("--job-id")

    for name in ("needs-action", "attention"):
        attention_parser = sub.add_parser(name, help="tell the user that a job is waiting" if name == "needs-action" else argparse.SUPPRESS)
        attention_parser.add_argument("reason")
        attention_parser.add_argument("--job-id")

    run_parser = sub.add_parser("run", help="declare and run a local command")
    run_parser.add_argument("--name", help="human-readable job name")
    run_parser.add_argument("command", nargs=argparse.REMAINDER, help="command to execute; use -- before command flags")

    sub.add_parser("comfyui-demo", help=argparse.SUPPRESS)
    return parser


def _command_after_separator(values: list[str]) -> list[str]:
    return values[1:] if values and values[0] == "--" else values


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.subcommand in ("login", "pair"):
            login(code=args.code, qr_image=args.qr_image, worker_url=args.worker_url, legacy=args.subcommand == "pair")
            return 0
        if args.subcommand == "logout":
            logout()
            return 0
        if args.subcommand == "status":
            value = status(quiet=args.json)
            if args.json:
                print(json.dumps(value, indent=2))
            return 0 if value.get("logged_in") and value.get("worker_reachable") else 1
        if args.subcommand == "notify":
            message = args.message if args.message is not None else sys.stdin.readline().rstrip("\r\n")
            result = publish_message(args.title, message, priority=args.priority)
            return 0 if result.ok else 1
        if args.subcommand == "progress":
            result = report_progress(args.current, args.total, job_id=args.job_id)
            return 0 if result.ok else 1
        if args.subcommand in ("needs-action", "attention"):
            result = report_needs_action(args.reason, job_id=args.job_id)
            return 0 if result.ok else 1
        if args.subcommand == "run":
            return run_job(_command_after_separator(args.command), name=args.name)
        if args.subcommand == "comfyui-demo":
            return comfyui_demo()
    except CliError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 1
