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
from .codex_integration import handle_codex_notify


AUTO_CONNECT_DISCLOSURE = (
    "연결된 개발 도구도 자동으로 설정 / PigeonHub가 설치된 Codex, ZCode 등의 "
    "작업 완료 알림을 연결할 수 있습니다."
)


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
    login_parser.add_argument("--no-auto-connect", action="store_true", help="do not connect supported local tools after pairing")

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

    recipe_parser = sub.add_parser("recipe", help="save and run reusable local commands")
    recipe_sub = recipe_parser.add_subparsers(dest="recipe_command")
    recipe_add = recipe_sub.add_parser("add", help="create a recipe (interactive when options are omitted)")
    recipe_add.add_argument("--name", help="display name; the id is derived from it")
    recipe_add.add_argument("--prompt", help="default prompt to store locally (use {prompt} in the command)")
    recipe_add.add_argument("--cwd", help="working directory to run the command in")
    recipe_add.add_argument("--replace", action="store_true", help="overwrite an existing recipe of the same id")
    recipe_add.add_argument("command", nargs=argparse.REMAINDER, help="command template; use -- before it")
    recipe_run = recipe_sub.add_parser("run", help="run a saved recipe as a PigeonHub job")
    recipe_run.add_argument("recipe")
    recipe_run.add_argument("--prompt", help="prompt for this run (overrides the stored default)")
    recipe_run.add_argument("--prompt-file", help="read the prompt from a file instead of the shell history")
    recipe_sub.add_parser("list", help="list saved recipes")
    recipe_show = recipe_sub.add_parser("show", help="show one saved recipe")
    recipe_show.add_argument("recipe")
    recipe_remove = recipe_sub.add_parser("remove", help="remove a saved recipe")
    recipe_remove.add_argument("recipe")
    recipe_remove.add_argument("--yes", action="store_true", help="skip the confirmation")

    setup_parser = sub.add_parser("setup", help="install or remove one PigeonHub agent integration")
    setup_parser.add_argument("agent", choices=AGENTS)
    setup_parser.add_argument("--remove", action="store_true", help="remove only PigeonHub-managed hooks")
    setup_parser.add_argument("--yes", action="store_true", help="confirm the printed change preview")
    setup_parser.add_argument("--json", action="store_true", help="print a machine-readable preview/result")

    sub.add_parser("onboard", help="open the local Setup Center (connect phone and tools in the browser)")

    event_parser = sub.add_parser("agent-event", help=argparse.SUPPRESS)
    event_parser.add_argument("agent", choices=AGENTS)
    event_parser.add_argument("--job-name")

    notify_callback_parser = sub.add_parser("internal-codex-notify", help=argparse.SUPPRESS)
    notify_callback_parser.add_argument("payload", nargs="?", help=argparse.SUPPRESS)

    sub.add_parser("comfyui-demo", help=argparse.SUPPRESS)
    return parser


def _command_after_separator(values: list[str]) -> list[str]:
    return values[1:] if values and values[0] == "--" else values


def _ask(label: str, current: str | None, required: bool = False) -> str | None:
    """Prompt once; Enter keeps [current]; EOF (Ctrl+Z/Ctrl+D) cancels safely."""
    try:
        if current:
            answer = input(f"{label} [{current}]: ").strip()
            return answer or current
        answer = input(f"{label}: ").strip()
        return answer or None
    except EOFError:
        if required and not current:
            print("Cancelled; that field is required.")
            raise CliError("Recipe cancelled") from None
        return current


def _recipe_add(args: argparse.Namespace) -> int:
    import shlex

    from . import recipes as recipe_store

    name = (args.name or "").strip()
    argv = _command_after_separator(args.command)
    print("Create a PigeonHub Recipe")
    interactive = sys.stdin.isatty()
    if interactive:
        if not name:
            name = _ask("Name", None, required=True) or ""
        if not argv:
            line = _ask("Command (mark the prompt with {prompt}, if any)", None, required=True) or ""
            try:
                argv = shlex.split(line, posix=True)
            except ValueError as exc:
                raise CliError(f"Could not parse the command: {exc}") from exc
    if not name or not argv:
        raise CliError("recipe add needs --name and a command when not interactive. Example: pigeonhub recipe add --name \"Nightly crawler\" -- python crawler.py")
    if not any("{prompt}" in token for token in argv):
        if args.prompt:
            print("Note: the command has no {prompt}; the default prompt would never be used.", file=sys.stderr)
            if not interactive:
                args.prompt = None
    default_prompt = args.prompt
    if interactive and default_prompt is None and any("{prompt}" in token for token in argv):
        default_prompt = _ask("Default prompt (optional, Enter to skip)", None)
    cwd = args.cwd
    if interactive and cwd is None:
        cwd = _ask("Working directory (optional, Enter for the current directory)", None)

    warnings = recipe_store.secret_warnings(argv, default_prompt)
    if warnings and interactive:
        print()
        print("Warning: this looks like it may contain a credential:")
        for hint in warnings:
            print(f"  - matches {hint}")
        print(f"Recipes are stored in plain text at {recipe_store.recipes_path()}.")
        print("Prefer environment variables or the tool's own authentication.")
        answer = input("Store anyway? [y/N] ").strip().lower()
        if answer not in {"y", "yes"}:
            print("Cancelled; nothing was stored.")
            return 1
    elif warnings:
        print("Warning: the command may contain a credential; recipes are stored in plain text.", file=sys.stderr)

    recipe = recipe_store.save_recipe(
        name=name,
        argv=argv,
        default_prompt=default_prompt,
        cwd=cwd,
        replace=args.replace,
    )
    print()
    print(f"Recipe '{recipe.id}' created.")
    print()
    print("Run it with:")
    print(f"  pigeonhub recipe run {recipe.id}")
    return 0


def _recipe_command(args: argparse.Namespace) -> int:
    from . import recipes as recipe_store

    if args.recipe_command == "add":
        return _recipe_add(args)
    if args.recipe_command == "list":
        recipes = recipe_store.load_recipes()
        if not recipes:
            print("No recipes yet. Create one with:")
            print("  pigeonhub recipe add")
            return 0
        print("Recipes")
        print()
        width = max(len(r.id) for r in recipes.values())
        for recipe in sorted(recipes.values(), key=lambda r: r.id):
            print(f"  {recipe.id:<{width}}   {recipe.name}")
        return 0
    if args.recipe_command == "show":
        recipe = recipe_store.get_recipe(args.recipe)
        print(recipe.id)
        print(f"  Name: {recipe.name}")
        print(f"  Command: {recipe_store.format_command(recipe.argv)}")
        print(f"  Working directory: {recipe.cwd or '(the directory you run it from)'}")
        print(f"  Uses prompt: {'yes' if recipe.uses_prompt else 'no'}")
        print(f"  Default prompt: {'configured (stored locally, not shown)' if recipe.default_prompt else 'not configured'}")
        return 0
    if args.recipe_command == "run":
        return recipe_store.run_recipe(args.recipe, prompt=args.prompt, prompt_file=args.prompt_file)
    if args.recipe_command == "remove":
        recipe = recipe_store.get_recipe(args.recipe)
        if not args.yes and sys.stdin.isatty():
            answer = input(f"Remove '{recipe.name}' ({recipe.id})? [y/N] ").strip().lower()
            if answer not in {"y", "yes"}:
                print("Cancelled; the recipe was kept.")
                return 0
        removed = recipe_store.delete_recipe(args.recipe)
        print(f"Removed recipe '{removed.id}'. Only the recipe was deleted; agent configs, credentials, and job history are untouched.")
        return 0
    raise CliError("Unknown recipe command")


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
    print("Run something often? Save it once:")
    print("  pigeonhub recipe add")
    print()
    print("Handy commands:")
    print("  pigeonhub status      show connection state")
    print("  pigeonhub --help      all commands")


def _pairing_auto_connect_consent(disabled: bool) -> bool:
    """Show the one-time pairing disclosure before enabling local setup."""
    if disabled:
        return False
    print(AUTO_CONNECT_DISCLOSURE)
    if not sys.stdin.isatty():
        return True
    try:
        answer = input("지원되는 도구 자동 연결 [Y/n]: ").strip().lower()
    except EOFError:
        return False
    return answer not in {"n", "no"}


def _run_onboard() -> int:
    """Ephemeral Setup Center: loopback server + browser; dies with the session."""
    import os
    import webbrowser

    from .onboard import SESSION_TTL_SECONDS, serve

    if os.name == "nt":
        try:
            import ctypes

            ctypes.windll.user32.ShowWindow(
                ctypes.windll.kernel32.GetConsoleWindow(), 6  # SW_MINIMIZE
            )
        except Exception:
            pass
    center, url = serve()
    try:
        webbrowser.open(url)
    except Exception:
        pass
    print("PigeonHub Setup Center is open in your browser.")
    print("Keep this window open until you finish; closing it (or finishing in the")
    print(f"browser) ends setup. The session expires after {SESSION_TTL_SECONDS // 60} minutes.")
    print()
    print("Advanced CLI remains available: pigeonhub login / pigeonhub setup <agent>")
    try:
        center.server.serve_forever()  # type: ignore[union-attr]
    except KeyboardInterrupt:
        pass
    finally:
        center.state.stop.set()
        try:
            center.server.server_close()  # type: ignore[union-attr]
        except Exception:
            pass
    print("Setup Center closed.")
    return 0


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
            auto_connect = False if args.subcommand == "pair" else _pairing_auto_connect_consent(args.no_auto_connect)
            login(
                code=args.code,
                qr_image=args.qr_image,
                worker_url=args.worker_url,
                legacy=args.subcommand == "pair",
                auto_connect=auto_connect,
            )
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
        if args.subcommand == "recipe":
            return _recipe_command(args)
        if args.subcommand == "agent-event":
            return handle_agent_event(args.agent, job_name=args.job_name)
        if args.subcommand == "internal-codex-notify":
            return handle_codex_notify(args.payload)
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
        if args.subcommand == "onboard":
            return _run_onboard()
        if args.subcommand == "comfyui-demo":
            return comfyui_demo()
    except CliError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 1
