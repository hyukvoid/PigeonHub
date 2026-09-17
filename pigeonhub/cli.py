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
from .agents import AGENTS, build_setup_plan, apply_setup, handle_agent_event, remove_setup, setup_summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="pigeonhub",
        description="Track a declared long-running command in the PigeonHub Job Inbox.",
    )
    parser.add_argument("--version", action="store_true", help="print the PigeonHub CLI version and exit")
    sub = parser.add_subparsers(dest="subcommand")

    version_parser = sub.add_parser("version", help="print the PigeonHub CLI version")

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

    setup_parser = sub.add_parser("setup", help="install or remove one PigeonHub agent integration")
    setup_parser.add_argument("agent", choices=AGENTS)
    setup_parser.add_argument("--remove", action="store_true", help="remove only PigeonHub-managed hooks")
    setup_parser.add_argument("--yes", action="store_true", help="confirm the printed change preview")
    setup_parser.add_argument("--json", action="store_true", help="print a machine-readable preview/result")

    event_parser = sub.add_parser("agent-event", help=argparse.SUPPRESS)
    event_parser.add_argument("agent", choices=AGENTS)
    event_parser.add_argument("--job-name")

    sub.add_parser("comfyui-demo", help=argparse.SUPPRESS)
    return parser


def _command_after_separator(values: list[str]) -> list[str]:
    return values[1:] if values and values[0] == "--" else values


def _print_first_run() -> None:
    """Friendly no-subcommand banner; raw help stays available via --help."""
    from .core import credentials_path

    print("PigeonHub")
    print("Long-running jobs, in your pocket.")
    print()
    if not credentials_path().exists():
        print("You're not connected yet.")
        print()
        print("Start with:")
        print("  pigeonhub login")
        print()
        print("Then run:")
        print("  pigeonhub run -- <your command>")
        return
    print("You're connected. Send a long-running job to your phone with:")
    print("  pigeonhub run --name \"My job\" -- <your command>")
    print()
    print("Handy commands:")
    print("  pigeonhub status      show connection state")
    print("  pigeonhub --help      all commands")


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if getattr(args, "version", False):
        from . import __version__

        print(f"PigeonHub CLI {__version__}")
        return 0
    if args.subcommand == "version":
        from . import __version__

        print(f"PigeonHub CLI {__version__}")
        return 0
    if args.subcommand is None:
        _print_first_run()
        return 0
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
        if args.subcommand == "agent-event":
            return handle_agent_event(args.agent, job_name=args.job_name)
        if args.subcommand == "setup":
            plan = build_setup_plan(args.agent, remove=args.remove)
            summary = setup_summary(plan)
            if not args.json:
                print(f"PigeonHub {args.agent} integration")
                print(f"Detected: {'yes' if plan.detection.detected else 'no'}" + (f" ({plan.detection.version})" if plan.detection.version else ""))
                print(f"Target: {plan.target}")
                for line in plan.change_lines:
                    print(f"- {line}")
            if plan.blocked_reason:
                if args.json:
                    print(json.dumps(summary, indent=2, ensure_ascii=False))
                else:
                    print(plan.blocked_reason, file=sys.stderr)
                return 2
            if not plan.changed:
                if args.json:
                    print(json.dumps(summary, indent=2, ensure_ascii=False))
                else:
                    print("Nothing to change; setup is already in the requested state.")
                return 0
            if args.json and not args.yes:
                print(json.dumps(summary, indent=2, ensure_ascii=False))
                return 0
            if not args.yes:
                try:
                    answer = input("Apply these changes? [Y/n] ").strip().lower()
                except EOFError:
                    answer = "n"
                if answer not in {"", "y", "yes"}:
                    print("Cancelled; no files changed.")
                    return 0
            backup = remove_setup(plan) if args.remove else apply_setup(plan)
            if args.json:
                summary["applied"] = True
                summary["backup"] = str(backup) if backup else None
                print(json.dumps(summary, indent=2, ensure_ascii=False))
            else:
                print(f"Applied. Backup: {backup}" if backup else "Applied. No backup was needed for a new file.")
            return 0
        if args.subcommand == "comfyui-demo":
            return comfyui_demo()
    except CliError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 1
