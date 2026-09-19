"""Safe, zero-command Codex Desktop completion integration.

The supported baseline is Codex's user-level ``notify`` setting.  This module
deliberately does not install hooks, inspect projects, read history, or parse
the prompt/assistant fields that Codex includes in the notify payload.

The TOML mutation is intentionally text based: it changes only a top-level
``notify`` assignment (or inserts one) and leaves comments, unknown keys,
profiles, MCP settings, and permissions byte-for-byte intact apart from the
small managed addition.  Existing non-PigeonHub notify commands are never
overwritten because a single notify argv cannot be safely chained without
knowing the other program's semantics.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence


INTERNAL_NOTIFY_COMMAND = "internal-codex-notify"
NOTIFY_EVENT = "agent-turn-complete"
MAX_NOTIFY_PAYLOAD_BYTES = 128 * 1024
MANAGED_COMMENT = "# PigeonHub: Codex completion notifications"
_SAFE_OPAQUE_ID = re.compile(r"[^A-Za-z0-9_.:-]+")
_NOTIFY_ASSIGNMENT = re.compile(r"^(?P<indent>\s*)notify\s*=")
_TABLE_LINE = re.compile(r"^\s*\[")

NOT_INSTALLED = "NOT_INSTALLED"
DETECTED = "DETECTED"
CONNECTED_BASIC = "CONNECTED_BASIC"
CONNECTED_FULL = "CONNECTED_FULL"
NEEDS_ATTENTION = "NEEDS_ATTENTION"


class CodexIntegrationError(RuntimeError):
    """A safe local Codex integration could not be planned or applied."""


@dataclass(frozen=True)
class CodexDetection:
    executable: str | None
    version: str | None
    codex_home: Path
    config_path: Path
    config_present: bool

    @property
    def detected(self) -> bool:
        return bool(self.executable or self.codex_home.exists())


@dataclass(frozen=True)
class NotifyAssignment:
    start: int
    end: int
    value: list[str]
    marker_start: int | None = None


@dataclass(frozen=True)
class CodexNotifyPlan:
    detection: CodexDetection
    target: Path
    desired_argv: tuple[str, ...]
    changed: bool
    status: str
    reason: str | None
    updated_text: str | None


@dataclass(frozen=True)
class CodexConnectResult:
    status: str
    detail: str
    version: str | None = None


def _home(home: Path | None = None) -> Path:
    if home is not None:
        return Path(home).expanduser()
    override = os.environ.get("PIGEONHUB_AGENT_HOME")
    return Path(override).expanduser() if override else Path.home()


def _safe_version(executable: str | None) -> str | None:
    if not executable:
        return None
    try:
        completed = subprocess.run(
            [executable, "--version"],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    raw = (completed.stdout or completed.stderr or "").replace("\r", " ").replace("\n", " ")
    match = re.search(r"\bcodex(?:-cli)?\s+([0-9][A-Za-z0-9_.-]*)\b", raw, re.IGNORECASE)
    return match.group(1) if match else None


def detect_codex(*, home: Path | None = None) -> CodexDetection:
    root = _home(home)
    codex_home = root / ".codex"
    config_path = codex_home / "config.toml"
    executable = shutil.which("codex")
    return CodexDetection(
        executable=executable,
        version=_safe_version(executable),
        codex_home=codex_home,
        config_path=config_path,
        config_present=config_path.exists(),
    )


def _pigeonhub_notify_argv() -> tuple[str, ...]:
    """Return an argv that works from source and from a PyInstaller exe."""
    override = os.environ.get("PIGEONHUB_NOTIFY_EXECUTABLE")
    if override:
        return (override, INTERNAL_NOTIFY_COMMAND)
    if getattr(sys, "frozen", False):
        return (str(Path(sys.executable).resolve()), INTERNAL_NOTIFY_COMMAND)
    installed = shutil.which("pigeonhub")
    if installed:
        return (str(Path(installed).resolve()), INTERNAL_NOTIFY_COMMAND)
    return (sys.executable, "-m", "pigeonhub", INTERNAL_NOTIFY_COMMAND)


def _toml_parser():
    try:
        import tomllib  # type: ignore

        return tomllib.loads
    except ImportError:
        try:
            import tomli  # type: ignore

            return tomli.loads
        except ImportError:
            return None


def _parse_toml(text: str) -> dict[str, Any]:
    parser = _toml_parser()
    if parser is None:
        raise CodexIntegrationError("TOML parser unavailable; refusing to edit Codex config")
    try:
        value = parser(text)
    except Exception as exc:
        raise CodexIntegrationError("Codex config is malformed; refusing to edit it") from exc
    if not isinstance(value, dict):
        raise CodexIntegrationError("Codex config did not parse as a table")
    return value


def _bracket_delta(line: str) -> int:
    """Count array brackets outside TOML strings and comments."""
    delta = 0
    quote: str | None = None
    escaped = False
    for char in line:
        if quote:
            if quote == '"' and escaped:
                escaped = False
            elif quote == '"' and char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char == "#":
            break
        if char in {'"', "'"}:
            quote = char
        elif char == "[":
            delta += 1
        elif char == "]":
            delta -= 1
    return delta


def _find_notify_assignment(text: str, parsed: dict[str, Any]) -> NotifyAssignment | None:
    if "notify" not in parsed:
        return None
    value = parsed.get("notify")
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise CodexIntegrationError("Codex notify must be an array of strings; refusing to edit it")

    offset = 0
    in_table = False
    lines = text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        stripped = line.lstrip()
        if not stripped or stripped.startswith("#"):
            offset += len(line)
            continue
        if _TABLE_LINE.match(line):
            in_table = True
            offset += len(line)
            continue
        match = _NOTIFY_ASSIGNMENT.match(line)
        if in_table or not match:
            offset += len(line)
            continue

        start = offset
        balance = _bracket_delta(line[match.end():])
        end = offset + len(line)
        cursor = index + 1
        while balance > 0 and cursor < len(lines):
            balance += _bracket_delta(lines[cursor])
            end += len(lines[cursor])
            cursor += 1
        if balance != 0:
            raise CodexIntegrationError("Codex notify array is incomplete; refusing to edit it")

        marker_start: int | None = None
        prefix = text[:start]
        marker_match = re.search(r"(?:^|\r?\n)[ \t]*" + re.escape(MANAGED_COMMENT) + r"[ \t]*\r?\n?$", prefix)
        if marker_match:
            marker_start = marker_match.start()
            if prefix[marker_start:marker_match.end()].startswith("\r\n"):
                marker_start += 2
        return NotifyAssignment(start, end, list(value), marker_start)
    raise CodexIntegrationError("Codex notify exists but its top-level assignment was not found")


def _is_managed_notify(value: Sequence[str]) -> bool:
    if not value or value[-1] != INTERNAL_NOTIFY_COMMAND:
        return False
    if len(value) == 2:
        return Path(value[0]).stem.lower() == "pigeonhub"
    return len(value) == 4 and list(value[1:3]) == ["-m", "pigeonhub"]


def _read_text(path: Path) -> str:
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            return handle.read()
    except OSError as exc:
        raise CodexIntegrationError("Codex config could not be read; refusing to edit it") from exc


def _assignment_text(argv: Sequence[str], newline: str = "\n") -> str:
    encoded = ", ".join(json.dumps(str(item), ensure_ascii=False) for item in argv)
    return f"{MANAGED_COMMENT}{newline}notify = [{encoded}]{newline}"


def _insert_assignment(text: str, argv: Sequence[str]) -> str:
    newline = "\r\n" if "\r\n" in text and "\n" in text else "\n"
    lines = text.splitlines(keepends=True)
    offset = 0
    for line in lines:
        if _TABLE_LINE.match(line):
            prefix = text[:offset]
            if prefix and not prefix.endswith(("\n", "\r")):
                prefix += newline
            return prefix + _assignment_text(argv, newline) + text[offset:]
        offset += len(line)
    prefix = text
    if prefix and not prefix.endswith(("\n", "\r")):
        prefix += newline
    return prefix + _assignment_text(argv, newline)


def _remove_assignment(text: str, assignment: NotifyAssignment) -> str:
    start = assignment.marker_start if assignment.marker_start is not None else assignment.start
    return text[:start] + text[assignment.end:]


def build_codex_notify_plan(
    *,
    home: Path | None = None,
    remove: bool = False,
) -> CodexNotifyPlan:
    detection = detect_codex(home=home)
    target = detection.config_path
    desired_argv = _pigeonhub_notify_argv()

    if not detection.detected:
        return CodexNotifyPlan(detection, target, desired_argv, False, NOT_INSTALLED, "Codex was not detected", None)
    if not detection.executable:
        return CodexNotifyPlan(
            detection,
            target,
            desired_argv,
            False,
            DETECTED,
            "Codex home was detected but no executable was found on PATH",
            None,
        )

    if not target.exists():
        if remove:
            return CodexNotifyPlan(detection, target, desired_argv, False, DETECTED, "No PigeonHub notify was installed", None)
        return CodexNotifyPlan(
            detection,
            target,
            desired_argv,
            True,
            CONNECTED_BASIC,
            "Codex notify will be installed",
            _insert_assignment("", desired_argv),
        )

    try:
        text = _read_text(target)
        parsed = _parse_toml(text)
        assignment = _find_notify_assignment(text, parsed)
    except CodexIntegrationError as exc:
        return CodexNotifyPlan(detection, target, desired_argv, False, NEEDS_ATTENTION, str(exc), None)

    if assignment is None:
        if remove:
            return CodexNotifyPlan(detection, target, desired_argv, False, DETECTED, "No PigeonHub notify was installed", None)
        return CodexNotifyPlan(
            detection,
            target,
            desired_argv,
            True,
            CONNECTED_BASIC,
            "Codex notify will be installed",
            _insert_assignment(text, desired_argv),
        )

    if not _is_managed_notify(assignment.value):
        return CodexNotifyPlan(
            detection,
            target,
            desired_argv,
            False,
            NEEDS_ATTENTION,
            "An existing Codex notify command was preserved; safe chaining is unavailable",
            None,
        )

    if remove:
        return CodexNotifyPlan(
            detection,
            target,
            desired_argv,
            True,
            DETECTED,
            "PigeonHub notify will be removed; unrelated Codex config is preserved",
            _remove_assignment(text, assignment),
        )
    return CodexNotifyPlan(detection, target, desired_argv, False, CONNECTED_BASIC, "PigeonHub notify is already configured", None)


def _backup(path: Path) -> Path | None:
    if not path.exists():
        return None
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%SZ")
    destination = path.with_name(f"{path.name}.pigeonhub.bak.{stamp}")
    try:
        shutil.copy2(path, destination)
    except OSError as exc:
        raise CodexIntegrationError("Could not create a Codex config backup; refusing to edit it") from exc
    return destination


def _atomic_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent), text=True)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except Exception as exc:
        try:
            os.unlink(temporary)
        except OSError:
            pass
        raise CodexIntegrationError("Could not atomically write Codex config; original content was kept") from exc


def apply_codex_notify_plan(plan: CodexNotifyPlan) -> Path | None:
    if plan.status == NEEDS_ATTENTION:
        raise CodexIntegrationError(plan.reason or "Codex integration needs attention")
    if not plan.changed or plan.updated_text is None:
        return None
    backup = _backup(plan.target)
    try:
        _atomic_write_text(plan.target, plan.updated_text)
        if plan.status == CONNECTED_BASIC:
            after = build_codex_notify_plan(home=plan.detection.codex_home.parent, remove=False)
            if after.status != CONNECTED_BASIC:
                raise CodexIntegrationError("Codex notify verification failed; restoring the backup")
        else:
            after = build_codex_notify_plan(home=plan.detection.codex_home.parent, remove=True)
            if after.changed or after.status == NEEDS_ATTENTION:
                raise CodexIntegrationError("Codex notify removal verification failed; restoring the backup")
    except Exception:
        try:
            if backup is not None:
                shutil.copy2(backup, plan.target)
            else:
                plan.target.unlink(missing_ok=True)
        except OSError:
            pass
        raise
    return backup


def connect_codex(*, home: Path | None = None) -> CodexConnectResult:
    plan = build_codex_notify_plan(home=home)
    if plan.status == NEEDS_ATTENTION:
        return CodexConnectResult(NEEDS_ATTENTION, plan.reason or "Codex integration needs attention", plan.detection.version)
    if plan.status == NOT_INSTALLED:
        return CodexConnectResult(NOT_INSTALLED, "Codex was not detected", plan.detection.version)
    if plan.status == DETECTED and not plan.changed:
        return CodexConnectResult(DETECTED, plan.reason or "Codex was detected but is not ready for auto-connect", plan.detection.version)
    try:
        backup = apply_codex_notify_plan(plan)
    except CodexIntegrationError as exc:
        return CodexConnectResult(NEEDS_ATTENTION, str(exc), plan.detection.version)
    if plan.changed:
        detail = "Codex notify connected" if backup is None else "Codex notify connected with backup"
    else:
        detail = "Codex notify already connected"
    return CodexConnectResult(CONNECTED_BASIC, detail, plan.detection.version)


def remove_codex(*, home: Path | None = None) -> CodexConnectResult:
    plan = build_codex_notify_plan(home=home, remove=True)
    if plan.status == NEEDS_ATTENTION:
        return CodexConnectResult(NEEDS_ATTENTION, plan.reason or "Codex integration needs attention", plan.detection.version)
    if not plan.changed:
        return CodexConnectResult(plan.status, plan.reason or "No PigeonHub notify was installed", plan.detection.version)
    try:
        apply_codex_notify_plan(plan)
    except CodexIntegrationError as exc:
        return CodexConnectResult(NEEDS_ATTENTION, str(exc), plan.detection.version)
    return CodexConnectResult(DETECTED, "PigeonHub notify removed", plan.detection.version)


def discover_local_integrations(*, home: Path | None = None) -> dict[str, CodexConnectResult]:
    """Discover only local executable/config presence; never scan projects or history."""
    detection = detect_codex(home=home)
    if not detection.detected:
        result = CodexConnectResult(NOT_INSTALLED, "Codex was not detected", detection.version)
    elif detection.executable:
        result = CodexConnectResult(DETECTED, "Codex detected", detection.version)
    else:
        result = CodexConnectResult(DETECTED, "Codex configuration directory detected", detection.version)
    return {"codex": result}


def _safe_opaque(value: Any, *, limit: int = 96) -> str | None:
    if not isinstance(value, str):
        return None
    cleaned = _SAFE_OPAQUE_ID.sub("-", value.strip())[:limit].strip("-.")
    return cleaned or None


def _notify_state_path() -> Path:
    override = os.environ.get("PIGEONHUB_CODEX_NOTIFY_STATE")
    if override:
        return Path(override).expanduser()
    credentials_override = os.environ.get("PIGEONHUB_CREDENTIALS")
    base = Path(credentials_override).expanduser().parent if credentials_override else Path.home() / ".pigeonhub"
    return base / "codex-notify-state.json"


def _load_notify_state(path: Path) -> dict[str, str]:
    try:
        value = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    except (OSError, json.JSONDecodeError):
        return {}
    return value if isinstance(value, dict) and all(isinstance(k, str) and isinstance(v, str) for k, v in value.items()) else {}


def _save_notify_state(path: Path, value: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent), text=True)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(value, handle, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except Exception:
        try:
            os.unlink(temporary)
        except OSError:
            pass


def handle_codex_notify(payload: str | None = None) -> int:
    """Process only a Codex notify callback; never expose a generic runner."""
    if os.environ.get("PIGEONHUB_CODEX_BRIDGED") == "1":
        return 0
    try:
        if payload is None:
            raw_bytes = sys.stdin.buffer.read(MAX_NOTIFY_PAYLOAD_BYTES + 1)
            if len(raw_bytes) > MAX_NOTIFY_PAYLOAD_BYTES:
                return 0
            payload = raw_bytes.decode("utf-8", "strict")
        if len(payload.encode("utf-8", "replace")) > MAX_NOTIFY_PAYLOAD_BYTES:
            return 0
        value = json.loads(payload)
        if not isinstance(value, dict) or value.get("type") != NOTIFY_EVENT:
            return 0
        thread_id = _safe_opaque(value.get("thread-id") or value.get("thread_id"))
        turn_id = _safe_opaque(value.get("turn-id") or value.get("turn_id"))
        opaque_id = thread_id or turn_id
        if not opaque_id:
            return 0
        event_key = f"{thread_id or 'thread'}:{turn_id or 'turn'}"
        state_path = _notify_state_path()
        state = _load_notify_state(state_path)
        if event_key in state:
            return 0
        job_suffix = "-".join(part for part in (thread_id, turn_id) if part)[:120]
        job_id = f"agent-codex-{job_suffix}"
        finished_at = datetime.now(timezone.utc).isoformat()
        # Import lazily to keep config discovery and the packaged callback free
        # of an import cycle through pigeonhub.core.
        from .core import publish_job_detailed

        result = publish_job_detailed(
            "codex",
            job_id,
            "DONE",
            job_name="Codex session",
            finished_at=finished_at,
            result="Codex turn completed",
            title="Codex: done",
            message="Codex turn completed",
        )
        if not result.ok:
            return 0
        state[event_key] = finished_at
        if len(state) > 200:
            for old_key in list(state)[: len(state) - 200]:
                state.pop(old_key, None)
        _save_notify_state(state_path, state)
    except Exception:
        # notify is best-effort and must never fail the Codex turn.  Do not print
        # exception text because it can contain a path or vendor payload detail.
        return 0
    return 0
