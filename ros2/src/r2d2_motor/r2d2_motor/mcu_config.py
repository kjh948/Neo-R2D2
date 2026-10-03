"""Share the r2d2 app's configuration with the ROS node.

The app is normally started as ``python3 -m r2d2 --config r2d2/config_local.json``,
so the real serial device lives in that file (e.g. ``/dev/ttyAMA0`` on this
image), not in the code defaults. The node loads the same MCU-relevant keys
and uses them as its defaults; explicitly set ROS parameters still win.
"""
from __future__ import annotations

import json
import os
from typing import Any, Dict

# Only these keys of the app config concern the motor link.
MCU_KEYS = ("serial_port", "baudrate", "serial_read_timeout", "mock_serial")

# Relative to a repo root discovered by walking up from cwd/module dir.
REPO_CONFIG_REL = os.path.join("r2d2", "config_local.json")

FALLBACK_CONFIG_PATHS = (
    os.path.expanduser("~/.config/r2d2/config.json"),
    "/etc/r2d2/config.json",
)


def find_config_file(explicit: str = "") -> str:
    """Locate the r2d2 config JSON, mirroring how the app is actually launched."""
    if explicit:
        return explicit if os.path.isfile(explicit) else ""
    env = os.environ.get("R2D2_CONFIG", "")
    if env and os.path.isfile(env):
        return env
    for start in (os.getcwd(), os.path.dirname(os.path.abspath(__file__))):
        candidate = start
        for _ in range(10):
            repo_candidate = os.path.join(candidate, REPO_CONFIG_REL)
            if os.path.isfile(repo_candidate):
                return repo_candidate
            parent = os.path.dirname(candidate)
            if parent == candidate:
                break
            candidate = parent
    for path in FALLBACK_CONFIG_PATHS:
        if os.path.isfile(path):
            return path
    return ""


def load_mcu_config(explicit: str = "") -> Dict[str, Any]:
    """Return the MCU-relevant keys found in the config (empty dict if none)."""
    path = find_config_file(explicit)
    if not path:
        return {}
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return {}
    if not isinstance(data, dict):
        return {}
    values = {key: data[key] for key in MCU_KEYS if key in data}
    values["_config_path"] = path
    return values
