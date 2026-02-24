"""
Main window — fully dockable Git-Extensions-style layout.

Every panel is a QDockWidget that can be dragged next-to, above, or
below any other panel, floated, closed (x), and restored via the
*Window* menu.

Default layout
--------------
┌──────────┬────────────────────────────────┬──────────────┐
│ Branches │  Commit Log                    │ AI Assistant │
│          ├────────────────────────────────┤ / Terminal   │
│          │  Diff Viewer | Diff Only (tabs)│              │
├──────────┴────────────────────────────────┴──────────────┤
│ Status bar  (branch, repo path)                         │
└─────────────────────────────────────────────────────────┘
"""

import os
from PyQt6.QtWidgets import (
    QMainWindow, QSplitter, QWidget, QVBoxLayout, QHBoxLayout,
    QMenuBar, QMenu, QToolBar, QStatusBar, QFileDialog,
    QMessageBox, QInputDialog, QLabel, QDockWidget, QApplication,
    QTextEdit, QPushButton, QDialog,
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
from csaszigit.widgets.git_terminal import GitTerminalPanel
from csaszigit.widgets.folder_browser import FolderBrowserPanel
from csaszigit.widgets.settings_dialog import SettingsDialog


class MainWindow(QMainWindow):
    """CsasziGit main window."""

    def __init__(self, settings: SettingsManager, parent=None):
        super().__init__(parent)
        self._settings = settings
        self._repo = ""

        self.setWindowTitle("CsasziGit")
        self.resize(1280, 800)

        # ── Build UI ──────────────────────────────────────────
        self._build_menu()
        self._build_toolbar()
        self._build_panels()          # creates all dock widgets
        self._build_statusbar()

        # Restore geometry / dock state AFTER panels are created
        geo = settings.window_geometry
        if geo:
            self.restoreGeometry(geo)
        state = settings.window_state
        if state:
            self.restoreState(state)

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

        act_refresh = QAction("&Refresh", self)
        act_refresh.setShortcut(QKeySequence("F5"))
        act_refresh.triggered.connect(self._refresh_all)
        view_menu.addAction(act_refresh)

        # ── Window (populated after panels are built) ─────────
        self._window_menu = mb.addMenu("&Window")

        # ── Git ───────────────────────────────────────────────
        git_menu = mb.addMenu("&Git")

        act_commit = QAction("&Add / Commit…", self)
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

        act_fetch_all = QAction("Fetch &All", self)
        act_fetch_all.setShortcut(QKeySequence("Ctrl+Alt+F"))
        act_fetch_all.triggered.connect(self._on_fetch_all)
        git_menu.addAction(act_fetch_all)

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

        self._tb_commit = tb.addAction("➕ Add/Commit")
        self._tb_commit.triggered.connect(self._on_commit)

        self._tb_push = tb.addAction("⬆  Push")
        self._tb_push.triggered.connect(self._on_push)

        self._tb_pull = tb.addAction("⬇  Pull")
        self._tb_pull.triggered.connect(self._on_pull)

        self._tb_fetch = tb.addAction("📥 Fetch")
        self._tb_fetch.triggered.connect(self._on_fetch)

        self._tb_fetch_all = tb.addAction("📥📥 Fetch All")
        self._tb_fetch_all.triggered.connect(self._on_fetch_all)

        tb.addSeparator()

        self._tb_stash = tb.addAction("📦 Stash")
        self._tb_stash.triggered.connect(self._on_stash)

        self._tb_stash_pop = tb.addAction("📤 Pop")
        self._tb_stash_pop.triggered.connect(self._on_stash_pop)

    # =====================================================================
    # Panels (central widget)
    # =====================================================================

    def _build_panels(self):
        # All content lives in dock widgets; central widget is hidden.
        self.setDockNestingEnabled(True)
        _central = QWidget()
        _central.setMaximumSize(0, 0)
        self.setCentralWidget(_central)

        # ── Create panels ─────────────────────────────────────
        self._branch_panel = BranchPanel()
        self._commit_log = CommitLogPanel()
        self._diff_viewer = DiffViewer()
        self._diff_only_viewer = DiffViewer()

        self._ai_panel = AiAssistantPanel()
        self._ai_panel.commands_executed.connect(self._refresh_all)
        self._terminal_panel = GitTerminalPanel()
        self._terminal_panel.commands_executed.connect(self._refresh_all)
        self._folder_browser = FolderBrowserPanel()

        # Connect signals
        self._commit_log.commit_selected.connect(self._on_commit_selected)
        self._commit_log.compare_requested.connect(self._on_compare_commits)
        self._branch_panel.branch_changed.connect(self._refresh_all)
        self._folder_browser.repo_selected.connect(self._open_repo)

        # ── Wrap each panel in a QDockWidget ──────────────────
        self._branch_dock = self._make_dock("Branches", self._branch_panel)
        self._log_dock = self._make_dock("Commit Log", self._commit_log)
        self._diff_dock = self._make_dock("Diff Viewer", self._diff_viewer)
        self._diff_only_dock = self._make_dock("Diff Only", self._diff_only_viewer)
        self._ai_dock = self._make_dock("AI Assistant", self._ai_panel, min_w=320)
        self._terminal_dock = self._make_dock("Git Terminal", self._terminal_panel, min_w=360)
        self._folder_dock = self._make_dock("Folder Browser", self._folder_browser)

        # ── Default arrangement ───────────────────────────
        # Left column — branches + folder browser below
        self.addDockWidget(Qt.DockWidgetArea.LeftDockWidgetArea, self._branch_dock)

        # Centre column — commit log on top, diff tabs below
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, self._log_dock)
        self.splitDockWidget(self._log_dock, self._diff_dock, Qt.Orientation.Vertical)
        self.tabifyDockWidget(self._diff_dock, self._diff_only_dock)
        self._diff_dock.raise_()  # Diff Viewer tab active by default

        # Left column — folder browser below branches
        self.splitDockWidget(self._branch_dock, self._folder_dock, Qt.Orientation.Vertical)

        # Right column — AI + Terminal as tabs
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, self._ai_dock)
        self.tabifyDockWidget(self._ai_dock, self._terminal_dock)
        self._ai_dock.raise_()

        # Initial proportions (horizontal: branches | main | AI)
        self.resizeDocks(
            [self._branch_dock, self._log_dock, self._ai_dock],
            [220, 700, 360],
            Qt.Orientation.Horizontal,
        )
        self.resizeDocks(
            [self._log_dock, self._diff_dock],
            [500, 280],
            Qt.Orientation.Vertical,
        )

        # ── Populate Window menu ──────────────────────────────
        self._populate_window_menu()

        # Configure AI from settings
        self._ai_panel.configure(self._settings.gpt_api_key, self._settings.gpt_model)

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
    # Dock helpers
    # =====================================================================

    def _make_dock(self, title: str, widget: QWidget, min_w: int = 0) -> QDockWidget:
        """Wrap *widget* in a closable, movable, floatable QDockWidget."""
        dock = QDockWidget(title, self)
        dock.setObjectName(title)          # stable key for saveState
        dock.setWidget(widget)
        dock.setAllowedAreas(Qt.DockWidgetArea.AllDockWidgetAreas)
        dock.setFeatures(
            QDockWidget.DockWidgetFeature.DockWidgetMovable
            | QDockWidget.DockWidgetFeature.DockWidgetClosable
            | QDockWidget.DockWidgetFeature.DockWidgetFloatable
        )
        if min_w:
            dock.setMinimumWidth(min_w)
        return dock

    def _populate_window_menu(self):
        """Fill the *Window* menu with toggle-visibility actions for every dock."""
        shortcuts = {
            "AI Assistant": "Ctrl+Shift+A",
            "Git Terminal": "Ctrl+Shift+T",
        }
        for dock in (
            self._branch_dock,
            self._folder_dock,
            self._log_dock,
            self._diff_dock,
            self._diff_only_dock,
            self._ai_dock,
            self._terminal_dock,
        ):
            act = dock.toggleViewAction()
            sc = shortcuts.get(dock.windowTitle())
            if sc:
                act.setShortcut(QKeySequence(sc))
            self._window_menu.addAction(act)

        self._window_menu.addSeparator()
        act_restore = QAction("Restore &Default Layout", self)
        act_restore.triggered.connect(self._restore_default_layout)
        self._window_menu.addAction(act_restore)

    def _restore_default_layout(self):
        """Show every dock and reset to the default arrangement."""
        for dock in (
            self._branch_dock, self._folder_dock,
            self._log_dock, self._diff_dock,
            self._diff_only_dock, self._ai_dock,
            self._terminal_dock,
        ):
            dock.setVisible(True)
            dock.setFloating(False)

        self.addDockWidget(Qt.DockWidgetArea.LeftDockWidgetArea, self._branch_dock)
        self.splitDockWidget(self._branch_dock, self._folder_dock, Qt.Orientation.Vertical)
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, self._log_dock)
        self.splitDockWidget(self._log_dock, self._diff_dock, Qt.Orientation.Vertical)
        self.tabifyDockWidget(self._diff_dock, self._diff_only_dock)
        self._diff_dock.raise_()
        self.addDockWidget(Qt.DockWidgetArea.RightDockWidgetArea, self._ai_dock)
        self.tabifyDockWidget(self._ai_dock, self._terminal_dock)
        self._ai_dock.raise_()
        self.resizeDocks(
            [self._branch_dock, self._log_dock, self._ai_dock],
            [220, 700, 360], Qt.Orientation.Horizontal,
        )
        self.resizeDocks(
            [self._log_dock, self._diff_dock],
            [500, 280], Qt.Orientation.Vertical,
        )

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
        self._terminal_panel.set_repo(root)

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
        self._diff_viewer.clear()
        self._diff_only_viewer.clear()

    # =====================================================================
    # Signal handlers
    # =====================================================================

    def _on_commit_selected(self, commit_hash: str):
        if not self._repo:
            return
        try:
            diff = git_ops.get_commit_diff(self._repo, commit_hash)
            self._diff_viewer.show_diff(diff, title=f"Commit {commit_hash[:8]}")
            self._diff_only_viewer.show_diff(
                self._filter_diff_only(diff),
                title=f"Diff Only — {commit_hash[:8]}",
            )
        except Exception as e:
            self._diff_viewer.show_diff(str(e), title="Error")
            self._diff_only_viewer.show_diff(str(e), title="Error")

    def _on_compare_commits(self, hash1: str, hash2: str):
        """Launch CsasziCompare to compare two commits."""
        if not self._repo:
            return
        compare_dir = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "..", "CsasziCompare",
        )
        compare_main = os.path.normpath(os.path.join(compare_dir, "main.py"))
        if not os.path.isfile(compare_main):
            QMessageBox.warning(
                self, "CsasziCompare Not Found",
                f"Cannot find CsasziCompare at:\n{compare_main}",
            )
            return

        import subprocess as _sp
        import sys as _sys
        _sp.Popen(
            [_sys.executable, compare_main,
             "--repo", self._repo,
             "--commit1", hash1,
             "--commit2", hash2],
            cwd=compare_dir,
        )
        self.statusBar().showMessage(
            f"Launched CsasziCompare: {hash1[:8]} ↔ {hash2[:8]}", 4000,
        )

    # =====================================================================
    # Git actions
    # =====================================================================

    def _on_commit(self):
        if not self._repo:
            return
        self._show_commit_dialog()

    def _show_commit_dialog(self):
        dlg = _IndexCommitDialog(self._repo, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self._refresh_all()

    def _on_push(self):
        if not self._repo:
            return
        self._run_git_action("Push", lambda: git_ops.push(self._repo), "Push successful.")

    def _on_pull(self):
        if not self._repo:
            return
        self._run_git_action("Pull", lambda: git_ops.pull(self._repo), "Pull successful.")

    def _on_fetch(self):
        if not self._repo:
            return
        self._run_git_action("Fetch", lambda: git_ops.fetch(self._repo), "Fetch successful.")

    def _on_fetch_all(self):
        if not self._repo:
            return
        self._run_git_action(
            "Fetch All", lambda: git_ops.fetch_all(self._repo), "Fetch all successful."
        )

    def _on_stash(self):
        if not self._repo:
            return
        msg, ok = QInputDialog.getText(self, "Stash", "Stash message (optional):")
        if ok:
            self._run_git_action(
                "Stash", lambda: git_ops.stash_save(self._repo, msg.strip()), "Stashed."
            )

    def _on_stash_pop(self):
        if not self._repo:
            return
        self._run_git_action("Stash Pop", lambda: git_ops.stash_pop(self._repo), "Stash applied.")

    def _run_git_action(self, title: str, func, success_fallback: str):
        QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        self.statusBar().showMessage(f"{title} in progress…", 2000)
        try:
            msg = func()
            QMessageBox.information(self, title, msg or success_fallback)
        except git_ops.GitError as e:
            QMessageBox.warning(self, f"{title} Error", str(e))
        finally:
            QApplication.restoreOverrideCursor()
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

    # =====================================================================
    # Diff-only filter
    # =====================================================================

    @staticmethod
    def _filter_diff_only(diff_text: str) -> str:
        """Strip context lines, keeping file headers, hunks, and +/- changes."""
        result: list[str] = []
        for line in diff_text.splitlines():
            if (
                line.startswith("diff --git")
                or line.startswith("@@")
                or line.startswith("+")
                or line.startswith("-")
            ):
                result.append(line)
        return "\n".join(result)


# =========================================================================
# Add/Commit dialog (inline helper)
# =========================================================================

class _IndexCommitDialog(QDialog):
    """Git-Extensions-style add/commit dialog with staging panes and diff."""

    def __init__(self, repo: str, parent=None):
        super().__init__(parent)
        self._repo = repo
        self.setWindowTitle("Add / Commit")
        self.setMinimumSize(1100, 720)

        lay = QVBoxLayout(self)

        content_split = QSplitter(Qt.Orientation.Horizontal)
        content_split.setOpaqueResize(False)

        self._file_status = FileStatusPanel()
        self._file_status.load(repo)
        self._file_status.file_selected.connect(self._on_file_selected)
        self._file_status.untracked_selected.connect(self._on_untracked_selected)
        self._file_status.status_changed.connect(self._on_status_changed)

        self._diff_viewer = DiffViewer()

        content_split.addWidget(self._file_status)
        content_split.addWidget(self._diff_viewer)
        content_split.setStretchFactor(0, 1)
        content_split.setStretchFactor(1, 2)
        content_split.setSizes([420, 680])

        lay.addWidget(content_split, 1)

        self._staged_label = QLabel()
        self._staged_label.setProperty("accent", True)
        self._staged_label.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        lay.addWidget(self._staged_label)

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

        btn_refresh = QPushButton("Refresh")
        btn_refresh.clicked.connect(self._refresh_status)
        btn_row.addWidget(btn_refresh)

        btn_commit = QPushButton("Commit")
        btn_commit.setProperty("accent", True)
        btn_commit.clicked.connect(self._do_commit)
        btn_row.addWidget(btn_commit)

        lay.addLayout(btn_row)

        self._refresh_status()
        self._msg_edit.setFocus()

    def _refresh_status(self):
        self._file_status.refresh()
        self._diff_viewer.clear()
        self._update_staged_label()

    def _update_staged_label(self):
        count = self._file_status.staged_count()
        self._staged_label.setText(f"Staged files: {count}")

    def _on_status_changed(self):
        self._update_staged_label()

    def _on_file_selected(self, filepath: str, is_staged: bool):
        try:
            diff = git_ops.get_diff(self._repo, filepath, staged=is_staged)
            if not diff.strip():
                diff = "(no diff available)"
            self._diff_viewer.show_diff(diff, title=filepath)
        except Exception as e:
            self._diff_viewer.show_diff(str(e), title="Error")

    def _on_untracked_selected(self, filepath: str):
        diff = git_ops.get_diff_for_untracked(self._repo, filepath)
        self._diff_viewer.show_diff(diff, title=f"(new) {filepath}")

    def _do_commit(self):
        if self._file_status.staged_count() == 0:
            QMessageBox.information(
                self,
                "Nothing Staged",
                "Stage some files first before committing.",
            )
            return

        msg = self._msg_edit.toPlainText().strip()
        if not msg:
            QMessageBox.warning(self, "Empty Message", "Please enter a commit message.")
            return
        try:
            git_ops.commit(self._repo, msg)
            self.accept()
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Commit Error", str(e))
