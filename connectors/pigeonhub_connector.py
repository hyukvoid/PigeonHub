#!/usr/bin/env python3
"""Compatibility entry point for the MVP-008 connector.

The supported user-facing command is now ``pigeonhub``. Existing ComfyUI and
agent-hook scripts still import this module, so the core symbols remain
re-exported and the old ``pair``/``attention`` names continue to work.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from pigeonhub.cli import main  # noqa: E402
from pigeonhub.core import credentials_path, http_json, load_credentials, publish_job  # noqa: E402,F401

CREDENTIALS_PATH = credentials_path()


if __name__ == "__main__":
    raise SystemExit(main())
