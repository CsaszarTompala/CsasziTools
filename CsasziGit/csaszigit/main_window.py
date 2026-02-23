"""
Main window — assembles every panel into a Git-Extensions-style layout.

Layout
------
┌─────────────────────────────────────────────────────────────────┐
│  Menu bar   (File, View, Git, Tools, Help)                     │
├─────────────────────────────────────────────────────────────────┤
│  Toolbar    (Open, Refresh, Commit, Push, Pull, Fetch, Stash)  │
├──────────┬──────────────────────────────────────────────────────┤
│ Branches │  Commit history (graph + table)                     │
│          ├──────────────────────────┬───────────────────────────┤
│          │  File status (staged /   │  Diff viewer              │
│          │  unstaged / untracked)   │                           │
├──────────┴──────────────────────────┴───────────────────────────┤
│ Status bar  (branch, repo path)                                │
└─────────────────────────────────────────────────────────────────┘
AI Assistant is a togglable dock widget on the right.
"""

import os
from PyQt6.QtWidgets import (
    QMainWindow, QSplitter, QWidget, QVBoxLayout, QHBoxLayout,
    QMenuBar, QMenu, QToolBar, QStatusBar, QFileDialog,
    QMessageBox, QInputDialog, QLabel, QDockWidget, QApplication,
    QTextEdit, QPlainTextEdit, QPushButton, QDialog,
)
from PyQt6.QtGui import QAction, QFont, QKeySequence
from PyQt6.QtCore import Qt, QTimer

from csaszigit import git_ops
from csaszigit.settings import SettingsManager
from csaszigit.themes import palette, apply_theme

from csaszigit.widgets.commit_log import CommitLogPanel
from csaszigit.widgets.file_status import FileStatusPanel
from csaszigit.widgets.diff_viewer import DiffViewer
from csaszigit.widgets.branch_panel import BranchPanel
from csaszigit.widgets.ai_assistant import AiAssistantPanel
from csaszigit.widgets.settings_dialog import SettingsDialog


class MainWindow(QMainWindow):
    """CsasziGit main window."""

    def __init__(self, settings: SettingsManager, parent=None):
        super().__init__(parent)
        self._settings = settings
        self._repo = ""

        self.setWindowTitle("CsasziGit")
        self.resize(1280, 800)

        # Restore geometry
        geo = settings.window_geometry
        if geo:
            self.restoreGeometry(geo)
        state = settings.window_state
        if state:
            self.restoreState(state)

        # ── Build UI ──────────────────────────────────────────
        self._build_menu()
        self._build_toolbar()
        self._build_panels()
        self._build_statusbar()
        self._build_ai_dock()

        # ── Open last repo or prompt ──────────────────────────
        last = settings.last_repo
        if last and git_ops.is_git_repo(last):
            self._open_repo(last)
        else:
            QTimer.singleShot(200, self._prompt_open_repo)

    # =====================================================================
    # Menu bar
    # =====================================================================

    def _build_menu(self):
        mb = self.menuBar()

        # ── File ──────────────────────────────────────────────
        file_menu = mb.addMenu("&File")

        act_open = QAction("&Open Repository…", self)
        act_open.setShortcut(QKeySequence("Ctrl+O"))
        act_open.triggered.connect(self._prompt_open_repo)
        file_menu.addAction(act_open)

        file_menu.addSeparator()

        act_quit = QAction("&Quit", self)
        act_quit.setShortcut(QKeySequence("Ctrl+Q"))
        act_quit.triggered.connect(self.close)
        file_menu.addAction(act_quit)

        # ── View ──────────────────────────────────────────────
        view_menu = mb.addMenu("&View")

        self._act_ai = QAction("AI Assistant", self)
        self._act_ai.setCheckable(True)
        self._act_ai.setChecked(True)
        self._act_ai.setShortcut(QKeySequence("Ctrl+Shift+A"))
        view_menu.addAction(self._act_ai)

        act_refresh = QAction("&Refresh", self)
        act_refresh.setShortcut(QKeySequence("F5"))
        act_refresh.triggered.connect(self._refresh_all)
        view_menu.addAction(act_refresh)

        # ── Git ───────────────────────────────────────────────
        git_menu = mb.addMenu("&Git")

        act_commit = QAction("&Commit…", self)
        act_commit.setShortcut(QKeySequence("Ctrl+Return"))
        act_commit.triggered.connect(self._on_commit)
        git_menu.addAction(act_commit)

        git_menu.addSeparator()

        act_push = QAction("&Push", self)
        act_push.setShortcut(QKeySequence("Ctrl+Shift+P"))
        act_push.triggered.connect(self._on_push)
        git_menu.addAction(act_push)

        act_pull = QAction("Pu&ll", self)
        act_pull.setShortcut(QKeySequence("Ctrl+Shift+L"))
        act_pull.triggered.connect(self._on_pull)
        git_menu.addAction(act_pull)

        act_fetch = QAction("&Fetch", self)
        act_fetch.setShortcut(QKeySequence("Ctrl+Shift+F"))
        act_fetch.triggered.connect(self._on_fetch)
        git_menu.addAction(act_fetch)

        git_menu.addSeparator()

        act_stash = QAction("&Stash", self)
        act_stash.triggered.connect(self._on_stash)
        git_menu.addAction(act_stash)

        act_stash_pop = QAction("Stash P&op", self)
        act_stash_pop.triggered.connect(self._on_stash_pop)
        git_menu.addAction(act_stash_pop)

        # ── Tools ─────────────────────────────────────────────
        tools_menu = mb.addMenu("&Tools")

        act_settings = QAction("&Settings…", self)
        act_settings.triggered.connect(self._on_settings)
        tools_menu.addAction(act_settings)

        # ── Help ──────────────────────────────────────────────
        help_menu = mb.addMenu("&Help")
        act_about = QAction("&About CsasziGit", self)
        act_about.triggered.connect(self._on_about)
        help_menu.addAction(act_about)

    # =====================================================================
    # Toolbar
    # =====================================================================

    def _build_toolbar(self):
        tb = QToolBar("Main")
        tb.setMovable(False)
        tb.setIconSize(tb.iconSize())
        self.addToolBar(tb)

        self._tb_open = tb.addAction("📂 Open")
        self._tb_open.triggered.connect(self._prompt_open_repo)

        tb.addSeparator()

        self._tb_refresh = tb.addAction("🔄 Refresh")
        self._tb_refresh.triggered.connect(self._refresh_all)

        tb.addSeparator()

        self._tb_commit = tb.addAction("✓  Commit")
        self._tb_commit.triggered.connect(self._on_commit)

        self._tb_push = tb.addAction("⬆  Push")
        self._tb_push.triggered.connect(self._on_push)

        self._tb_pull = tb.addAction("⬇  Pull")
        self._tb_pull.triggered.connect(self._on_pull)

        self._tb_fetch = tb.addAction("📥 Fetch")
        self._tb_fetch.triggered.connect(self._on_fetch)

        tb.addSeparator()

        self._tb_stash = tb.addAction("📦 Stash")
        self._tb_stash.triggered.connect(self._on_stash)

        self._tb_stash_pop = tb.addAction("📤 Pop")
        self._tb_stash_pop.triggered.connect(self._on_stash_pop)

    # =====================================================================
    # Panels (central widget)
    # =====================================================================

    def _build_panels(self):
        # Create panels
        self._branch_panel = BranchPanel()
        self._commit_log = CommitLogPanel()
        self._file_status = FileStatusPanel()
        self._diff_viewer = DiffViewer()

        # Connect signals
        self._commit_log.commit_selected.connect(self._on_commit_selected)
        self._file_status.file_selected.connect(self._on_file_selected)
        self._file_status.untracked_selected.connect(self._on_untracked_selected)
        self._file_status.status_changed.connect(self._on_status_changed)
        self._branch_panel.branch_changed.connect(self._refresh_all)

        # Layout using splitters
        # Top-level: horizontal splitter  [branches | main_area]
        main_hsplit = QSplitter(Qt.Orientation.Horizontal)

        # Left: branch panel
        main_hsplit.addWidget(self._branch_panel)

        # Right: vertical splitter [commit log | bottom area]
        right_vsplit = QSplitter(Qt.Orientation.Vertical)

        right_vsplit.addWidget(self._commit_log)

        # Bottom: horizontal splitter [file status | diff viewer]
        bottom_hsplit = QSplitter(Qt.Orientation.Horizontal)
        bottom_hsplit.addWidget(self._file_status)
        bottom_hsplit.addWidget(self._diff_viewer)
        bottom_hsplit.setStretchFactor(0, 1)
        bottom_hsplit.setStretchFactor(1, 2)

        right_vsplit.addWidget(bottom_hsplit)
        right_vsplit.setStretchFactor(0, 2)
        right_vsplit.setStretchFactor(1, 1)

        main_hsplit.addWidget(right_vsplit)
        main_hsplit.setStretchFactor(0, 0)
        main_hsplit.setStretchFactor(1, 1)
        main_hsplit.setSizes([220, 1060])

        self.setCentralWidget(main_hsplit)

    # =====================================================================
    # Status bar
    # =====================================================================

    def _build_statusbar(self):
        sb = QStatusBar()
        self.setStatusBar(sb)
        self._status_branch = QLabel("No repo")
        self._status_branch.setFont(QFont("Consolas", 10))
        sb.addWidget(self._status_branch, 1)
        self._status_path = QLabel("")
        self._status_path.setFont(QFont("Consolas", 10))
        sb.addPermanentWidget(self._status_path)

    # =====================================================================
    # AI dock
    # =====================================================================

    def _build_ai_dock(self):
        self._ai_panel = AiAssistantPanel()
        self._ai_panel.commands_executed.connect(self._refresh_all)

        dock = QDockWidget("AI Assistant", self)
        dock.setWidget(self._ai_panel)
        dock.setAllowedAreas(
            Qt.DockWidgetArea.RightDockWidgetArea | Qt.DockWidgetArea.BottomDockWidgetArea
        )
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, dock)
        dock.setMinimumWidth(320)

        # Wire toggle
        self._act_ai.toggled.connect(dock.setVisible)
        dock.visibilityChanged.connect(self._act_ai.setChecked)

        # Configure from settings
        self._ai_panel.configure(self._settings.gpt_api_key, self._settings.gpt_model)

    # =====================================================================
    # Repo operations
    # =====================================================================

    def _prompt_open_repo(self):
        start_dir = self._repo or os.path.expanduser("~")
        path = QFileDialog.getExistingDirectory(
            self, "Open Git Repository", start_dir,
        )
        if path:
            if git_ops.is_git_repo(path):
                self._open_repo(path)
            else:
                QMessageBox.warning(
                    self, "Not a Git Repo",
                    f"{path}\n\nis not inside a Git repository.",
                )

    def _open_repo(self, path: str):
        try:
            root = git_ops.get_repo_root(path)
        except Exception:
            root = path
        self._repo = root
        self._settings.last_repo = root

        self.setWindowTitle(f"CsasziGit — {os.path.basename(root)}")
        self._ai_panel.set_repo(root)

        self._refresh_all()

    def _refresh_all(self):
        if not self._repo:
            return
        # Branch label
        try:
            branch = git_ops.get_current_branch(self._repo)
        except Exception:
            branch = "?"
        self._status_branch.setText(f"  Branch: {branch}")
        self._status_path.setText(f"{self._repo}  ")

        self._branch_panel.load(self._repo)
        self._commit_log.load(self._repo)
        self._file_status.load(self._repo)
        self._diff_viewer.clear()

    # =====================================================================
    # Signal handlers
    # =====================================================================

    def _on_commit_selected(self, commit_hash: str):
        if not self._repo:
            return
        try:
            diff = git_ops.get_commit_diff(self._repo, commit_hash)
            self._diff_viewer.show_diff(diff, title=f"Commit {commit_hash[:8]}")
        except Exception as e:
            self._diff_viewer.show_diff(str(e), title="Error")

    def _on_file_selected(self, filepath: str, is_staged: bool):
        if not self._repo:
            return
        try:
            diff = git_ops.get_diff(self._repo, filepath, staged=is_staged)
            if not diff.strip():
                diff = "(no diff available)"
            self._diff_viewer.show_diff(diff, title=filepath)
        except Exception as e:
            self._diff_viewer.show_diff(str(e), title="Error")

    def _on_untracked_selected(self, filepath: str):
        if not self._repo:
            return
        diff = git_ops.get_diff_for_untracked(self._repo, filepath)
        self._diff_viewer.show_diff(diff, title=f"(new) {filepath}")

    def _on_status_changed(self):
        """After staging/unstaging refresh the file list but not the whole log."""
        self._file_status.refresh()

    # =====================================================================
    # Git actions
    # =====================================================================

    def _on_commit(self):
        if not self._repo:
            return
        if self._file_status.staged_count() == 0:
            QMessageBox.information(
                self, "Nothing Staged",
                "Stage some files first before committing.",
            )
            return
        self._show_commit_dialog()

    def _show_commit_dialog(self):
        dlg = _CommitDialog(self._repo, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self._refresh_all()

    def _on_push(self):
        if not self._repo:
            return
        try:
            msg = git_ops.push(self._repo)
            QMessageBox.information(self, "Push", msg or "Push successful.")
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Push Error", str(e))
        self._refresh_all()

    def _on_pull(self):
        if not self._repo:
            return
        try:
            msg = git_ops.pull(self._repo)
            QMessageBox.information(self, "Pull", msg or "Pull successful.")
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Pull Error", str(e))
        self._refresh_all()

    def _on_fetch(self):
        if not self._repo:
            return
        try:
            msg = git_ops.fetch_all(self._repo)
            QMessageBox.information(self, "Fetch", msg or "Fetch successful.")
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Fetch Error", str(e))
        self._refresh_all()

    def _on_stash(self):
        if not self._repo:
            return
        msg, ok = QInputDialog.getText(self, "Stash", "Stash message (optional):")
        if ok:
            try:
                out = git_ops.stash_save(self._repo, msg.strip())
                QMessageBox.information(self, "Stash", out or "Stashed.")
            except git_ops.GitError as e:
                QMessageBox.warning(self, "Stash Error", str(e))
            self._refresh_all()

    def _on_stash_pop(self):
        if not self._repo:
            return
        try:
            out = git_ops.stash_pop(self._repo)
            QMessageBox.information(self, "Stash Pop", out or "Stash applied.")
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Stash Pop Error", str(e))
        self._refresh_all()

    def _on_settings(self):
        dlg = SettingsDialog(self._settings, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self._ai_panel.configure(
                self._settings.gpt_api_key, self._settings.gpt_model,
            )

    def _on_about(self):
        QMessageBox.about(
            self, "About CsasziGit",
            "<h2>CsasziGit</h2>"
            "<p>A Git GUI tool with Dracula theme and AI-assisted commands.</p>"
            "<p>Built with PyQt6.</p>"
        )

    # =====================================================================
    # Window lifecycle
    # =====================================================================

    def closeEvent(self, event):
        self._settings.window_geometry = self.saveGeometry()
        self._settings.window_state = self.saveState()
        super().closeEvent(event)


# =========================================================================
# Commit dialog (inline helper)
# =========================================================================

class _CommitDialog(QDialog):
    """Simple commit dialog with a message editor."""

    def __init__(self, repo: str, parent=None):
        super().__init__(parent)
        self._repo = repo
        self.setWindowTitle("Commit")
        self.setMinimumSize(520, 300)

        lay = QVBoxLayout(self)

        lbl = QLabel("Commit Message")
        lbl.setProperty("accent", True)
        lbl.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        lay.addWidget(lbl)

        self._msg_edit = QTextEdit()
        self._msg_edit.setFont(QFont("Consolas", 11))
        self._msg_edit.setPlaceholderText("Enter commit message…")
        lay.addWidget(self._msg_edit)

        btn_row = QHBoxLayout()
        btn_row.addStretch()

        btn_cancel = QPushButton("Cancel")
        btn_cancel.clicked.connect(self.reject)
        btn_row.addWidget(btn_cancel)

        btn_commit = QPushButton("Commit")
        btn_commit.setProperty("accent", True)
        btn_commit.clicked.connect(self._do_commit)
        btn_row.addWidget(btn_commit)

        lay.addLayout(btn_row)

        self._msg_edit.setFocus()

    def _do_commit(self):
        msg = self._msg_edit.toPlainText().strip()
        if not msg:
            QMessageBox.warning(self, "Empty Message", "Please enter a commit message.")
            return
        try:
            git_ops.commit(self._repo, msg)
            self.accept()
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Commit Error", str(e))
