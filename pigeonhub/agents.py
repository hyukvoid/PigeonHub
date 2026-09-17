"""AI coding-agent adapters and safe setup helpers for MVP-017.

The adapter boundary is intentionally small: vendor hook input is reduced to
an allowlisted lifecycle event before anything is sent to PigeonHub. Prompts,
transcripts, source files, tool inputs, environment variables, and raw logs
never cross this boundary.
"""

from __future__ import annotations

import copy
import json
import os
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping
from urllib.parse import urlsplit, urlunsplit

from .core import CliError, PublishResult, credentials_path, publish_job_detailed


AGENTS = ("codex", "claude", "grok", "zcode")
STATES = ("RUNNING", "PROGRESS", "DONE", "FAILED", "NEEDS_ACTION")
TERMINAL_STATES = {"DONE", "FAILED"}
_SAFE_ID = re.compile(r"[^A-Za-z0-9_.-]+")
_MANAGED_COMMAND = "pigeonhub agent-event"


class AgentSetupError(CliError):
    """An agent setup is unsafe or cannot be verified."""


@dataclass(frozen=True)
class NormalizedJobEvent:
    source: str
    job_id: str
    job_name: str
    state: str
    started_at: str
    finished_at: str | None = None
    attention_reason: str | None = None
    result_summary: str | None = None
    deep_link: str | None = None
    progress_current: int | None = None
    progress_total: int | None = None


@dataclass(frozen=True)
class AgentDetection:
    agent: str
    executable: str | None
    version: str | None
    config_path: Path
    config_present: bool

    @property
    def detected(self) -> bool:
        return bool(self.executable or self.config_present)


@dataclass(frozen=True)
class SetupPlan:
    agent: str
    detection: AgentDetection
    target: Path
    exists: bool
    changed: bool
    desired: Mapping[str, Any] | None
    change_lines: tuple[str, ...]
    blocked_reason: str | None = None
    previous_enabled: bool | None = None


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _safe_text(value: Any, limit: int = 240) -> str | None:
    if not isinstance(value, str):
        return None
    value = " ".join(value.replace("\r", " ").replace("\n", " ").split())
    return value[:limit] if value else None


def _safe_id(value: Any, limit: int = 64) -> str | None:
    value = _safe_text(value, limit)
    if not value:
        return None
    value = _SAFE_ID.sub("-", value).strip("-.")
    return value[:limit] or None


def _safe_deep_link(value: Any) -> str | None:
    value = _safe_text(value, 1200)
    if not value:
        return None
    parsed = urlsplit(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc or parsed.username or parsed.password:
        return None
    try:
        hostname = parsed.hostname
        port = parsed.port
    except ValueError:
        return None
    if not hostname:
        return None
    netloc = hostname
    if port:
        netloc = f"{netloc}:{port}"
    # Query strings and fragments can carry tokens; only retain a safe path.
    return urlunsplit((parsed.scheme, netloc, parsed.path[:800], "", ""))


def _home() -> Path:
    override = os.environ.get("PIGEONHUB_AGENT_HOME")
    return Path(override).expanduser() if override else Path.home()


def agent_state_path() -> Path:
    override = os.environ.get("PIGEONHUB_AGENT_STATE")
    return Path(override).expanduser() if override else credentials_path().parent / "agent-state.json"


def _load_state(path: Path | None = None) -> dict[str, Any]:
    path = path or agent_state_path()
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return value if isinstance(value, dict) else {}


def _atomic_write(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent), text=True)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(value, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except Exception:
        try:
            os.unlink(temporary)
        except OSError:
            pass
        raise


def _save_state(value: Mapping[str, Any], path: Path | None = None) -> None:
    path = path or agent_state_path()
    _atomic_write(path, value)


def _event_name(raw: Mapping[str, Any]) -> str:
    value = raw.get("hook_event_name") or raw.get("hookEventName") or raw.get("event") or raw.get("type")
    return str(value or "").replace("_", "").replace("-", "").replace(".", "").lower()


def _session_id(raw: Mapping[str, Any]) -> str:
    for key in ("job_id", "thread_id", "threadId", "session_id", "sessionId", "GROK_SESSION_ID"):
        candidate = _safe_id(raw.get(key))
        if candidate:
            return candidate
    candidate = _safe_id(os.environ.get("PIGEONHUB_JOB_ID"))
    return candidate or "session"


def _actual_attention(raw: Mapping[str, Any], event: str) -> bool:
    if event == "permissionrequest":
        # Official hook contracts fire PermissionRequest only when a real
        # user-facing permission prompt would be shown.
        return True
    for key in ("needs_action", "needs_user", "awaiting_user", "waiting_for_user", "requires_input"):
        if raw.get(key) is True:
            return True
    kind = " ".join(str(raw.get(key, "")) for key in ("notification_type", "reason", "status", "message")).lower()
    return any(token in kind for token in ("permission", "approval", "approve", "elicitation", "awaiting input", "needs input"))


def _terminal_failure(raw: Mapping[str, Any], event: str) -> bool:
    if event in {"stopfailure", "fatalerror"}:
        return True
    if raw.get("fatal") is True or raw.get("terminal_failure") is True:
        return True
    if isinstance(raw.get("exit_code"), int) and raw.get("exit_code") != 0:
        return True
    if raw.get("success") is False:
        return True
    return event == "error" and raw.get("terminal") is True


def normalize_event(
    agent: str,
    raw: Mapping[str, Any],
    *,
    existing: Mapping[str, Any] | None = None,
    job_name: str | None = None,
) -> NormalizedJobEvent | None:
    """Map one vendor event to the small PigeonHub Job Model.

    In particular, PostToolUseFailure is progress/recovery information, not a
    terminal FAILED state, and only actual permission/attention events become
    NEEDS_ACTION.
    """

    if agent not in AGENTS:
        raise AgentSetupError(f"Unsupported agent: {agent}. Choose one of {', '.join(AGENTS)}")
    event = _event_name(raw)
    if not event:
        return None
    existing = existing or {}
    session = _session_id(raw)
    stable_id = _safe_id(raw.get("job_id")) or f"agent-{agent}-{session}"
    safe_name = _safe_text(job_name) or _safe_text(raw.get("job_name")) or _safe_text(raw.get("agent_name"))
    safe_name = safe_name or str(existing.get("job_name") or f"{agent.title()} session")
    started = _safe_text(raw.get("started_at")) or _safe_text(existing.get("started_at")) or _now()

    explicit = raw.get("state")
    state = str(explicit).upper() if explicit in STATES or str(explicit).upper() in STATES else None
    if state is None:
        if event in {"sessionstart", "threadstarted", "start", "agentstart"}:
            state = "RUNNING"
        elif event in {"permissionrequest", "notification"} and _actual_attention(raw, event):
            state = "NEEDS_ACTION"
        elif event in {"posttooluse", "itemcompleted", "turncompleted", "progress", "posttoolfailure", "posttoolusefailure"}:
            state = "PROGRESS"
        elif event in {"stopfailure", "fatalerror"} or _terminal_failure(raw, event):
            state = "FAILED"
        elif event in {"stop", "sessionend", "agentdone", "done", "success"}:
            state = "DONE"
        elif event == "error":
            # A non-terminal internal error can be recovered by the agent.
            state = "PROGRESS"
        else:
            return None

    progress_current = raw.get("progress_current")
    if not isinstance(progress_current, int):
        progress_current = raw.get("progress") if isinstance(raw.get("progress"), int) else None
    progress_total = raw.get("progress_total")
    if not isinstance(progress_total, int):
        progress_total = raw.get("total") if isinstance(raw.get("total"), int) else None
    if state == "PROGRESS" and progress_current is None:
        progress_current = int(existing.get("progress_current", 0)) + 1

    finished = _safe_text(raw.get("finished_at")) if state in TERMINAL_STATES else None
    if state in TERMINAL_STATES and not finished:
        finished = _now()
    attention = None
    if state == "NEEDS_ACTION":
        attention = _safe_text(raw.get("attention_reason")) or "Agent is waiting for your input"
    result = None
    if state == "DONE":
        result = _safe_text(raw.get("safe_summary")) or _safe_text(raw.get("result_summary")) or "Agent session completed"
    elif state == "FAILED":
        code = raw.get("exit_code")
        result = _safe_text(raw.get("safe_summary")) or (f"exit code {code}" if isinstance(code, int) else "Agent session failed")

    return NormalizedJobEvent(
        source=agent,
        job_id=stable_id,
        job_name=safe_name,
        state=state,
        started_at=started,
        finished_at=finished,
        attention_reason=attention,
        result_summary=result,
        deep_link=_safe_deep_link(raw.get("deep_link")),
        progress_current=progress_current,
        progress_total=progress_total,
    )


def publish_agent_event(
    agent: str,
    raw: Mapping[str, Any],
    *,
    job_name: str | None = None,
    state_path: Path | None = None,
) -> PublishResult | None:
    """Normalize and publish one event, retaining only local safe state."""

    path = state_path or agent_state_path()
    store = _load_state(path)
    session = _session_id(raw)
    lookup_id = _safe_id(raw.get("job_id")) or f"agent-{agent}-{session}"
    previous = store.get(lookup_id) if isinstance(store.get(lookup_id), dict) else {}
    event = normalize_event(agent, raw, existing=previous, job_name=job_name)
    if event is None:
        return None
    store[event.job_id] = {
        "job_name": event.job_name,
        "started_at": event.started_at,
        "progress_current": event.progress_current or previous.get("progress_current", 0),
        "state": event.state,
    }
    # Bound local metadata even if a long-lived machine runs many sessions.
    if len(store) > 200:
        for old_id in list(store)[: len(store) - 200]:
            store.pop(old_id, None)
    _save_state(store, path)
    kwargs: dict[str, Any] = {
        "job_name": event.job_name,
        "started_at": event.started_at,
        "finished_at": event.finished_at,
        "attention": event.attention_reason,
        "result": event.result_summary,
        "deep_link": event.deep_link,
        "progress": event.progress_current,
        "total": event.progress_total,
        "title": f"{event.job_name}: {event.state.lower().replace('_', ' ')}",
        "message": event.attention_reason or event.result_summary or event.state,
        "priority": "high" if event.state in {"NEEDS_ACTION", "FAILED"} else "normal",
    }
    return publish_job_detailed(event.source, event.job_id, event.state, **kwargs)


def handle_agent_event(agent: str, *, input_text: str | None = None, job_name: str | None = None) -> int:
    """Hook entry point. It is deliberately fail-open for the vendor agent."""

    try:
        raw_text = input_text if input_text is not None else os.sys.stdin.read()
        raw = json.loads(raw_text) if raw_text.strip() else {}
        if not isinstance(raw, dict):
            return 0
        publish_agent_event(agent, raw, job_name=job_name)
    except (CliError, OSError, ValueError, TypeError) as exc:
        print(f"[pigeonhub] agent hook skipped: {exc}", file=os.sys.stderr)
    return 0


def _spec_command(agent: str) -> str:
    return {"codex": "codex", "claude": "claude", "grok": "grok", "zcode": "zcode"}[agent]


def _config_path(agent: str, home: Path) -> Path:
    return {
        "codex": home / ".codex" / "hooks.json",
        "claude": home / ".claude" / "settings.json",
        "grok": home / ".grok" / "hooks" / "pigeonhub.json",
        "zcode": home / ".zcode" / "cli" / "config.json",
    }[agent]


def detect_agent(agent: str, *, home: Path | None = None) -> AgentDetection:
    if agent not in AGENTS:
        raise AgentSetupError(f"Unsupported agent: {agent}. Choose one of {', '.join(AGENTS)}")
    home = home or _home()
    executable_path = shutil.which(_spec_command(agent))
    version = None
    if executable_path:
        try:
            probe = [executable_path, "--version"]
            completed = subprocess.run(probe, capture_output=True, text=True, timeout=5, check=False)
            version = _safe_text((completed.stdout or completed.stderr).strip(), 120)
        except (OSError, subprocess.SubprocessError):
            version = None
    config = _config_path(agent, home)
    return AgentDetection(agent, executable_path, version, config, config.exists())


def _hook_events(agent: str) -> tuple[str, ...]:
    if agent == "codex":
        return ("SessionStart", "PermissionRequest", "PostToolUse", "PostToolUseFailure", "Stop")
    if agent == "claude":
        return ("SessionStart", "PermissionRequest", "Notification", "PostToolUse", "PostToolUseFailure", "Stop")
    if agent == "grok":
        return ("SessionStart", "PermissionDenied", "Notification", "PostToolUse", "PostToolUseFailure", "Stop", "StopFailure")
    return ("SessionStart", "PermissionRequest", "PostToolUse", "PostToolUseFailure", "Stop")


def _hook_handler(agent: str) -> dict[str, Any]:
    if agent == "zcode":
        # ZCode's process-hook schema is strict: only type/command/args/timeoutMs.
        return {"type": "process", "command": "pigeonhub", "args": ["agent-event", agent], "timeoutMs": 10000}
    return {"type": "command", "command": f"{_MANAGED_COMMAND} {agent}", "timeout": 10, "statusMessage": "Reporting lifecycle to PigeonHub"}


def _is_managed_hook(value: Any, agent: str) -> bool:
    if not isinstance(value, dict):
        return False
    if value.get("type") == "command" and value.get("command") == f"{_MANAGED_COMMAND} {agent}":
        return True
    return value.get("type") == "process" and value.get("command") == "pigeonhub" and value.get("args") == ["agent-event", agent]


def _remove_managed(events: Mapping[str, Any], agent: str) -> tuple[dict[str, Any], int]:
    result: dict[str, Any] = {}
    removed = 0
    for event_name, groups in events.items():
        if not isinstance(groups, list):
            raise AgentSetupError(f"Unsupported {agent} hook format for {event_name}; refusing to overwrite it")
        new_groups: list[Any] = []
        for group in groups:
            if not isinstance(group, dict) or not isinstance(group.get("hooks"), list):
                raise AgentSetupError(f"Unsupported {agent} hook entry for {event_name}; refusing to overwrite it")
            hooks = []
            for hook in group["hooks"]:
                if _is_managed_hook(hook, agent):
                    removed += 1
                else:
                    hooks.append(hook)
            if hooks:
                next_group = dict(group)
                next_group["hooks"] = hooks
                new_groups.append(next_group)
        if new_groups:
            result[event_name] = new_groups
    return result, removed


def _desired_json(agent: str, current: Mapping[str, Any], *, remove: bool = False) -> tuple[dict[str, Any], int, bool | None]:
    desired = copy.deepcopy(dict(current))
    previous_enabled: bool | None = None
    if agent == "zcode":
        hooks_root = desired.get("hooks", {})
        if not isinstance(hooks_root, dict):
            raise AgentSetupError("ZCode hooks must be an object; refusing to overwrite it")
        previous_enabled = hooks_root.get("enabled") if isinstance(hooks_root.get("enabled"), bool) else None
        events = hooks_root.get("events", {})
        if not isinstance(events, dict):
            raise AgentSetupError("ZCode hooks.events must be an object; refusing to overwrite it")
        clean, removed = _remove_managed(events, agent)
        if not remove:
            for event_name in _hook_events(agent):
                clean.setdefault(event_name, []).append({"hooks": [_hook_handler(agent)]})
            hooks_root["enabled"] = True
        elif clean:
            hooks_root["events"] = clean
        elif "events" in hooks_root:
            hooks_root["events"] = {}
        hooks_root["events"] = clean
        desired["hooks"] = hooks_root
        return desired, removed, previous_enabled
    hooks_root = desired.get("hooks", {})
    if not isinstance(hooks_root, dict):
        raise AgentSetupError(f"{agent} hooks must be an object; refusing to overwrite it")
    clean, removed = _remove_managed(hooks_root, agent)
    if not remove:
        for event_name in _hook_events(agent):
            clean.setdefault(event_name, []).append({"hooks": [_hook_handler(agent)]})
    desired["hooks"] = clean
    return desired, removed, previous_enabled


def _read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AgentSetupError(f"Cannot parse {path}; refusing to overwrite it: {exc}") from exc
    if not isinstance(value, dict):
        raise AgentSetupError(f"{path} must contain a JSON object; refusing to overwrite it")
    return value


def build_setup_plan(agent: str, *, home: Path | None = None, remove: bool = False) -> SetupPlan:
    home = home or _home()
    detection = detect_agent(agent, home=home)
    target = detection.config_path
    current = _read_json(target)
    if remove:
        if not target.exists():
            return SetupPlan(agent, detection, target, False, False, current, ("No PigeonHub setup found.",))
        desired, removed, previous_enabled = _desired_json(agent, current, remove=True)
        if agent == "zcode" and removed == 0:
            return SetupPlan(agent, detection, target, True, False, current, ("No PigeonHub setup found.",))
        lines = (f"Remove {removed} PigeonHub-managed hook(s) from {target}.",) if removed else ("No PigeonHub setup found.",)
        return SetupPlan(agent, detection, target, True, bool(removed), desired, lines, previous_enabled=previous_enabled)
    if not detection.detected:
        return SetupPlan(
            agent, detection, target, target.exists(), False, None,
            (f"{agent} was not detected on PATH or by its user configuration.",),
            "Install or sign in to the agent, then rerun this command.",
        )
    desired, removed, previous_enabled = _desired_json(agent, current)
    before = json.dumps(current, sort_keys=True)
    after = json.dumps(desired, sort_keys=True)
    changed = before != after
    action = "Update" if target.exists() else "Create"
    lines = (
        f"{action} {target}",
        f"Keep existing user hooks; add one PigeonHub handler to: {', '.join(_hook_events(agent))}",
        "Report only lifecycle, timing, safe summary, and attention reason; never prompts/source/tool input.",
        "A timestamped backup is created before an existing file is changed.",
    )
    if removed:
        lines = (f"Replace {removed} existing PigeonHub-managed hook(s) without duplicating them.",) + lines
    return SetupPlan(agent, detection, target, target.exists(), changed, desired, lines, previous_enabled=previous_enabled)


def _backup(path: Path) -> Path | None:
    if not path.exists():
        return None
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%SZ")
    destination = path.with_name(f"{path.name}.pigeonhub.bak.{stamp}")
    shutil.copy2(path, destination)
    return destination


def _managed_count(value: Mapping[str, Any], agent: str) -> int:
    root = value.get("hooks", {})
    if agent == "zcode" and isinstance(root, dict):
        root = root.get("events", {})
    if not isinstance(root, dict):
        return 0
    count = 0
    for groups in root.values():
        if isinstance(groups, list):
            for group in groups:
                if isinstance(group, dict) and isinstance(group.get("hooks"), list):
                    count += sum(1 for hook in group["hooks"] if _is_managed_hook(hook, agent))
    return count


def _metadata_path(agent: str, target: Path) -> Path:
    if agent == "grok":
        home = target.parent.parent.parent
    elif agent == "zcode":
        home = target.parent.parent.parent
    else:
        home = target.parent.parent
    return home / ".pigeonhub" / "agent-integrations" / f"{agent}.json"


def apply_setup(plan: SetupPlan) -> Path | None:
    if plan.blocked_reason:
        raise AgentSetupError(plan.blocked_reason)
    if not plan.changed or plan.desired is None:
        return None
    backup = _backup(plan.target)
    _atomic_write(plan.target, plan.desired)
    if _managed_count(_read_json(plan.target), plan.agent) != len(_hook_events(plan.agent)):
        raise AgentSetupError(f"Verification failed after writing {plan.target}; restore the backup before retrying")
    metadata = {
        "agent": plan.agent,
        "target": str(plan.target),
        "backup": str(backup) if backup else None,
        "managed_events": list(_hook_events(plan.agent)),
    }
    metadata["previous_enabled"] = plan.previous_enabled
    _atomic_write(_metadata_path(plan.agent, plan.target), metadata)
    return backup


def remove_setup(plan: SetupPlan) -> Path | None:
    if plan.blocked_reason:
        raise AgentSetupError(plan.blocked_reason)
    if not plan.changed or plan.desired is None:
        return None
    backup = _backup(plan.target)
    desired = copy.deepcopy(dict(plan.desired))
    metadata_path = _metadata_path(plan.agent, plan.target)
    if plan.agent == "zcode" and metadata_path.exists():
        metadata = _read_json(metadata_path)
        previous_enabled = metadata.get("previous_enabled")
        if isinstance(previous_enabled, bool):
            hooks_root = desired.get("hooks")
            if isinstance(hooks_root, dict):
                hooks_root["enabled"] = previous_enabled
    _atomic_write(plan.target, desired)
    if _managed_count(_read_json(plan.target), plan.agent) != 0:
        raise AgentSetupError(f"Removal verification failed for {plan.target}")
    return backup


def setup_summary(plan: SetupPlan) -> dict[str, Any]:
    return {
        "agent": plan.agent,
        "detected": plan.detection.detected,
        "executable": plan.detection.executable,
        "version": plan.detection.version,
        "target": str(plan.target),
        "changed": plan.changed,
        "blocked_reason": plan.blocked_reason,
        "changes": list(plan.change_lines),
    }
