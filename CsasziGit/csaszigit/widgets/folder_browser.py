"""
Folder browser panel — Windows-Explorer-style folder tree.

Shows the filesystem as a collapsible tree starting from a
configurable root (default: ``C:\``).  Clicking a folder checks
whether it is a Git repository and, if so, emits ``repo_selected``
so the main window can open it.  Non-repo folders display a small
status label saying "Not a git repo".
"""

from __future__ import annotations

import os

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTreeView, QLabel,
    QPushButton, QFileDialog, QAbstractItemView, QHeaderView,
    QLineEdit,
)
from PyQt6.QtGui import QFont, QFileSystemModel
from PyQt6.QtCore import Qt, QDir, pyqtSignal, QModelIndex

from csaszigit import git_ops
from csaszigit.themes import palette


class FolderBrowserPanel(QWidget):
    """Dockable folder tree that lets the user pick a Git repo."""

    repo_selected = pyqtSignal(str)  # absolute path of a valid git repo

    def __init__(self, parent=None):
        super().__init__(parent)

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(2)

        # ── Header ────────────────────────────────────────────
        hdr = QHBoxLayout()
        hdr.setContentsMargins(4, 4, 4, 0)

        lbl = QLabel("Folder Browser")
        lbl.setProperty("accent", True)
        lbl.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        hdr.addWidget(lbl)
        hdr.addStretch()

        btn_root = QPushButton("📂 Root…")
        btn_root.setFixedHeight(24)
        btn_root.setToolTip("Choose a different root folder")
        btn_root.clicked.connect(self._change_root)
        hdr.addWidget(btn_root)
        lay.addLayout(hdr)

        # ── Path bar ─────────────────────────────────────────
        self._path_edit = QLineEdit()
        self._path_edit.setFont(QFont("Consolas", 9))
        self._path_edit.setPlaceholderText("Root path…")
        self._path_edit.returnPressed.connect(self._on_path_enter)
        lay.addWidget(self._path_edit)

        # ── Tree view ─────────────────────────────────────────
        self._model = QFileSystemModel()
        self._model.setFilter(QDir.Filter.Dirs | QDir.Filter.NoDotAndDotDot)
        self._model.setRootPath("")

        self._tree = QTreeView()
        self._tree.setModel(self._model)
        self._tree.setFont(QFont("Consolas", 10))
        self._tree.setHeaderHidden(False)
        self._tree.setAnimated(True)
        self._tree.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self._tree.setSortingEnabled(True)

        # Only show the "Name" column
        for col in range(1, self._model.columnCount()):
            self._tree.hideColumn(col)

        header = self._tree.header()
        header.setStretchLastSection(True)
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)

        self._tree.clicked.connect(self._on_clicked)
        self._tree.doubleClicked.connect(self._on_double_clicked)

        lay.addWidget(self._tree, 1)

        # ── Status label ──────────────────────────────────────
        self._status = QLabel("")
        self._status.setFont(QFont("Segoe UI", 9))
        self._status.setContentsMargins(4, 2, 4, 4)
        self._status.setWordWrap(True)
        lay.addWidget(self._status)

        # Default root
        default_root = "C:\\" if os.name == "nt" else os.path.expanduser("~")
        self._set_root(default_root)

    # ── Public API ────────────────────────────────────────────

    def set_root(self, path: str):
        """Set the root folder for the tree."""
        if os.path.isdir(path):
            self._set_root(path)

    # ── Internal ──────────────────────────────────────────────

    def _set_root(self, path: str):
        idx = self._model.setRootPath(path)
        self._tree.setRootIndex(idx)
        self._path_edit.setText(path)
        self._status.setText("")

    def _on_path_enter(self):
        path = self._path_edit.text().strip()
        if os.path.isdir(path):
            self._set_root(path)
        else:
            self._status.setText("⚠ Path does not exist")
            p = palette()
            self._status.setStyleSheet(f"color: {p['orange']};")

    def _change_root(self):
        path = QFileDialog.getExistingDirectory(self, "Choose Root Folder")
        if path:
            self._set_root(path)

    def _on_clicked(self, index: QModelIndex):
        """Single-click: check if it's a git repo and show status."""
        path = self._model.filePath(index)
        if not path or not os.path.isdir(path):
            return

        p = palette()
        if git_ops.is_git_repo(path):
            self._status.setText(f"✔ Git repo: {os.path.basename(path)}")
            self._status.setStyleSheet(f"color: {p['green']};")
        else:
            self._status.setText("✖ Not a git repo")
            self._status.setStyleSheet(f"color: {p['red']};")

    def _on_double_clicked(self, index: QModelIndex):
        """Double-click: open as repo if it's a valid git repo."""
        path = self._model.filePath(index)
        if not path or not os.path.isdir(path):
            return

        p = palette()
        if git_ops.is_git_repo(path):
            self._status.setText(f"✔ Opened: {os.path.basename(path)}")
            self._status.setStyleSheet(f"color: {p['green']};")
            self.repo_selected.emit(path)
        else:
            self._status.setText("✖ Not a git repo — double-click a valid repo to open")
            self._status.setStyleSheet(f"color: {p['red']};")
