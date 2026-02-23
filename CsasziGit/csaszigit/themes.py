"""
Theme definitions — Dracula (default), Dark, Bright.

Each theme is a colour palette + a generated Qt Style Sheet (QSS).
``apply_theme(app, name)`` applies the chosen theme globally.
"""

from PyQt6.QtWidgets import QApplication
from PyQt6.QtGui import QPalette, QColor
from PyQt6.QtCore import Qt

# ── Colour palettes ──────────────────────────────────────────────────────────

PALETTES = {
    "dracula": {
        "bg":         "#282A36",
        "bg_alt":     "#21222C",
        "surface":    "#343746",
        "current":    "#44475A",
        "fg":         "#F8F8F2",
        "fg_dim":     "#BFBFBF",
        "comment":    "#6272A4",
        "cyan":       "#8BE9FD",
        "green":      "#50FA7B",
        "orange":     "#FFB86C",
        "pink":       "#FF79C6",
        "purple":     "#BD93F9",
        "red":        "#FF5555",
        "yellow":     "#F1FA8C",
        "selection":  "#44475A",
        "border":     "#6272A4",
        "link":       "#8BE9FD",
        "button":     "#44475A",
        "button_hover": "#6272A4",
        "button_pressed": "#BD93F9",
        "accent":     "#BD93F9",
        "scrollbar":  "#44475A",
        "scrollbar_hover": "#6272A4",
        "tab_active": "#282A36",
        "tab_inactive": "#21222C",
        "diff_add_bg":  "#2a4030",
        "diff_del_bg":  "#4a2a2a",
        "diff_hunk_bg": "#2a2a4a",
    },
    "dark": {
        "bg":         "#1E1E1E",
        "bg_alt":     "#252526",
        "surface":    "#2D2D2D",
        "current":    "#3A3A3A",
        "fg":         "#D4D4D4",
        "fg_dim":     "#808080",
        "comment":    "#608B4E",
        "cyan":       "#4EC9B0",
        "green":      "#6A9955",
        "orange":     "#CE9178",
        "pink":       "#C586C0",
        "purple":     "#9CDCFE",
        "red":        "#F44747",
        "yellow":     "#DCDCAA",
        "selection":  "#264F78",
        "border":     "#474747",
        "link":       "#4EC9B0",
        "button":     "#3A3A3A",
        "button_hover": "#505050",
        "button_pressed": "#264F78",
        "accent":     "#007ACC",
        "scrollbar":  "#424242",
        "scrollbar_hover": "#4F4F4F",
        "tab_active": "#1E1E1E",
        "tab_inactive": "#2D2D2D",
        "diff_add_bg":  "#1E3A1E",
        "diff_del_bg":  "#3A1E1E",
        "diff_hunk_bg": "#1E1E3A",
    },
    "bright": {
        "bg":         "#FFFFFF",
        "bg_alt":     "#F5F5F5",
        "surface":    "#EEEEEE",
        "current":    "#E0E0E0",
        "fg":         "#1E1E1E",
        "fg_dim":     "#616161",
        "comment":    "#008000",
        "cyan":       "#0097A7",
        "green":      "#388E3C",
        "orange":     "#E65100",
        "pink":       "#C2185B",
        "purple":     "#7B1FA2",
        "red":        "#D32F2F",
        "yellow":     "#F9A825",
        "selection":  "#BBDEFB",
        "border":     "#BDBDBD",
        "link":       "#1565C0",
        "button":     "#E0E0E0",
        "button_hover": "#BDBDBD",
        "button_pressed": "#90CAF9",
        "accent":     "#1565C0",
        "scrollbar":  "#BDBDBD",
        "scrollbar_hover": "#9E9E9E",
        "tab_active": "#FFFFFF",
        "tab_inactive": "#F5F5F5",
        "diff_add_bg":  "#E8F5E9",
        "diff_del_bg":  "#FFEBEE",
        "diff_hunk_bg": "#E3F2FD",
    },
}

THEME_NAMES = list(PALETTES.keys())       # ["dracula", "dark", "bright"]
THEME_LABELS = {"dracula": "Dracula", "dark": "Dark", "bright": "Bright"}

# ---------------------------------------------------------------------------
# Palette accessor — widgets can query individual colours at runtime
# ---------------------------------------------------------------------------

_current_palette: dict = PALETTES["dracula"]


def palette() -> dict:
    """Return the active colour palette dict."""
    return _current_palette


# ---------------------------------------------------------------------------
# QSS generation
# ---------------------------------------------------------------------------

def _build_qss(p: dict) -> str:
    """Build a full Qt Style Sheet from a palette dict *p*."""
    return f"""
/* ── Global ─────────────────────────────────────────── */
QWidget {{
    background-color: {p["bg"]};
    color: {p["fg"]};
    font-family: "Segoe UI", "Consolas", sans-serif;
    font-size: 13px;
    selection-background-color: {p["selection"]};
    selection-color: {p["fg"]};
}}

/* ── Menu bar ───────────────────────────────────────── */
QMenuBar {{
    background-color: {p["bg_alt"]};
    color: {p["fg"]};
    border-bottom: 1px solid {p["border"]};
    padding: 2px;
}}
QMenuBar::item:selected {{
    background-color: {p["current"]};
    border-radius: 3px;
}}
QMenu {{
    background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    padding: 4px 0;
}}
QMenu::item:selected {{
    background-color: {p["accent"]};
    color: {p["bg"]};
    border-radius: 2px;
}}
QMenu::separator {{
    height: 1px;
    background: {p["border"]};
    margin: 4px 8px;
}}

/* ── Tool bar ───────────────────────────────────────── */
QToolBar {{
    background-color: {p["bg_alt"]};
    border-bottom: 1px solid {p["border"]};
    spacing: 4px;
    padding: 3px;
}}
QToolBar::separator {{
    width: 1px;
    background: {p["border"]};
    margin: 4px 2px;
}}
QToolButton {{
    background-color: transparent;
    color: {p["fg"]};
    border: 1px solid transparent;
    border-radius: 4px;
    padding: 4px 8px;
    font-size: 13px;
}}
QToolButton:hover {{
    background-color: {p["button_hover"]};
    border-color: {p["border"]};
}}
QToolButton:pressed {{
    background-color: {p["button_pressed"]};
}}

/* ── Push buttons ──────────────────────────────────── */
QPushButton {{
    background-color: {p["button"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    border-radius: 4px;
    padding: 5px 14px;
    min-height: 22px;
}}
QPushButton:hover {{
    background-color: {p["button_hover"]};
}}
QPushButton:pressed {{
    background-color: {p["button_pressed"]};
}}
QPushButton:disabled {{
    color: {p["fg_dim"]};
    background-color: {p["bg_alt"]};
}}
QPushButton[accent="true"] {{
    background-color: {p["accent"]};
    color: {p["bg"]};
    border-color: {p["accent"]};
    font-weight: bold;
}}
QPushButton[accent="true"]:hover {{
    background-color: {p["button_pressed"]};
}}
QPushButton[danger="true"] {{
    background-color: {p["red"]};
    color: #FFFFFF;
    border-color: {p["red"]};
}}

/* ── Line edits / text edits ────────────────────────── */
QLineEdit, QTextEdit, QPlainTextEdit {{
    background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    border-radius: 4px;
    padding: 4px 6px;
}}
QLineEdit:focus, QTextEdit:focus, QPlainTextEdit:focus {{
    border-color: {p["accent"]};
}}

/* ── Combo box ─────────────────────────────────────── */
QComboBox {{
    background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    border-radius: 4px;
    padding: 4px 8px;
    min-height: 22px;
}}
QComboBox::drop-down {{
    border: none;
    width: 20px;
}}
QComboBox QAbstractItemView {{
    background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    selection-background-color: {p["accent"]};
    selection-color: {p["bg"]};
}}

/* ── Spin box ──────────────────────────────────────── */
QSpinBox {{
    background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    border-radius: 4px;
    padding: 2px 4px;
}}

/* ── Tree / Table / List ───────────────────────────── */
QTreeWidget, QTreeView, QTableWidget, QTableView, QListWidget, QListView {{
    background-color: {p["bg_alt"]};
    alternate-background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    border-radius: 4px;
    outline: none;
    gridline-color: {p["border"]};
}}
QTreeWidget::item, QTableWidget::item, QListWidget::item {{
    padding: 3px 4px;
}}
QTreeWidget::item:hover, QTableWidget::item:hover, QListWidget::item:hover {{
    background-color: {p["current"]};
}}
QTreeWidget::item:selected, QTableWidget::item:selected, QListWidget::item:selected {{
    background-color: {p["selection"]};
    color: {p["fg"]};
}}
QTreeWidget::branch {{
    background: transparent;
}}

/* ── Header view ───────────────────────────────────── */
QHeaderView::section {{
    background-color: {p["bg_alt"]};
    color: {p["fg"]};
    border: none;
    border-right: 1px solid {p["border"]};
    border-bottom: 1px solid {p["border"]};
    padding: 4px 6px;
    font-weight: bold;
}}

/* ── Scroll bars ───────────────────────────────────── */
QScrollBar:vertical {{
    background: {p["bg_alt"]};
    width: 12px;
    margin: 0;
    border: none;
}}
QScrollBar::handle:vertical {{
    background: {p["scrollbar"]};
    min-height: 30px;
    border-radius: 4px;
    margin: 2px;
}}
QScrollBar::handle:vertical:hover {{
    background: {p["scrollbar_hover"]};
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0;
}}
QScrollBar:horizontal {{
    background: {p["bg_alt"]};
    height: 12px;
    margin: 0;
    border: none;
}}
QScrollBar::handle:horizontal {{
    background: {p["scrollbar"]};
    min-width: 30px;
    border-radius: 4px;
    margin: 2px;
}}
QScrollBar::handle:horizontal:hover {{
    background: {p["scrollbar_hover"]};
}}
QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {{
    width: 0;
}}

/* ── Tab widget ────────────────────────────────────── */
QTabWidget::pane {{
    border: 1px solid {p["border"]};
    border-top: none;
}}
QTabBar::tab {{
    background-color: {p["tab_inactive"]};
    color: {p["fg_dim"]};
    border: 1px solid {p["border"]};
    border-bottom: none;
    padding: 6px 16px;
    margin-right: 2px;
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
}}
QTabBar::tab:selected {{
    background-color: {p["tab_active"]};
    color: {p["fg"]};
    font-weight: bold;
}}
QTabBar::tab:hover:!selected {{
    background-color: {p["current"]};
    color: {p["fg"]};
}}

/* ── Splitter ──────────────────────────────────────── */
QSplitter::handle {{
    background-color: {p["border"]};
}}
QSplitter::handle:horizontal {{
    width: 2px;
}}
QSplitter::handle:vertical {{
    height: 2px;
}}

/* ── Status bar ────────────────────────────────────── */
QStatusBar {{
    background-color: {p["bg_alt"]};
    color: {p["fg_dim"]};
    border-top: 1px solid {p["border"]};
    padding: 2px;
}}

/* ── Dock widget ───────────────────────────────────── */
QDockWidget {{
    color: {p["fg"]};
    titlebar-close-icon: none;
}}
QDockWidget::title {{
    background-color: {p["bg_alt"]};
    border: 1px solid {p["border"]};
    padding: 5px;
    text-align: left;
    font-weight: bold;
}}

/* ── Group box ─────────────────────────────────────── */
QGroupBox {{
    border: 1px solid {p["border"]};
    border-radius: 4px;
    margin-top: 8px;
    padding-top: 12px;
    font-weight: bold;
}}
QGroupBox::title {{
    subcontrol-origin: margin;
    left: 10px;
    padding: 0 4px;
    color: {p["accent"]};
}}

/* ── Tooltips ──────────────────────────────────────── */
QToolTip {{
    background-color: {p["surface"]};
    color: {p["fg"]};
    border: 1px solid {p["border"]};
    padding: 4px;
    border-radius: 3px;
}}

/* ── Check / Radio boxes ───────────────────────────── */
QCheckBox, QRadioButton {{
    color: {p["fg"]};
    spacing: 6px;
}}
QCheckBox::indicator, QRadioButton::indicator {{
    width: 16px;
    height: 16px;
}}

/* ── Progress bar ──────────────────────────────────── */
QProgressBar {{
    background-color: {p["surface"]};
    border: 1px solid {p["border"]};
    border-radius: 4px;
    text-align: center;
    color: {p["fg"]};
    height: 18px;
}}
QProgressBar::chunk {{
    background-color: {p["accent"]};
    border-radius: 3px;
}}

/* ── Dialog ────────────────────────────────────────── */
QDialog {{
    background-color: {p["bg"]};
}}

/* ── Label accent helper ───────────────────────────── */
QLabel[accent="true"] {{
    color: {p["accent"]};
    font-weight: bold;
}}
"""


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def apply_theme(app: QApplication, name: str = "dracula"):
    """Apply the named theme to the whole application."""
    global _current_palette
    p = PALETTES.get(name, PALETTES["dracula"])
    _current_palette = p
    app.setStyleSheet(_build_qss(p))

    # Also set the QPalette for widgets that don't respect QSS fully
    pal = QPalette()
    pal.setColor(QPalette.ColorRole.Window, QColor(p["bg"]))
    pal.setColor(QPalette.ColorRole.WindowText, QColor(p["fg"]))
    pal.setColor(QPalette.ColorRole.Base, QColor(p["bg_alt"]))
    pal.setColor(QPalette.ColorRole.AlternateBase, QColor(p["surface"]))
    pal.setColor(QPalette.ColorRole.Text, QColor(p["fg"]))
    pal.setColor(QPalette.ColorRole.Button, QColor(p["button"]))
    pal.setColor(QPalette.ColorRole.ButtonText, QColor(p["fg"]))
    pal.setColor(QPalette.ColorRole.Highlight, QColor(p["accent"]))
    pal.setColor(QPalette.ColorRole.HighlightedText, QColor(p["bg"]))
    pal.setColor(QPalette.ColorRole.Link, QColor(p["link"]))
    pal.setColor(QPalette.ColorRole.ToolTipBase, QColor(p["surface"]))
    pal.setColor(QPalette.ColorRole.ToolTipText, QColor(p["fg"]))
    app.setPalette(pal)
