"""
File tree — filesystem browser widget for navigating directory structures.

Shows a standard tree view of files/folders. Clicking a file emits
``file_selected`` with the absolute path.

Features:
- Editable path bar with clickable breadcrumb segments.
- Back-up (parent directory) button.
"""

import os
from pathlib import Path

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTreeView, QLabel,
    QHeaderView, QLineEdit, QPushButton,
)
from PyQt6.QtGui import QFont, QFileSystemModel
from PyQt6.QtCore import Qt, pyqtSignal


class _BreadcrumbBar(QWidget):
    """Clickable path breadcrumb.  Each segment is a clickable label."""

    segment_clicked = pyqtSignal(str)   # absolute path up to that segment

    def __init__(self, parent=None):
        super().__init__(parent)
        self._layout = QHBoxLayout(self)
        self._layout.setContentsMargins(0, 0, 0, 0)
        self._layout.setSpacing(0)
        self._labels: list[QLabel] = []

    def set_path(self, path: str):
        # Clear existing
        for lbl in self._labels:
            self._layout.removeWidget(lbl)
            lbl.deleteLater()
        self._labels.clear()

        parts = Path(path).parts  # e.g. ('C:\\', 'Users', 'foo')
        for i, part in enumerate(parts):
            seg_path = str(Path(*parts[:i + 1]))
            # Separator
            if i > 0:
                sep = QLabel(" › ")
                sep.setFont(QFont("Consolas", 10))
                sep.setStyleSheet("color: grey; padding: 0;")
                sep.setFixedWidth(sep.fontMetrics().horizontalAdvance(" › "))
                self._layout.addWidget(sep)
                self._labels.append(sep)
            # Segment
            lbl = QLabel(part.rstrip(os.sep + "/"))
            lbl.setFont(QFont("Consolas", 10))
            lbl.setCursor(Qt.CursorShape.PointingHandCursor)
            lbl.setStyleSheet(
                "QLabel { padding: 1px 2px; border-radius: 2px; }"
                "QLabel:hover { background: rgba(255,255,255,0.1); }"
            )
            lbl.mousePressEvent = lambda e, p=seg_path: self.segment_clicked.emit(p)
            self._layout.addWidget(lbl)
            self._labels.append(lbl)

        self._layout.addStretch()


class FileTree(QWidget):
    """Filesystem explorer panel with path bar and parent navigation."""

    file_selected = pyqtSignal(str)   # absolute path

    def __init__(self, parent=None):
        super().__init__(parent)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(2)

        self._header = QLabel("File Explorer")
        self._header.setProperty("accent", True)
        self._header.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self._header.setContentsMargins(4, 4, 4, 2)
        lay.addWidget(self._header)

        # ── Path edit bar ─────────────────────────────────────
        path_row = QHBoxLayout()
        path_row.setContentsMargins(4, 0, 4, 0)
        path_row.setSpacing(2)

        self._path_edit = QLineEdit()
        self._path_edit.setFont(QFont("Consolas", 10))
        self._path_edit.setPlaceholderText("Enter path…")
        self._path_edit.returnPressed.connect(self._on_path_enter)
        path_row.addWidget(self._path_edit, 1)
        lay.addLayout(path_row)

        # ── Breadcrumb bar ────────────────────────────────────
        self._breadcrumb = _BreadcrumbBar()
        self._breadcrumb.segment_clicked.connect(self._navigate_to)
        lay.addWidget(self._breadcrumb)

        # ── Back-up button ────────────────────────────────────
        btn_row = QHBoxLayout()
        btn_row.setContentsMargins(4, 0, 4, 0)
        self._btn_up = QPushButton("⬆ Parent Directory")
        self._btn_up.setFont(QFont("Segoe UI", 10))
        self._btn_up.clicked.connect(self._go_parent)
        btn_row.addWidget(self._btn_up)
        btn_row.addStretch()
        lay.addLayout(btn_row)

        # ── Tree view ─────────────────────────────────────────
        self._model = QFileSystemModel()
        self._model.setReadOnly(True)

        self._view = QTreeView()
        self._view.setModel(self._model)
        self._view.setAnimated(False)
        self._view.setIndentation(16)
        self._view.setSortingEnabled(True)
        self._view.setFont(QFont("Consolas", 10))
        self._view.header().setSectionResizeMode(
            0, QHeaderView.ResizeMode.Stretch,
        )
        # Hide size, type, date modified columns by default
        self._view.setColumnHidden(1, True)
        self._view.setColumnHidden(2, True)
        self._view.setColumnHidden(3, True)

        self._view.clicked.connect(self._on_click)
        self._view.doubleClicked.connect(self._on_double_click)
        lay.addWidget(self._view)

        self._root = ""

    def set_root(self, path: str):
        """Set the root directory shown in the tree."""
        if not path or not os.path.isdir(path):
            return
        self._root = path
        idx = self._model.setRootPath(path)
        self._view.setRootIndex(idx)
        self._header.setText(f"File Explorer — {os.path.basename(path)}")
        self._path_edit.setText(path)
        self._breadcrumb.set_path(path)

    def _on_click(self, index):
        path = self._model.filePath(index)
        if path and os.path.isfile(path):
            self.file_selected.emit(path)

    def _on_double_click(self, index):
        """Double-click a directory to navigate into it."""
        path = self._model.filePath(index)
        if path and os.path.isdir(path):
            self._navigate_to(path)

    def _go_parent(self):
        """Navigate to the parent directory."""
        if not self._root:
            return
        parent = str(Path(self._root).parent)
        if parent and os.path.isdir(parent):
            self._navigate_to(parent)

    def _on_path_enter(self):
        """User pressed Enter in the path edit bar."""
        path = self._path_edit.text().strip()
        if path and os.path.isdir(path):
            self._navigate_to(path)

    def _navigate_to(self, path: str):
        """Navigate the tree to *path*."""
        if not os.path.isdir(path):
            return
        self._root = path
        idx = self._model.setRootPath(path)
        self._view.setRootIndex(idx)
        self._header.setText(f"File Explorer — {os.path.basename(path)}")
        self._path_edit.setText(path)
        self._breadcrumb.set_path(path)
