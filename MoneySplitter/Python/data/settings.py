"""Persist user settings (e.g. selected theme) to a JSON file."""

from __future__ import annotations

import json
import os
import sys

_SETTINGS_FILE_NAME = "settings.json"


def _settings_path() -> str:
    """Return the path to the settings file next to the executable / project root."""
    if getattr(sys, "frozen", False):
        base = os.path.dirname(sys.executable)
    else:
        # Dev mode: logic/ → project root is one level up
        base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, _SETTINGS_FILE_NAME)


def load_settings() -> dict:
    """Load settings from disk.  Returns empty dict on any failure."""
    path = _settings_path()
    if not os.path.isfile(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except (json.JSONDecodeError, OSError):
        return {}


def save_settings(data: dict) -> bool:
    """Persist *data* to the settings file.  Returns True on success."""
    path = _settings_path()
    try:
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2)
        return True
    except OSError:
        return False


# ---- Convenience helpers for the theme key ---------------------------

THEME_KEY = "theme"


def load_theme_name() -> str | None:
    """Return the saved theme name, or None if not set."""
    return load_settings().get(THEME_KEY)


def save_theme_name(name: str) -> bool:
    """Save the chosen theme name to settings."""
    settings = load_settings()
    settings[THEME_KEY] = name
    return save_settings(settings)
