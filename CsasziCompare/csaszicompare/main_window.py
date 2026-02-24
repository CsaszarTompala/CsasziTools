"""
CsasziCompare main window — Meld-style comparison tool.

Layout (dockable):
┌──────────────┬──────────────────────────────────────┐
│ File Explorer│  Diff View / Merge View              │
├──────────────┤                                      │
│ Changed Files│                                      │
└──────────────┴──────────────────────────────────────┘

Modes:
- Compare (2-way): side-by-side diff of two files or directory trees.
- Merge   (3-way): three-column merge / rebase conflict resolution.
"""

from __future__ import annotations

import os
import sys
import tempfile
import subprocess
from pathlib import Path

from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QMenuBar, QMenu, QToolBar, QStatusBar, QFileDialog,
    QMessageBox, QDockWidget, QApplication, QLabel,
    QSplitter, QDialog, QComboBox, QPushButton,
)
from PyQt6.QtGui import QAction, QFont, QKeySequence
from PyQt6.QtCore import Qt, QTimer

from csaszicompare.themes import (
    palette, apply_theme, THEME_NAMES, THEME_LABELS,
)
from csaszicompare.diff_engine import (
    diff_files, diff_directories_changed_only, FileDiff, FileState,
)
from csaszicompare.widgets.file_tree import FileTree
from csaszicompare.widgets.changed_files import ChangedFilesPanel
from csaszicompare.widgets.diff_view import TwoWayDiffView
from csaszicompare.widgets.merge_view import ThreeWayMergeView


class MainWindow(QMainWindow):
    """CsasziCompare main window."""

    def __init__(
        self,
        left: str = "",
        right: str = "",
        base: str = "",
        mode: str = "compare",
        repo: str = "",
        commit1: str = "",
        commit2: str = "",
        theme: str = "dracula",
        parent=None,
    ):
        super().__init__(parent)
        self._left = left
        self._right = right
        self._base = base
        self._mode = mode     # "compare" or "merge" or "rebase"
        self._repo = repo
        self._commit1 = commit1
        self._commit2 = commit2
        self._temp_dirs: list[str] = []
        self._git_hash1 = ""    # e.g. "abc12345"
        self._git_hash2 = ""
        self._git_date1 = ""    # e.g. "2025-12-01 14:30"
        self._git_date2 = ""

        self.setWindowTitle("CsasziCompare")
        self.resize(1400, 900)

        self._build_menu()
        self._build_toolbar()
        self._build_panels()
        self._build_statusbar()

        # If launched with CLI args, auto-load
        QTimer.singleShot(100, self._auto_load)

    # =====================================================================
    # Menu bar
    # =====================================================================

    def _build_menu(self):
        mb = self.menuBar()

        # ── File ──────────────────────────────────────────────
        file_menu = mb.addMenu("&File")

        act_cmp_files = QAction("Compare Two &Files…", self)
        act_cmp_files.setShortcut(QKeySequence("Ctrl+O"))
        act_cmp_files.triggered.connect(self._prompt_compare_files)
        file_menu.addAction(act_cmp_files)

        act_cmp_dirs = QAction("Compare Two &Directories…", self)
        act_cmp_dirs.setShortcut(QKeySequence("Ctrl+D"))
        act_cmp_dirs.triggered.connect(self._prompt_compare_dirs)
        file_menu.addAction(act_cmp_dirs)

        file_menu.addSeparator()

        act_merge = QAction("Three-Way &Merge…", self)
        act_merge.setShortcut(QKeySequence("Ctrl+M"))
        act_merge.triggered.connect(self._prompt_merge)
        file_menu.addAction(act_merge)

        file_menu.addSeparator()

        act_quit = QAction("&Quit", self)
        act_quit.setShortcut(QKeySequence("Ctrl+Q"))
        act_quit.triggered.connect(self.close)
        file_menu.addAction(act_quit)

        # ── View ──────────────────────────────────────────────
        view_menu = mb.addMenu("&View")

        act_refresh = QAction("&Refresh Diff", self)
        act_refresh.setShortcut(QKeySequence("F5"))
        act_refresh.triggered.connect(self._refresh_diff)
        view_menu.addAction(act_refresh)

        # ── Tools ─────────────────────────────────────────────
        tools_menu = mb.addMenu("&Tools")

        act_settings = QAction("&Settings…", self)
        act_settings.triggered.connect(self._on_settings)
        tools_menu.addAction(act_settings)

        # ── Window (dock toggles) ─────────────────────────────
        self._window_menu = mb.addMenu("&Window")

    # =====================================================================
    # Toolbar
    # =====================================================================

    def _build_toolbar(self):
        tb = QToolBar("Main")
        tb.setMovable(False)
        self.addToolBar(tb)

        tb.addAction("📂 Files").triggered.connect(self._prompt_compare_files)
        tb.addAction("📁 Dirs").triggered.connect(self._prompt_compare_dirs)
        tb.addSeparator()
        tb.addAction("🔀 Merge").triggered.connect(self._prompt_merge)
        tb.addSeparator()

        self._mode_label = QLabel(f"  Mode: {self._mode.title()}")
        self._mode_label.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        tb.addWidget(self._mode_label)

    # =====================================================================
    # Panels
    # =====================================================================

    def _build_panels(self):
        self.setDockNestingEnabled(True)
        _central = QWidget()
        _central.setMaximumSize(0, 0)
        self.setCentralWidget(_central)

        # ── Create widgets ────────────────────────────────────
        self._file_tree = FileTree()
        self._changed_files = ChangedFilesPanel()
        self._diff_view = TwoWayDiffView()
        self._merge_view = ThreeWayMergeView()

        # Connect signals
        self._file_tree.file_selected.connect(self._on_file_tree_select)
        self._changed_files.file_selected.connect(self._on_changed_file_select)
        self._merge_view.merge_saved.connect(self._on_merge_saved)

        # ── Dock widgets ──────────────────────────────────────
        self._tree_dock = self._make_dock("File Explorer", self._file_tree)
        self._files_dock = self._make_dock("Changed Files", self._changed_files)
        self._diff_dock = self._make_dock("Diff View", self._diff_view)
        self._merge_dock = self._make_dock("Merge View", self._merge_view)

        # Left column: file tree on top, changed files below
        self.addDockWidget(Qt.DockWidgetArea.LeftDockWidgetArea, self._tree_dock)
        self.splitDockWidget(self._tree_dock, self._files_dock, Qt.Orientation.Vertical)

        # Right column: diff view and merge view as tabs
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, self._diff_dock)
        self.tabifyDockWidget(self._diff_dock, self._merge_dock)
        self._diff_dock.raise_()

        # Sizes
        self.resizeDocks(
            [self._tree_dock, self._diff_dock],
            [320, 1080],
            Qt.Orientation.Horizontal,
        )
        self.resizeDocks(
            [self._tree_dock, self._files_dock],
            [400, 500],
            Qt.Orientation.Vertical,
        )

        # Show/hide merge dock based on mode
        if self._mode == "compare":
            self._merge_dock.setVisible(False)
        else:
            self._diff_dock.setVisible(False)
            self._merge_dock.raise_()

        # Window menu
        self._populate_window_menu()

    def _build_statusbar(self):
        sb = QStatusBar()
        self.setStatusBar(sb)
        self._status_label = QLabel("Ready")
        self._status_label.setFont(QFont("Consolas", 10))
        sb.addWidget(self._status_label, 1)

    def _make_dock(self, title: str, widget: QWidget) -> QDockWidget:
        dock = QDockWidget(title, self)
        dock.setObjectName(title)
        dock.setWidget(widget)
        dock.setAllowedAreas(Qt.DockWidgetArea.AllDockWidgetAreas)
        dock.setFeatures(
            QDockWidget.DockWidgetFeature.DockWidgetMovable
            | QDockWidget.DockWidgetFeature.DockWidgetClosable
            | QDockWidget.DockWidgetFeature.DockWidgetFloatable
        )
        return dock

    def _populate_window_menu(self):
        for dock in (
            self._tree_dock, self._files_dock,
            self._diff_dock, self._merge_dock,
        ):
            self._window_menu.addAction(dock.toggleViewAction())

    # =====================================================================
    # Theme & Settings
    # =====================================================================

    def _switch_theme(self, name: str):
        apply_theme(QApplication.instance(), name)
        self._current_theme = name
        # Re-render diff view to pick up new colours
        self._diff_view.rerender()
        self._status_label.setText(f"Theme: {THEME_LABELS.get(name, name)}")

    def mark_theme(self, name: str):
        """Record the initial theme."""
        self._current_theme = name

    def _on_settings(self):
        dlg = _SettingsDialog(getattr(self, "_current_theme", "dracula"), self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            new_theme = dlg.selected_theme()
            if new_theme != getattr(self, "_current_theme", ""):
                self._switch_theme(new_theme)

    def _refresh_diff(self):
        """Re-render the current diff (e.g. after theme change)."""
        self._diff_view.rerender()

    # =====================================================================
    # File / directory prompts
    # =====================================================================

    def _prompt_compare_files(self):
        left, _ = QFileDialog.getOpenFileName(self, "Select Left File")
        if not left:
            return
        right, _ = QFileDialog.getOpenFileName(self, "Select Right File")
        if not right:
            return
        self._compare_files(left, right)

    def _prompt_compare_dirs(self):
        left = QFileDialog.getExistingDirectory(self, "Select Left Directory")
        if not left:
            return
        right = QFileDialog.getExistingDirectory(self, "Select Right Directory")
        if not right:
            return
        self._compare_dirs(left, right)

    def _prompt_merge(self):
        base, _ = QFileDialog.getOpenFileName(self, "Select Base (ancestor) File")
        if not base:
            return
        left, _ = QFileDialog.getOpenFileName(self, "Select Left (ours) File")
        if not left:
            return
        right, _ = QFileDialog.getOpenFileName(self, "Select Right (theirs) File")
        if not right:
            return
        self._do_merge(base, left, right, mode="merge")

    # =====================================================================
    # Compare logic
    # =====================================================================

    def _compare_files(self, left: str, right: str):
        self._left = left
        self._right = right
        self._mode = "compare"
        self._mode_label.setText("  Mode: Compare")
        self._diff_dock.setVisible(True)
        self._diff_dock.raise_()

        self._diff_view.show_files(left, right)
        self._changed_files.clear()
        self._status_label.setText(f"Comparing: {os.path.basename(left)} ↔ {os.path.basename(right)}")

    def _compare_dirs(self, left: str, right: str):
        self._left = left
        self._right = right
        self._mode = "compare"
        self._mode_label.setText("  Mode: Compare")
        self._diff_dock.setVisible(True)
        self._diff_dock.raise_()

        self._file_tree.set_root(left)
        diffs = diff_directories_changed_only(left, right)
        self._changed_files.load_file_list(diffs)
        self._diff_view.clear()
        self._status_label.setText(
            f"Comparing dirs: {os.path.basename(left)} ↔ {os.path.basename(right)}  "
            f"({len(diffs)} changed files)"
        )

    def _do_merge(self, base: str, left: str, right: str, mode: str = "merge"):
        self._base = base
        self._left = left
        self._right = right
        self._mode = mode
        self._mode_label.setText(f"  Mode: {mode.title()}")
        self._merge_dock.setVisible(True)
        self._merge_dock.raise_()

        self._merge_view.load_files(base, left, right, mode=mode)
        self._status_label.setText(
            f"{mode.title()}: {os.path.basename(left)} ← {os.path.basename(base)} → {os.path.basename(right)}"
        )

    # =====================================================================
    # Git commit comparison (launched from CsasziGit)
    # =====================================================================

    def _auto_load(self):
        """Auto-load based on CLI arguments."""
        if self._repo and self._commit1 and self._commit2:
            self._compare_git_commits()
        elif self._left and self._right and self._base:
            self._do_merge(self._base, self._left, self._right, mode=self._mode)
        elif self._left and self._right:
            if os.path.isdir(self._left) and os.path.isdir(self._right):
                self._compare_dirs(self._left, self._right)
            else:
                self._compare_files(self._left, self._right)

    @staticmethod
    def _git_commit_date(repo: str, commit_hash: str) -> str:
        """Return the commit date as a short string."""
        r = subprocess.run(
            ["git", "log", "-1", "--format=%ci", commit_hash],
            cwd=repo, capture_output=True, text=True,
            encoding="utf-8", errors="replace",
        )
        if r.returncode == 0:
            # "2025-12-01 14:30:00 +0100" → "2025-12-01 14:30"
            return r.stdout.strip()[:16]
        return ""

    def _compare_git_commits(self):
        """Export two commits to temp dirs and compare them.

        The *older* commit is placed on the left (difflib 'old') and the
        *newer* commit on the right (difflib 'new') so that:
        - DELETE  → red  on the left  = content removed in the newer commit
        - INSERT  → green on the right = content added  in the newer commit
        """
        # Fetch dates before exporting so we can sort chronologically
        date1 = self._git_commit_date(self._repo, self._commit1)
        date2 = self._git_commit_date(self._repo, self._commit2)

        # Ensure older commit is on the left (base/old), newer on the right
        if date1 and date2 and date1 > date2:
            self._commit1, self._commit2 = self._commit2, self._commit1
            date1, date2 = date2, date1

        try:
            left_dir = self._export_commit_tree(self._repo, self._commit1)
            right_dir = self._export_commit_tree(self._repo, self._commit2)
            self._temp_dirs.extend([left_dir, right_dir])
        except Exception as e:
            QMessageBox.warning(self, "Git Error", str(e))
            return

        short1 = self._commit1[:8]
        short2 = self._commit2[:8]
        self._git_hash1 = short1
        self._git_hash2 = short2
        self._git_date1 = date1
        self._git_date2 = date2
        self.setWindowTitle(f"CsasziCompare — {short1} (older) ↔ {short2} (newer)")

        self._file_tree.set_root(left_dir)
        diffs = diff_directories_changed_only(left_dir, right_dir)
        self._changed_files.load_file_list(diffs)

        self._diff_view.clear()
        self._diff_dock.setVisible(True)
        self._diff_dock.raise_()
        self._mode = "compare"
        self._mode_label.setText("  Mode: Compare (Git)")

        self._left = left_dir
        self._right = right_dir

        self._status_label.setText(
            f"Comparing commits: {short1} ↔ {short2}  ({len(diffs)} changed files)"
        )

        # Auto-select first changed file
        if diffs:
            self._on_changed_file_select(diffs[0].rel_path)

    @staticmethod
    def _export_commit_tree(repo: str, commit_hash: str) -> str:
        """Export the file tree at a given commit to a temporary directory."""
        tmpdir = tempfile.mkdtemp(prefix=f"csaszi_cmp_{commit_hash[:8]}_")
        # List files at the commit
        r = subprocess.run(
            ["git", "ls-tree", "-r", "--name-only", commit_hash],
            cwd=repo, capture_output=True, text=True,
            encoding="utf-8", errors="replace",
        )
        if r.returncode != 0:
            raise RuntimeError(f"git ls-tree failed: {r.stderr.strip()}")

        for rel_path in r.stdout.strip().splitlines():
            if not rel_path:
                continue
            out_file = os.path.join(tmpdir, rel_path.replace("/", os.sep))
            os.makedirs(os.path.dirname(out_file), exist_ok=True)
            r2 = subprocess.run(
                ["git", "show", f"{commit_hash}:{rel_path}"],
                cwd=repo, capture_output=True,
            )
            if r2.returncode == 0:
                with open(out_file, "wb") as f:
                    f.write(r2.stdout)
        return tmpdir

    # =====================================================================
    # Signal handlers
    # =====================================================================

    def _on_file_tree_select(self, abs_path: str):
        """User clicked a file in the file explorer."""
        if not self._left or not self._right:
            return
        # Find relative path from left dir
        try:
            rel = os.path.relpath(abs_path, self._left)
        except ValueError:
            rel = os.path.basename(abs_path)
        self._on_changed_file_select(rel)

    def _on_changed_file_select(self, rel_path: str):
        """User clicked a changed file — show its diff."""
        if not self._left or not self._right:
            return
        left_file = os.path.join(self._left, rel_path.replace("/", os.sep))
        right_file = os.path.join(self._right, rel_path.replace("/", os.sep))

        # Build titles — include commit info when in git compare mode
        # Each title has up to 3 lines: hash, date, filepath
        if self._git_hash1 and self._git_hash2:
            left_title = f"{self._git_hash1}\n{self._git_date1}\n{rel_path}"
            right_title = f"{self._git_hash2}\n{self._git_date2}\n{rel_path}"
        else:
            left_title = f"{os.path.basename(self._left)}\n{rel_path}"
            right_title = f"{os.path.basename(self._right)}\n{rel_path}"

        self._diff_view.show_files(
            left_file, right_file,
            left_title=left_title,
            right_title=right_title,
        )
        self._status_label.setText(f"Viewing: {rel_path}")

    def _on_merge_saved(self, text: str):
        """User clicked Save Result in the merge view."""
        path, _ = QFileDialog.getSaveFileName(self, "Save Merged Result")
        if path:
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            self._status_label.setText(f"Saved: {path}")

    # =====================================================================
    # Cleanup
    # =====================================================================

    def closeEvent(self, event):
        import shutil
        for d in self._temp_dirs:
            try:
                shutil.rmtree(d, ignore_errors=True)
            except Exception:
                pass
        super().closeEvent(event)


# =========================================================================
# Settings dialog
# =========================================================================

class _SettingsDialog(QDialog):
    """Simple settings dialog — currently just theme selection."""

    def __init__(self, current_theme: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Settings")
        self.setMinimumWidth(350)

        lay = QVBoxLayout(self)

        lbl = QLabel("Theme")
        lbl.setProperty("accent", True)
        lbl.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        lay.addWidget(lbl)

        self._combo = QComboBox()
        self._combo.setFont(QFont("Segoe UI", 11))
        for name in THEME_NAMES:
            self._combo.addItem(THEME_LABELS.get(name, name), name)
        idx = THEME_NAMES.index(current_theme) if current_theme in THEME_NAMES else 0
        self._combo.setCurrentIndex(idx)

        # Live preview
        self._combo.currentIndexChanged.connect(self._on_preview)

        lay.addWidget(self._combo)
        lay.addSpacing(12)

        btn_row = QHBoxLayout()
        btn_row.addStretch()

        btn_cancel = QPushButton("Cancel")
        btn_cancel.clicked.connect(self.reject)
        btn_row.addWidget(btn_cancel)

        btn_ok = QPushButton("OK")
        btn_ok.setProperty("accent", True)
        btn_ok.clicked.connect(self.accept)
        btn_row.addWidget(btn_ok)

        lay.addLayout(btn_row)

        self._original_theme = current_theme

    def selected_theme(self) -> str:
        return self._combo.currentData()

    def _on_preview(self):
        name = self._combo.currentData()
        if name:
            apply_theme(QApplication.instance(), name)

    def reject(self):
        # Revert to original theme on cancel
        apply_theme(QApplication.instance(), self._original_theme)
        super().reject()
