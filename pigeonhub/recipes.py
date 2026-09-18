"""BETA-001B: local command recipes.

A recipe stores a Name, an argv template, an optional default prompt and an
optional working directory on this PC only. Running one resolves the prompt
locally and delegates to the existing `pigeonhub run` lifecycle — there is no
second job engine, no scheduling, and nothing about the recipe (command,
prompt, working directory) is ever sent to the PigeonHub worker.

The only supported template placeholder is `{prompt}`; it is replaced inside
individual argv tokens, so the prompt is always a single argument value and
can never start a second command.
"""

from __future__ import annotations

import json
import os
import re
import shlex
import shutil
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Mapping, Sequence

from .core import CliError, _atomic_write_json, credentials_path

PROMPT_PLACEHOLDER = "{prompt}"
SCHEMA_VERSION = 1

_RECIPE_FILE = "recipes.v1.json"
_SLUG_FORBIDDEN = re.compile(r"[^a-z0-9-]+")


@dataclass(frozen=True)
class Recipe:
    id: str
    name: str
    argv: tuple[str, ...]
    default_prompt: str | None = None
    cwd: str | None = None
    created_at: str = ""
    updated_at: str = ""

    @property
    def uses_prompt(self) -> bool:
        return any(PROMPT_PLACEHOLDER in token for token in self.argv)


def recipes_path() -> Path:
    override = os.environ.get("PIGEONHUB_RECIPES")
    if override:
        return Path(override).expanduser()
    return credentials_path().parent / _RECIPE_FILE


def slugify(name: str) -> str:
    import uuid

    slug = _SLUG_FORBIDDEN.sub("-", name.strip().lower()).strip("-")
    slug = re.sub(r"-{2,}", "-", slug)
    if not slug:
        # Non-Latin names (e.g. Korean) keep their display name and get a
        # generated id so recipe add never fails on the language of the name.
        return f"recipe-{uuid.uuid4().hex[:8]}"
    return slug[:64]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_recipes(path: Path | None = None) -> dict[str, Recipe]:
    path = path or recipes_path()
    if not path.exists():
        return {}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        # Never wipe a file we cannot parse; the user may be able to repair it.
        raise CliError(f"PigeonHub recipes file at {path} is unreadable: {exc}. Fix or remove it, then try again.") from exc
    if not isinstance(raw, dict) or not isinstance(raw.get("recipes"), dict):
        raise CliError(f"PigeonHub recipes file at {path} has an unexpected format")
    recipes: dict[str, Recipe] = {}
    for key, value in raw["recipes"].items():
        argv = value.get("argv")
        if not isinstance(argv, list) or not argv or not all(isinstance(t, str) and t for t in argv):
            continue
        recipes[str(key)] = Recipe(
            id=str(value.get("id") or key),
            name=str(value.get("name") or key),
            argv=tuple(argv),
            default_prompt=value.get("default_prompt") or None,
            cwd=value.get("cwd") or None,
            created_at=str(value.get("created_at") or ""),
            updated_at=str(value.get("updated_at") or ""),
        )
    return recipes


def save_recipes(recipes: Mapping[str, Recipe], path: Path | None = None) -> Path:
    path = path or recipes_path()
    payload = {
        "schema_version": SCHEMA_VERSION,
        "recipes": {
            recipe_id: {
                "id": recipe.id,
                "name": recipe.name,
                "argv": list(recipe.argv),
                "default_prompt": recipe.default_prompt,
                "cwd": recipe.cwd,
                "created_at": recipe.created_at,
                "updated_at": recipe.updated_at,
            }
            for recipe_id, recipe in recipes.items()
        },
    }
    _atomic_write_json(path, payload)
    return path


def save_recipe(
    *,
    name: str,
    argv: Sequence[str],
    default_prompt: str | None = None,
    cwd: str | None = None,
    replace: bool = False,
    path: Path | None = None,
) -> Recipe:
    if not argv or not all(token.strip() for token in argv):
        raise CliError("A recipe needs a command. Example: pigeonhub recipe add --name \"Nightly crawler\" -- python crawler.py")
    recipe_id = slugify(name)
    recipes = load_recipes(path)
    now = _now()
    existing = recipes.get(recipe_id)
    if existing is not None and not replace:
        raise CliError(f"Recipe '{recipe_id}' already exists. Use --replace to update it.")
    recipe = Recipe(
        id=recipe_id,
        name=name.strip(),
        argv=tuple(argv),
        default_prompt=default_prompt.strip() if default_prompt else None,
        cwd=cwd or None,
        created_at=existing.created_at if existing else now,
        updated_at=now,
    )
    recipes[recipe_id] = recipe
    save_recipes(recipes, path)
    return recipe


def delete_recipe(recipe_id: str, *, path: Path | None = None) -> Recipe:
    recipes = load_recipes(path)
    recipe = recipes.get(recipe_id)
    if recipe is None:
        raise CliError(f"No recipe named '{recipe_id}'. Run: pigeonhub recipe list")
    del recipes[recipe_id]
    save_recipes(recipes, path)
    return recipe


def get_recipe(recipe_id: str, *, path: Path | None = None) -> Recipe:
    recipe = load_recipes(path).get(recipe_id)
    if recipe is None:
        raise CliError(f"No recipe named '{recipe_id}'. Run: pigeonhub recipe list")
    return recipe


def resolve_prompt(recipe: Recipe, *, prompt: str | None = None, prompt_file: str | None = None) -> str | None:
    """Runtime prompt beats stored default; otherwise interactive (tty only)."""
    if not recipe.uses_prompt:
        if prompt or prompt_file:
            print(f"Note: recipe '{recipe.id}' has no {PROMPT_PLACEHOLDER} in its command; the prompt is ignored.", file=sys.stderr)
        return None
    if prompt_file:
        try:
            content = Path(prompt_file).read_text(encoding="utf-8")
        except OSError as exc:
            raise CliError(f"Could not read prompt file: {exc}") from exc
        return content.rstrip("\r\n")
    if prompt:
        return prompt
    if recipe.default_prompt:
        return recipe.default_prompt
    if sys.stdin.isatty():
        try:
            answer = input("Prompt: ").strip()
        except EOFError:
            answer = ""
        if answer:
            return answer
    raise CliError(
        f"Recipe '{recipe.id}' needs a prompt. Use --prompt \"...\", --prompt-file <file>, or store a default with: pigeonhub recipe add --replace --prompt \"...\""
    )


def build_argv(recipe: Recipe, prompt: str | None) -> list[str]:
    if prompt is None:
        return list(recipe.argv)
    return [token.replace(PROMPT_PLACEHOLDER, prompt) for token in recipe.argv]


_SECRET_HINTS = (
    re.compile(r"sk-[A-Za-z0-9_-]{10,}"),
    re.compile(r"ghp_[A-Za-z0-9]{10,}"),
    re.compile(r"github_pat_[A-Za-z0-9_]{10,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\b(api[_-]?key|secret|password|passwd|token|bearer)\b\s*[:=]\s*\S", re.IGNORECASE),
)


def secret_warnings(argv: Sequence[str], default_prompt: str | None = None) -> list[str]:
    """Credential-shaped substrings worth a warning. Best effort, not a boundary."""
    text = " ".join(argv) + (" " + default_prompt if default_prompt else "")
    return [pattern.pattern for pattern in _SECRET_HINTS if pattern.search(text)]


def format_command(argv: Sequence[str]) -> str:
    return shlex.join(argv)


def run_recipe(
    recipe_id: str,
    *,
    prompt: str | None = None,
    prompt_file: str | None = None,
    path: Path | None = None,
) -> int:
    """Resolve the recipe locally, then delegate to the `pigeonhub run` lifecycle.

    Official agents (codex/claude/grok/zcode) run under the same wrapper: their
    native hooks detect the inherited PIGEONHUB_JOB_ID and attach to that job,
    so one execution is exactly one job card.
    """
    from .core import run_job

    recipe = get_recipe(recipe_id, path=path)
    effective = resolve_prompt(recipe, prompt=prompt, prompt_file=prompt_file)
    argv = build_argv(recipe, effective)
    if shutil.which(argv[0]) is None and not Path(argv[0]).is_file():
        raise CliError(f"Command not found: {argv[0]}. The recipe points at a program that is not on this PC's PATH.")
    if recipe.cwd:
        if not Path(recipe.cwd).is_dir():
            raise CliError(f"Working directory does not exist: {recipe.cwd}")
        os.chdir(recipe.cwd)
    return run_job(
        argv,
        name=recipe.name,
        source="recipe",
        message="Started from a PigeonHub recipe.",
    )
