"""Core PigeonHub CLI operations.

The module deliberately stays stdlib-only. Packaging a Python interpreter and
this small package is an MVP-016 development distribution; a single-file
Windows distribution remains an MVP-018 concern.
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence


DEFAULT_WORKER_URL = "https://pigeonhub-push.pigeonhub.workers.dev"
PAIRING_CODE_RE = re.compile(r"PHC-[2-9A-HJKMNP-TV-Z]{5}-[2-9A-HJKMNP-TV-Z]{5}-[2-9A-HJKMNP-TV-Z]{5}")
TERMINAL_STATES = {"DONE", "FAILED", "NEEDS_ACTION"}
TRACKING_START_FAILURE_EXIT = 78


class CliError(RuntimeError):
    """A user-actionable CLI error that should not produce a traceback."""


@dataclass(frozen=True)
class PublishResult:
    ok: bool
    status: int
    body: Mapping[str, Any]

    @property
    def detail(self) -> str:
        if self.body.get("error"):
            return str(self.body["error"])
        if self.body.get("raw"):
            return str(self.body["raw"])
        return f"HTTP {self.status or 'network error'}"

    @property
    def push_status(self) -> str | None:
        value = self.body.get("push_status")
        return str(value) if value else None

    @property
    def delivered(self) -> bool:
        """True when the server reports the push reached at least one device."""
        return self.push_status in (None, "fcm_accepted")

    @property
    def stale_pairing(self) -> bool:
        """True when delivery failed because the device no longer knows us."""
        detail = self.detail
        return "NotRegistered" in detail or "UNREGISTERED" in detail


def credentials_path() -> Path:
    override = os.environ.get("PIGEONHUB_CREDENTIALS")
    return Path(override).expanduser() if override else Path.home() / ".pigeonhub" / "credentials.json"


def default_state_path() -> Path:
    override = os.environ.get("PIGEONHUB_STATE")
    return Path(override).expanduser() if override else credentials_path().parent / "state.json"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _atomic_write_json(path: Path, value: Mapping[str, Any]) -> None:
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
        raise


def load_credentials(path: Path | None = None) -> dict[str, Any]:
    path = path or credentials_path()
    if not path.exists():
        raise CliError("Not logged in. Run: pigeonhub login")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CliError(f"Could not read PigeonHub credentials at {path}: {exc}") from exc
    if not isinstance(value, dict) or not all(value.get(key) for key in ("endpoint", "write_token", "channel_id")):
        raise CliError(f"PigeonHub credentials at {path} are incomplete. Run: pigeonhub login")
    return value


def _save_credentials(value: Mapping[str, Any], path: Path | None = None) -> Path:
    path = path or credentials_path()
    _atomic_write_json(path, value)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass
    return path


def http_json(
    url: str,
    payload: Mapping[str, Any] | None = None,
    *,
    bearer: str | None = None,
    method: str | None = None,
    timeout: float = 30,
    idempotency_key: str | None = None,
) -> tuple[int, dict[str, Any]]:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(url, data=data, method=method or ("POST" if data else "GET"))
    request.add_header("Content-Type", "application/json")
    from . import __version__

    request.add_header("User-Agent", f"PigeonHubCLI/{__version__}")
    if bearer:
        request.add_header("Authorization", f"Bearer {bearer}")
    if idempotency_key:
        request.add_header("Idempotency-Key", idempotency_key)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", "replace")
            try:
                body = json.loads(raw)
            except json.JSONDecodeError:
                body = {"raw": raw[:500]}
            return response.status, body if isinstance(body, dict) else {"raw": body}
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            body = {"raw": raw[:500]}
        return exc.code, body if isinstance(body, dict) else {"raw": body}
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        return 0, {"error": str(exc)}


def _payload(
    source: str,
    job_id: str,
    state: str,
    *,
    job_name: str | None = None,
    progress: int | None = None,
    total: int | None = None,
    attention: str | None = None,
    result: str | None = None,
    started_at: str | None = None,
    finished_at: str | None = None,
    deep_link: str | None = None,
    title: str | None = None,
    message: str | None = None,
    priority: str = "normal",
) -> dict[str, Any]:
    job: dict[str, Any] = {"source": source, "job_id": job_id, "state": state}
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
    if deep_link:
        job["deep_link"] = deep_link
    return {
        "title": (title or f"{job_name or job_id}: {state.lower()}")[:500],
        "message": str(message if message is not None else state)[:4000],
        "priority": priority,
        "job": job,
    }


def _stale_pairing_guidance() -> str:
    return (
        "This PC's pairing can no longer reach your phone.\n"
        "The event was saved, but the device rejected the push.\n\n"
        "Fix:\n"
        "  1. Open PigeonHub on your phone and make sure it is signed in.\n"
        "  2. Run: pigeonhub login   (then scan the QR with the phone)"
    )


def publish_payload(payload: Mapping[str, Any], state: str) -> PublishResult:
    """Publish one event; retries converge on one stored row.

    MVP-019 semantics: durability and delivery are distinct. A 200 with
    `stored: true` ends the loop even when `push_status: "failed"` — the row
    is durable and the delivery outcome is data, not a transport error. Only
    genuinely ambiguous outcomes (network loss, 5xx, quota) retry, and every
    attempt carries the same Idempotency-Key so the server replays the first
    stored row instead of duplicating it.
    """
    credentials = load_credentials()
    idempotency_key = hashlib.sha256(
        json.dumps(payload, sort_keys=True).encode("utf-8")
    ).hexdigest()[:40]
    attempts = 8 if state in TERMINAL_STATES else 1
    status = 0
    body: dict[str, Any] = {"error": "not attempted"}
    for attempt in range(1, attempts + 1):
        status, body = http_json(
            credentials["endpoint"],
            dict(payload),
            bearer=credentials["write_token"],
            idempotency_key=idempotency_key,
        )
        stored = status in (200, 201) and bool(body.get("stored"))
        if stored:
            return PublishResult(True, status, body)
        retryable = status == 429 or status >= 500 or status == 0
        if not retryable or attempt == attempts:
            break
        wait = min(15.0, 2 ** attempt) + (0.3 * attempt)
        print(f"[pigeonhub] {state} HTTP {status or 'network error'}, retrying in {wait:.0f}s ({attempt}/{attempts})", file=sys.stderr)
        time.sleep(wait)
    return PublishResult(False, status, body)


def _delivery_note(result: PublishResult) -> str:
    if result.delivered:
        return " OK" if not result.body.get("idempotent_replay") else " OK (replayed)"
    return " saved, delivery failed"


def publish_job(source: str, job_id: str, state: str, **kwargs: Any) -> bool:
    """Publish a structured event, retaining the legacy boolean contract."""
    result = publish_payload(_payload(source, job_id, state, **kwargs), state)
    progress = ""
    if kwargs.get("progress") is not None:
        progress = f" {kwargs['progress']}/{kwargs.get('total')}"
    print(f"[pigeonhub] {state}{progress}" + _delivery_note(result))
    if result.ok and not result.delivered and result.stale_pairing:
        print(_stale_pairing_guidance(), file=sys.stderr)
    return result.ok


def publish_job_detailed(source: str, job_id: str, state: str, **kwargs: Any) -> PublishResult:
    result = publish_payload(_payload(source, job_id, state, **kwargs), state)
    progress = ""
    if kwargs.get("progress") is not None:
        progress = f" {kwargs['progress']}/{kwargs.get('total')}"
    print(f"[pigeonhub] {state}{progress}" + _delivery_note(result))
    if result.ok and not result.delivered and result.stale_pairing:
        print(_stale_pairing_guidance(), file=sys.stderr)
    return result


def publish_message(title: str, message: str, *, priority: str = "normal") -> PublishResult:
    credentials = load_credentials()
    payload = {"title": title[:500], "message": message[:4000], "priority": priority}
    status, body = http_json(credentials["endpoint"], payload, bearer=credentials["write_token"])
    result = PublishResult(status in (200, 201) and bool(body.get("stored")), status, body)
    print(f"[pigeonhub] notification" + _delivery_note(result))
    if result.ok and not result.delivered and result.stale_pairing:
        print(_stale_pairing_guidance(), file=sys.stderr)
    return result


def read_pairing_code_from_image(path: str | Path) -> str | None:
    try:
        import zxingcpp  # type: ignore
        from PIL import Image  # type: ignore
    except ImportError as exc:
        raise CliError("QR scanning needs zxing-cpp and Pillow; type the one-time code instead") from exc
    results = zxingcpp.read_barcodes(Image.open(path))
    for result in results:
        match = PAIRING_CODE_RE.search(result.text)
        if match:
            return match.group(0)
    return None


def _print_login_qr(payload: str) -> None:
    try:
        import qrcode  # type: ignore
    except ImportError as exc:
        raise CliError("pigeonhub login needs the qrcode package to display a QR in the terminal") from exc
    qr = qrcode.QRCode(border=2, box_size=1)
    qr.add_data(payload)
    qr.make(fit=True)
    matrix = qr.get_matrix()
    print("Scan this QR with the PigeonHub Android app:")
    for row in matrix:
        print("".join("##" if cell else "  " for cell in row))


def _render_login_qr_png(payload: str) -> Path:
    """Render the pairing QR as a raster PNG and return its temporary path.

    Phone cameras cannot reliably scan terminal-rendered QR: font metrics,
    Windows display scaling, line spacing, and terminal cell aspect ratio all
    distort the modules. The PNG is plain black/white with square modules,
    integer pixel scaling, and a 4-module quiet zone. The caller must delete
    the file once login finishes.
    """
    import qrcode  # type: ignore

    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=4, box_size=1)
    qr.add_data(payload)
    qr.make(fit=True)
    modules = qr.modules_count + 2 * qr.border
    qr.box_size = max(8, math.ceil(512 / modules))
    fd, raw = tempfile.mkstemp(prefix="pigeonhub-login-", suffix=".png")
    os.close(fd)
    path = Path(raw)
    try:
        qr.make_image(fill_color="black", back_color="white").save(str(path))
    except Exception:
        _remove_temp_qr(path)
        raise
    return path


def _remove_temp_qr(path: Path) -> None:
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass


def _open_qr_image(path: Path) -> bool:
    """Open the QR PNG in the default image viewer. Best effort, never fatal."""
    try:
        if os.name == "nt":
            os.startfile(str(path))  # type: ignore[attr-defined]  # noqa: S606
            return True
    except OSError:
        pass
    return False


def _print_waiting(deadline: float) -> None:
    # Cap the display at 99:59 so a bogus server clock can never print an
    # absurd counter; the real pairing TTL is 10 minutes.
    remaining = min(99 * 60 + 59, max(0, int(deadline - time.time())))
    text = f"Waiting for approval... Expires in {remaining // 60:02d}:{remaining % 60:02d}"
    if sys.stdout.isatty():
        sys.stdout.write("\r" + text + "  ")
        sys.stdout.flush()
    elif not getattr(_print_waiting, "_quiet", False) or time.time() - _print_waiting._last > 60:  # type: ignore[attr-defined]
        print(text)
        _print_waiting._quiet = True  # type: ignore[attr-defined]
        _print_waiting._last = time.time()  # type: ignore[attr-defined]


def _save_redeemed_login(body: Mapping[str, Any]) -> Path:
    required = ("endpoint", "write_token", "channel_id")
    if not all(body.get(key) for key in required):
        raise CliError("Login response did not contain a complete connector credential")
    path = _save_credentials(
        {
            "endpoint": body["endpoint"],
            "write_token": body["write_token"],
            "channel_id": body["channel_id"],
            "paired_at": _now(),
        }
    )
    print(f"Logged in to channel {body['channel_id'][:12]}... - credentials saved to {path}")
    return path


def _legacy_login(*, code: str | None = None, qr_image: str | None = None, worker_url: str | None = None) -> Path:
    if qr_image:
        code = read_pairing_code_from_image(qr_image)
        if not code:
            raise CliError(f"No PigeonHub pairing code found in {qr_image}")
    if not code:
        print("Open PigeonHub on Android, choose a PC connection, and enter the one-time code shown there.")
        try:
            code = input("Pairing code: ").strip()
        except EOFError as exc:
            raise CliError("No pairing code supplied. Run: pigeonhub login --code PHC-XXXXX-XXXXX-XXXXX") from exc
    base = (worker_url or os.environ.get("PIGEONHUB_WORKER_URL") or DEFAULT_WORKER_URL).rstrip("/")
    status, body = http_json(f"{base}/v1/pairing/redeem", {"code": code})
    if status != 200 or not body.get("ok"):
        raise CliError(f"Login failed: {body.get('error', status or 'network error')}")
    return _save_redeemed_login(body)


def _qr_login(*, worker_url: str | None = None) -> Path:
    base = (worker_url or os.environ.get("PIGEONHUB_WORKER_URL") or DEFAULT_WORKER_URL).rstrip("/")
    status, body = http_json(f"{base}/v1/pairing/requests", {})
    if status != 200 or not body.get("ok"):
        raise CliError(f"Could not start PC login: {body.get('error', status or 'network error')}")
    request_id = str(body.get("request_id", ""))
    challenge = str(body.get("challenge", ""))
    poll_secret = str(body.get("poll_secret", ""))
    if not request_id or not challenge or not poll_secret:
        raise CliError("PC login response was incomplete")
    query = urllib.parse.urlencode({"request_id": request_id, "challenge": challenge})
    payload = str(body.get("qr_payload") or f"pigeonhub://login?{query}")
    expires_at = body.get("expires_at")
    try:
        deadline = datetime.fromisoformat(str(expires_at).replace("Z", "+00:00")).timestamp() if expires_at else time.time() + 600
    except ValueError:
        deadline = time.time() + 600
    qr_image: Path | None = None
    try:
        try:
            qr_image = _render_login_qr_png(payload)
        except Exception:
            qr_image = None
        print("Connect this PC")
        print()
        if qr_image is not None:
            if os.environ.get("PIGEONHUB_NO_OPEN"):
                print("QR image rendered (automatic opening disabled by PIGEONHUB_NO_OPEN).")
            elif _open_qr_image(qr_image):
                print("A QR code has opened in your image viewer.")
            else:
                # Fallback only: terminal QR is unreliable on real phone cameras.
                _print_login_qr(payload)
        else:
            # Fallback only: terminal QR is unreliable on real phone cameras.
            _print_login_qr(payload)
        print("On your phone:")
        print("  PigeonHub -> Connections -> Connect PC")
        print()
        try:
            while time.time() < deadline:
                _print_waiting(deadline)
                poll_status, poll_body = http_json(
                    f"{base}/v1/pairing/requests/poll",
                    {"request_id": request_id, "poll_secret": poll_secret},
                )
                if poll_status == 200 and poll_body.get("status") == "pending":
                    time.sleep(2)
                    continue
                if poll_status == 200 and poll_body.get("status") == "approved":
                    if sys.stdout.isatty():
                        sys.stdout.write("\n")
                    return _save_redeemed_login(poll_body)
                if sys.stdout.isatty():
                    sys.stdout.write("\n")
                raise CliError(f"PC login failed: {poll_body.get('error', poll_status or 'network error')}")
            if sys.stdout.isatty():
                sys.stdout.write("\n")
            raise CliError("PC login request expired before Android approval")
        except KeyboardInterrupt:
            raise CliError("Login cancelled before approval. The pairing request expires on its own.") from None
    finally:
        if qr_image is not None:
            _remove_temp_qr(qr_image)


def login(*, code: str | None = None, qr_image: str | None = None, worker_url: str | None = None, legacy: bool = False) -> Path:
    if code or qr_image or legacy:
        return _legacy_login(code=code, qr_image=qr_image, worker_url=worker_url)
    return _qr_login(worker_url=worker_url)


def logout() -> bool:
    path = credentials_path()
    if not path.exists():
        print("Already logged out.")
        return False
    try:
        path.unlink()
    except OSError as exc:
        raise CliError(f"Could not remove credentials at {path}: {exc}") from exc
    print(f"Logged out. Removed local credentials from {path}")
    return True


def worker_origin(endpoint: str) -> str:
    parsed = urllib.parse.urlsplit(endpoint)
    if not parsed.scheme or not parsed.netloc:
        raise CliError("Saved PigeonHub endpoint is invalid; run: pigeonhub login")
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, "", "", "")).rstrip("/")


def status(*, quiet: bool = False) -> dict[str, Any]:
    path = credentials_path()
    if not path.exists():
        value = {"logged_in": False, "credentials_path": str(path)}
        if not quiet:
            print("Not logged in. Run: pigeonhub login")
        return value
    credentials = load_credentials(path)
    health_status, health = http_json(f"{worker_origin(credentials['endpoint'])}/health", timeout=5)
    value = {
        "logged_in": True,
        "channel_id": credentials["channel_id"],
        "endpoint": credentials["endpoint"],
        "paired_at": credentials.get("paired_at"),
        "credentials_path": str(path),
        "worker_reachable": health_status == 200 and bool(health.get("ok")),
    }
    if not quiet:
        print(f"Connected - channel {credentials['channel_id'][:12]}...")
        print(f"Server: {'reachable' if value['worker_reachable'] else 'unreachable'}")
    return value


def _load_job_context(job_id: str | None = None) -> dict[str, Any]:
    explicit_id = job_id or os.environ.get("PIGEONHUB_JOB_ID")
    state_path_value = os.environ.get("PIGEONHUB_STATE")
    state_path = Path(state_path_value).expanduser() if state_path_value else None
    if state_path and state_path.exists():
        try:
            state = json.loads(state_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise CliError(f"Could not read current job state: {exc}") from exc
        if explicit_id and state.get("job_id") != explicit_id:
            raise CliError("PIGEONHUB_JOB_ID does not match PIGEONHUB_STATE")
        return state
    if explicit_id:
        return {
            "source": os.environ.get("PIGEONHUB_JOB_SOURCE", "cli"),
            "job_id": explicit_id,
            "job_name": os.environ.get("PIGEONHUB_JOB_NAME", explicit_id),
            "started_at": os.environ.get("PIGEONHUB_STARTED_AT"),
        }
    raise CliError("No running job context. Use progress/needs-action from inside `pigeonhub run`.")


def report_progress(current: int, total: int, *, job_id: str | None = None) -> PublishResult:
    if current < 0 or total <= 0 or current > total:
        raise CliError("Progress must satisfy 0 <= current <= total and total > 0")
    state = _load_job_context(job_id)
    return publish_job_detailed(
        state.get("source", "cli"),
        state["job_id"],
        "PROGRESS",
        job_name=state.get("job_name"),
        started_at=state.get("started_at"),
        progress=current,
        total=total,
    )


def report_needs_action(reason: str, *, job_id: str | None = None) -> PublishResult:
    if not reason.strip():
        raise CliError("A reason is required for needs-action")
    state = _load_job_context(job_id)
    name = state.get("job_name") or state["job_id"]
    return publish_job_detailed(
        state.get("source", "cli"),
        state["job_id"],
        "NEEDS_ACTION",
        job_name=name,
        started_at=state.get("started_at"),
        attention=reason,
        title=f"{name}: needs your action",
        message=reason,
        priority="high",
    )


def _resolve_windows_command(command: Sequence[str]) -> Sequence[str]:
    """CreateProcess runs real executables only; .cmd/.bat shims need cmd.exe.

    Tools like npm or winget ship as batch shims, so `pigeonhub run -- npm …`
    would otherwise fail to start on Windows.
    """
    if os.name != "nt" or not command:
        return command
    executable = shutil.which(command[0])
    if executable and executable.lower().endswith((".cmd", ".bat")):
        return ["cmd", "/c"] + list(command)
    return command


def run_job(command: Sequence[str], *, name: str | None = None, source: str = "cli", message: str | None = None) -> int:
    if not command:
        raise CliError("A command is required. Example: pigeonhub run python crawler.py")
    job_name = name or " ".join(command)
    job_id = f"{source}-{uuid.uuid4().hex}"
    started = _now()
    state_path = default_state_path()
    state = {"source": source, "job_id": job_id, "job_name": job_name, "started_at": started}
    _atomic_write_json(state_path, state)

    try:
        running = publish_job_detailed(
            source,
            job_id,
            "RUNNING",
            job_name=job_name,
            started_at=started,
            title=f"{job_name}: started",
            # BETA-001B: recipes never transmit their command/prompt to the
            # worker, so they pass an explicit safe message here.
            message=" ".join(command) if message is None else message,
        )
    except CliError as exc:
        running = None
        print(f"PigeonHub could not start tracking this job: {exc}", file=sys.stderr)
    if running is None or not running.ok:
        print(
            "PigeonHub could not start tracking this job.\n"
            "The command was not started.\n\n"
            "Run:\n"
            "pigeonhub status",
            file=sys.stderr,
        )
        try:
            state_path.unlink()
        except OSError:
            pass
        return TRACKING_START_FAILURE_EXIT

    child_env = os.environ.copy()
    child_env.update(
        {
            "PIGEONHUB_JOB_ID": job_id,
            "PIGEONHUB_JOB_NAME": job_name,
            "PIGEONHUB_JOB_SOURCE": source,
            "PIGEONHUB_STARTED_AT": started,
            "PIGEONHUB_STATE": str(state_path),
        }
    )
    try:
        process = subprocess.Popen(_resolve_windows_command(list(command)), env=child_env)
        exit_code = process.wait()
    except OSError as exc:
        try:
            publish_job_detailed(
                source,
                job_id,
                "FAILED",
                job_name=job_name,
                started_at=started,
                finished_at=_now(),
                attention=f"could not start command: {exc}",
                title=f"{job_name}: failed",
                message=str(exc),
                priority="high",
            )
        except CliError as publish_error:
            print(f"PigeonHub could not publish FAILED: {publish_error}", file=sys.stderr)
        try:
            state_path.unlink()
        except OSError:
            pass
        return 1

    if exit_code == 0:
        terminal_state = "DONE"
        terminal_kwargs = {"result": "exit 0", "message": "exit code 0"}
    else:
        terminal_state = "FAILED"
        terminal_kwargs = {"attention": f"exit code {exit_code}", "message": f"exit code {exit_code}"}
    try:
        terminal = publish_job_detailed(
            source,
            job_id,
            terminal_state,
            job_name=job_name,
            started_at=started,
            finished_at=_now(),
            title=f"{job_name}: {'done' if terminal_state == 'DONE' else 'failed'}",
            priority="high",
            **terminal_kwargs,
        )
    except CliError as exc:
        terminal = None
        print(f"PigeonHub could not publish {terminal_state}: {exc}", file=sys.stderr)
    if terminal is None or not terminal.ok:
        print(f"PigeonHub could not publish {terminal_state}; the child exit code remains {exit_code}.", file=sys.stderr)
    try:
        state_path.unlink()
    except OSError:
        pass
    return exit_code


def comfyui_demo() -> int:
    job_id = f"comfy-{uuid.uuid4().hex}"
    started = _now()
    total = 20
    if not publish_job("comfyui", job_id, "RUNNING", job_name="Video generation", started_at=started, title="ComfyUI: video generation", message="workflow queued"):
        return TRACKING_START_FAILURE_EXIT
    for step in range(1, total + 1):
        time.sleep(1.5)
        publish_job("comfyui", job_id, "PROGRESS", job_name="Video generation", progress=step, total=total, started_at=started, title="ComfyUI: video generation", message="rendering")
    publish_job("comfyui", job_id, "DONE", job_name="Video generation", started_at=started, finished_at=_now(), result=f"1 video - {total} steps", title="ComfyUI: video generation", message="render complete", priority="high")
    print("Done — check the Job Inbox on your phone.")
    return 0
