"""Colour themes for the Money Splitter application.

Each theme is a plain dict with a fixed set of colour keys.  The
`build_stylesheet` helper turns any theme dict into a Qt stylesheet, and
`build_palette` produces a matching QPalette.
"""

from __future__ import annotations

from PyQt5.QtGui import QColor, QPalette

# =====================================================================
# Theme definitions
# =====================================================================

DRACULA: dict[str, str] = {
    "name": "Dracula",
    "bg": "#282a36",
    "current": "#44475a",
    "fg": "#f8f8f2",
    "comment": "#6272a4",
    "cyan": "#8be9fd",
    "green": "#50fa7b",
    "orange": "#ffb86c",
    "pink": "#ff79c6",
    "purple": "#bd93f9",
    "red": "#ff5555",
    "yellow": "#f1fa8c",
    "partial_split_bg": "#3d2f58",
    "calc_btn_bg": "#50fa7b",
    "calc_btn_hover": "#69d97a",
    "calc_btn_pressed": "#3fcf5e",
}

MONOKAI: dict[str, str] = {
    "name": "Monokai",
    "bg": "#272822",
    "current": "#3e3d32",
    "fg": "#f8f8f2",
    "comment": "#75715e",
    "cyan": "#66d9ef",
    "green": "#a6e22e",
    "orange": "#fd971f",
    "pink": "#f92672",
    "purple": "#ae81ff",
    "red": "#f92672",
    "yellow": "#e6db74",
    "partial_split_bg": "#3b3226",
    "calc_btn_bg": "#a6e22e",
    "calc_btn_hover": "#b8f040",
    "calc_btn_pressed": "#8cc91e",
}

NORD: dict[str, str] = {
    "name": "Nord",
    "bg": "#2e3440",
    "current": "#3b4252",
    "fg": "#eceff4",
    "comment": "#4c566a",
    "cyan": "#88c0d0",
    "green": "#a3be8c",
    "orange": "#d08770",
    "pink": "#b48ead",
    "purple": "#b48ead",
    "red": "#bf616a",
    "yellow": "#ebcb8b",
    "partial_split_bg": "#3b3952",
    "calc_btn_bg": "#a3be8c",
    "calc_btn_hover": "#b5d0a0",
    "calc_btn_pressed": "#8faa78",
}

SOLARIZED_LIGHT: dict[str, str] = {
    "name": "Solarized Light",
    "bg": "#fdf6e3",
    "current": "#eee8d5",
    "fg": "#657b83",
    "comment": "#93a1a1",
    "cyan": "#2aa198",
    "green": "#859900",
    "orange": "#cb4b16",
    "pink": "#d33682",
    "purple": "#6c71c4",
    "red": "#dc322f",
    "yellow": "#b58900",
    "partial_split_bg": "#f0e8c8",
    "calc_btn_bg": "#859900",
    "calc_btn_hover": "#95a910",
    "calc_btn_pressed": "#6d8000",
}

# ---- Registry of all available themes --------------------------------
ALL_THEMES: dict[str, dict[str, str]] = {
    "Dracula": DRACULA,
    "Monokai": MONOKAI,
    "Nord": NORD,
    "Solarized Light": SOLARIZED_LIGHT,
}

DEFAULT_THEME_NAME = "Dracula"

# ---- "active theme" singleton ----------------------------------------
_active_theme: dict[str, str] = dict(DRACULA)


def get_active_theme() -> dict[str, str]:
    """Return the currently active theme dict."""
    return _active_theme


def set_active_theme(name: str) -> dict[str, str]:
    """Switch the active theme.  Returns the new theme dict."""
    theme = ALL_THEMES.get(name, DRACULA)
    _active_theme.clear()
    _active_theme.update(theme)
    return _active_theme


# =====================================================================
# Currency colour palette derived from the active theme
# =====================================================================

def get_currency_palette(theme: dict[str, str] | None = None) -> list[str]:
    """Build an ordered list of currency colours from *theme*."""
    t = theme or _active_theme
    return [
        t["fg"],       # 0  base-currency colour
        t["cyan"],     # 1
        t["green"],    # 2
        t["orange"],   # 3
        t["pink"],     # 4
        t["yellow"],   # 5
        t["purple"],   # 6
        t["red"],      # 7
    ]


# =====================================================================
# Stylesheet generator
# =====================================================================

def build_stylesheet(theme: dict[str, str] | None = None) -> str:
    """Return a full Qt stylesheet string for *theme*."""
    t = theme or _active_theme

    # Determine whether the theme is "light" to adjust certain contrasts
    is_light = _luminance(t["bg"]) > 0.5
    calc_fg = t["bg"] if not is_light else "#ffffff"

    return f"""
/* ---- base ---- */
QMainWindow, QWidget {{
    background-color: {t['bg']};
    color: {t['fg']};
    font-family: 'Segoe UI';
}}
QMenuBar {{
    background-color: {t['current']};
    color: {t['fg']};
}}
QMenuBar::item:selected {{
    background-color: {t['comment']};
}}
QMenu {{
    background-color: {t['current']};
    color: {t['fg']};
    border: 1px solid {t['comment']};
}}
QMenu::item:selected {{
    background-color: {t['purple']};
    color: {t['fg']};
}}

/* ---- buttons ---- */
QPushButton {{
    background-color: {t['current']};
    color: {t['fg']};
    border: 1px solid {t['comment']};
    border-radius: 4px;
    padding: 6px 12px;
}}
QPushButton:hover {{
    background-color: {t['comment']};
}}
QPushButton:pressed {{
    background-color: {t['purple']};
}}

/* ---- table ---- */
QTableWidget {{
    background-color: {t['bg']};
    color: {t['fg']};
    gridline-color: {t['comment']};
    selection-background-color: {t['current']};
    selection-color: {t['fg']};
    border: 1px solid {t['comment']};
}}
QHeaderView::section {{
    background-color: {t['current']};
    color: {t['cyan']};
    border: 1px solid {t['comment']};
    padding: 4px;
    font-weight: bold;
}}

/* ---- inputs ---- */
QComboBox {{
    background-color: {t['current']};
    color: {t['fg']};
    border: 1px solid {t['comment']};
    border-radius: 3px;
    padding: 4px;
}}
QComboBox QAbstractItemView {{
    background-color: {t['current']};
    color: {t['fg']};
    selection-background-color: {t['purple']};
}}
QComboBox::drop-down {{
    border: none;
}}
QLineEdit, QDoubleSpinBox, QSpinBox {{
    background-color: {t['current']};
    color: {t['fg']};
    border: 1px solid {t['comment']};
    border-radius: 3px;
    padding: 4px;
}}

/* ---- labels ---- */
QLabel {{
    color: {t['fg']};
}}

/* ---- scroll area ---- */
QScrollArea {{
    border: none;
}}
QScrollBar:vertical {{
    background: {t['bg']};
    width: 12px;
}}
QScrollBar::handle:vertical {{
    background: {t['comment']};
    border-radius: 4px;
    min-height: 20px;
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0;
}}

/* ---- status bar ---- */
QStatusBar {{
    background-color: {t['current']};
    color: {t['comment']};
}}

/* ---- group box ---- */
QGroupBox {{
    color: {t['purple']};
    border: 1px solid {t['comment']};
    border-radius: 4px;
    margin-top: 8px;
    padding-top: 14px;
}}
QGroupBox::title {{
    subcontrol-origin: margin;
    left: 10px;
    padding: 0 4px;
}}

/* ---- check box ---- */
QCheckBox {{
    color: {t['fg']};
    spacing: 6px;
}}
QCheckBox::indicator {{
    width: 16px;
    height: 16px;
    border: 1px solid {t['comment']};
    border-radius: 3px;
    background-color: {t['bg']};
}}
QCheckBox::indicator:checked {{
    background-color: {t['purple']};
    border-color: {t['purple']};
}}

/* ---- tooltip ---- */
QToolTip {{
    background-color: {t['current']};
    color: {t['fg']};
    border: 1px solid {t['comment']};
    padding: 4px;
}}

/* ---- CALCULATE button (override) ---- */
#calc_btn {{
    background-color: {t['calc_btn_bg']};
    color: {calc_fg};
    border-radius: 6px;
    border: none;
}}
#calc_btn:hover {{
    background-color: {t['calc_btn_hover']};
}}
#calc_btn:pressed {{
    background-color: {t['calc_btn_pressed']};
}}
"""


# =====================================================================
# QPalette builder (for native dialogs / QMessageBox)
# =====================================================================

def build_palette(theme: dict[str, str] | None = None) -> QPalette:
    """Return a QPalette matching *theme*."""
    t = theme or _active_theme
    p = QPalette()
    p.setColor(QPalette.Window, QColor(t["bg"]))
    p.setColor(QPalette.WindowText, QColor(t["fg"]))
    p.setColor(QPalette.Base, QColor(t["current"]))
    p.setColor(QPalette.AlternateBase, QColor(t["bg"]))
    p.setColor(QPalette.ToolTipBase, QColor(t["current"]))
    p.setColor(QPalette.ToolTipText, QColor(t["fg"]))
    p.setColor(QPalette.Text, QColor(t["fg"]))
    p.setColor(QPalette.Button, QColor(t["current"]))
    p.setColor(QPalette.ButtonText, QColor(t["fg"]))
    p.setColor(QPalette.BrightText, QColor(t["red"]))
    p.setColor(QPalette.Link, QColor(t["cyan"]))
    p.setColor(QPalette.Highlight, QColor(t["purple"]))
    p.setColor(QPalette.HighlightedText, QColor(t["fg"]))
    p.setColor(QPalette.Disabled, QPalette.Text, QColor(t["comment"]))
    p.setColor(QPalette.Disabled, QPalette.ButtonText, QColor(t["comment"]))
    return p


# =====================================================================
# Utility
# =====================================================================

def _luminance(hex_colour: str) -> float:
    """Return a rough perceived brightness (0 = black, 1 = white)."""
    h = hex_colour.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255
