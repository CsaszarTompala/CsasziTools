"""Colour themes and theme persistence for the Remote Desktop Connector.

Same palette set as MoneySplitter: Dracula, Monokai, Nord, Solarized Light.
"""

from __future__ import annotations

import json
import os


# ---------------------------------------------------------------------------
# Theme definitions
# ---------------------------------------------------------------------------
THEMES: dict[str, dict[str, str]] = {
    "Dracula": {
        "bg":        "#282a36",
        "current":   "#44475a",
        "fg":        "#f8f8f2",
        "comment":   "#6272a4",
        "cyan":      "#8be9fd",
        "green":     "#50fa7b",
        "orange":    "#ffb86c",
        "pink":      "#ff79c6",
        "purple":    "#bd93f9",
        "red":       "#ff5555",
        "yellow":    "#f1fa8c",
        "accent":    "#bd93f9",
        "btn_bg":    "#44475a",
        "btn_fg":    "#f8f8f2",
        "btn_active": "#6272a4",
        "entry_bg":  "#44475a",
        "entry_fg":  "#f8f8f2",
        "select_bg": "#6272a4",
    },
    "Monokai": {
        "bg":        "#272822",
        "current":   "#3e3d32",
        "fg":        "#f8f8f2",
        "comment":   "#75715e",
        "cyan":      "#66d9ef",
        "green":     "#a6e22e",
        "orange":    "#fd971f",
        "pink":      "#f92672",
        "purple":    "#ae81ff",
        "red":       "#f92672",
        "yellow":    "#e6db74",
        "accent":    "#a6e22e",
        "btn_bg":    "#3e3d32",
        "btn_fg":    "#f8f8f2",
        "btn_active": "#75715e",
        "entry_bg":  "#3e3d32",
        "entry_fg":  "#f8f8f2",
        "select_bg": "#75715e",
    },
    "Nord": {
        "bg":        "#2e3440",
        "current":   "#3b4252",
        "fg":        "#eceff4",
        "comment":   "#4c566a",
        "cyan":      "#88c0d0",
        "green":     "#a3be8c",
        "orange":    "#d08770",
        "pink":      "#b48ead",
        "purple":    "#b48ead",
        "red":       "#bf616a",
        "yellow":    "#ebcb8b",
        "accent":    "#88c0d0",
        "btn_bg":    "#3b4252",
        "btn_fg":    "#eceff4",
        "btn_active": "#4c566a",
        "entry_bg":  "#3b4252",
        "entry_fg":  "#eceff4",
        "select_bg": "#4c566a",
    },
    "Solarized Light": {
        "bg":        "#fdf6e3",
        "current":   "#eee8d5",
        "fg":        "#657b83",
        "comment":   "#93a1a1",
        "cyan":      "#2aa198",
        "green":     "#859900",
        "orange":    "#cb4b16",
        "pink":      "#d33682",
        "purple":    "#6c71c4",
        "red":       "#dc322f",
        "yellow":    "#b58900",
        "accent":    "#268bd2",
        "btn_bg":    "#eee8d5",
        "btn_fg":    "#657b83",
        "btn_active": "#93a1a1",
        "entry_bg":  "#eee8d5",
        "entry_fg":  "#657b83",
        "select_bg": "#93a1a1",
    },
}

ALL_THEME_NAMES: list[str] = list(THEMES.keys())
DEFAULT_THEME = "Dracula"

# ---------------------------------------------------------------------------
# Settings persistence
# ---------------------------------------------------------------------------
_SETTINGS_FILE = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "settings.json",
)


def load_theme_name() -> str:
    """Load the saved theme name from settings, defaulting to Dracula."""
    try:
        with open(_SETTINGS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            name = data.get("theme", DEFAULT_THEME)
            return name if name in THEMES else DEFAULT_THEME
    except (FileNotFoundError, json.JSONDecodeError, IOError):
        return DEFAULT_THEME


def save_theme_name(name: str) -> None:
    """Persist the selected theme name to settings.json."""
    data: dict = {}
    try:
        with open(_SETTINGS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
    except (FileNotFoundError, json.JSONDecodeError, IOError):
        pass
    data["theme"] = name
    try:
        with open(_SETTINGS_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=4)
    except IOError:
        pass
