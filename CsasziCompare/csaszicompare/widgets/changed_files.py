"""
Changed-files panel — shows the list of files that differ between two
sides (directories or commits).  Clicking a file emits ``file_selected``
with the relative path.
"""

import os
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QListWidget, QListWidgetItem, QLabel,
    QAbstractItemView,
)
from PyQt6.QtGui import QFont, QColor, QIcon
from PyQt6.QtCore import Qt, pyqtSignal

from csaszicompare.diff_engine import FileDiff, FileState, diff_directories_changed_only
from csaszicompare.themes import palette


_STATE_ICON = {
    FileState.MODIFIED: "✏️",
    FileState.ADDED:    "➕",
    FileState.REMOVED:  "➖",
}

_STATE_LABEL = {
    FileState.MODIFIED: "Modified",
    FileState.ADDED:    "Added",
    FileState.REMOVED:  "Removed",
}


class ChangedFilesPanel(QWidget):
    """Lists files that differ between two sides."""

    file_selected = pyqtSignal(str)   # relative path

    def __init__(self, parent=None):
        super().__init__(parent)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(0)

        self._header = QLabel("Changed Files")
        self._header.setProperty("accent", True)
        self._header.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self._header.setContentsMargins(4, 4, 4, 2)
        lay.addWidget(self._header)

        self._list = QListWidget()
        self._list.setFont(QFont("Consolas", 10))
        self._list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self._list.currentItemChanged.connect(self._on_item_changed)
        lay.addWidget(self._list)

        self._diffs: list[FileDiff] = []

    # ── public ────────────────────────────────────────────────────────────

    def load_directories(self, left_dir: str, right_dir: str):
        """Compare two directories and populate the list."""
        self._diffs = diff_directories_changed_only(left_dir, right_dir)
        self._populate()

    def load_file_list(self, diffs: list[FileDiff]):
        """Load a pre-computed list of changed files."""
        self._diffs = [d for d in diffs if d.state != FileState.SAME]
        self._populate()

    def load_paths(self, paths: list[tuple[str, FileState]]):
        """Load from a list of (rel_path, state) tuples."""
        self._diffs = [FileDiff(p, s) for p, s in paths]
        self._populate()

    def clear(self):
        self._diffs = []
        self._list.clear()
        self._header.setText("Changed Files")

    # ── private ───────────────────────────────────────────────────────────

    def _populate(self):
        self._list.clear()
        p = palette()
        colour_map = {
            FileState.MODIFIED: QColor(p["yellow"]),
            FileState.ADDED:    QColor(p["green"]),
            FileState.REMOVED:  QColor(p["red"]),
        }
        for fd in self._diffs:
            icon = _STATE_ICON.get(fd.state, "")
            label = f"{icon}  {fd.rel_path}"
            item = QListWidgetItem(label)
            item.setData(Qt.ItemDataRole.UserRole, fd.rel_path)
            item.setToolTip(f"{_STATE_LABEL.get(fd.state, '')} — {fd.rel_path}")
            c = colour_map.get(fd.state)
            if c:
                item.setForeground(c)
            self._list.addItem(item)

        self._header.setText(f"Changed Files ({len(self._diffs)})")

    def _on_item_changed(self, current, previous):
        if current:
            rel = current.data(Qt.ItemDataRole.UserRole)
            if rel:
                self.file_selected.emit(rel)
