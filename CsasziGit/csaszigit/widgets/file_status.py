"""
File status panel — staged and working-directory files with
stage / unstage buttons, similar to Git Extensions' index view.

Untracked (new) files are shown alongside modified files in the
"Working Directory Changes" section so they are easy to discover
and stage.
"""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTreeWidget, QTreeWidgetItem,
    QPushButton, QLabel, QAbstractItemView, QMenu, QHeaderView,
)
from PyQt6.QtGui import QColor, QFont, QAction
from PyQt6.QtCore import Qt, pyqtSignal

from csaszigit import git_ops
from csaszigit.themes import palette

# Status → display text + colour key
_STATUS_MAP = {
    "M":  ("Modified",  "orange"),
    "A":  ("Added",     "green"),
    "D":  ("Deleted",   "red"),
    "R":  ("Renamed",   "cyan"),
    "C":  ("Copied",    "cyan"),
    "U":  ("Unmerged",  "red"),
    "??": ("New",       "green"),
}

# UserRole offsets for item data
_ROLE_PATH   = Qt.ItemDataRole.UserRole
_ROLE_STAGED = Qt.ItemDataRole.UserRole + 1
_ROLE_STATUS = Qt.ItemDataRole.UserRole + 2   # raw git status code


class FileStatusPanel(QWidget):
    """Shows staged / working-directory files with stage/unstage actions.

    Untracked files are merged into the working-directory tree so
    users can see *all* uncommitted changes in one place.
    """

    file_selected = pyqtSignal(str, bool)  # (filepath, is_staged)
    untracked_selected = pyqtSignal(str)   # filepath (new / untracked)
    status_changed = pyqtSignal()          # after stage/unstage

    def __init__(self, parent=None):
        super().__init__(parent)
        self._repo = ""

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(2)

        # ── Staged ────────────────────────────────────────────
        hdr1 = QHBoxLayout()
        hdr1.setContentsMargins(4, 4, 4, 0)
        self._staged_label = QLabel("Staged Changes")
        self._staged_label.setProperty("accent", True)
        self._staged_label.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        hdr1.addWidget(self._staged_label)
        hdr1.addStretch()
        btn_unstage_all = QPushButton("Unstage All")
        btn_unstage_all.setFixedHeight(24)
        btn_unstage_all.clicked.connect(self._unstage_all)
        hdr1.addWidget(btn_unstage_all)
        lay.addLayout(hdr1)

        self._staged_tree = self._make_tree()
        self._staged_tree.itemSelectionChanged.connect(self._on_staged_sel)
        self._staged_tree.itemDoubleClicked.connect(self._on_staged_dblclick)
        self._staged_tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self._staged_tree.customContextMenuRequested.connect(
            lambda pos: self._ctx_menu(pos, staged=True)
        )
        lay.addWidget(self._staged_tree, 1)

        # ── Working directory (unstaged + untracked merged) ───
        hdr2 = QHBoxLayout()
        hdr2.setContentsMargins(4, 4, 4, 0)
        self._unstaged_label = QLabel("Working Directory Changes")
        self._unstaged_label.setProperty("accent", True)
        self._unstaged_label.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        hdr2.addWidget(self._unstaged_label)
        hdr2.addStretch()
        btn_stage_all = QPushButton("Stage All")
        btn_stage_all.setFixedHeight(24)
        btn_stage_all.clicked.connect(self._stage_all)
        hdr2.addWidget(btn_stage_all)
        lay.addLayout(hdr2)

        self._unstaged_tree = self._make_tree()
        self._unstaged_tree.itemSelectionChanged.connect(self._on_unstaged_sel)
        self._unstaged_tree.itemDoubleClicked.connect(self._on_unstaged_dblclick)
        self._unstaged_tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self._unstaged_tree.customContextMenuRequested.connect(
            lambda pos: self._ctx_menu(pos, staged=False)
        )
        lay.addWidget(self._unstaged_tree, 3)

    # -- helpers ---------------------------------------------------------------

    @staticmethod
    def _make_tree() -> QTreeWidget:
        t = QTreeWidget()
        t.setHeaderLabels(["File", "Status"])
        t.setRootIsDecorated(False)
        t.setAlternatingRowColors(True)
        t.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        t.setFont(QFont("Consolas", 10))
        h = t.header()
        h.setStretchLastSection(False)
        h.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        h.setSectionResizeMode(1, QHeaderView.ResizeMode.Fixed)
        t.setColumnWidth(1, 90)
        return t

    def _file_item(self, fs: git_ops.FileStatus) -> QTreeWidgetItem:
        label, color_key = _STATUS_MAP.get(fs.status, (fs.status, "comment"))
        item = QTreeWidgetItem([fs.path, label])
        item.setData(0, _ROLE_PATH, fs.path)
        item.setData(0, _ROLE_STAGED, fs.staged)
        item.setData(0, _ROLE_STATUS, fs.status)
        p = palette()
        item.setForeground(1, QColor(p[color_key]))
        return item

    # -- public API ------------------------------------------------------------

    def load(self, repo: str):
        self._repo = repo
        self.refresh()

    def refresh(self):
        if not self._repo:
            return
        try:
            staged, unstaged, untracked = git_ops.get_status(self._repo)
        except Exception:
            staged, unstaged, untracked = [], [], []

        self._staged_tree.clear()
        for fs in staged:
            self._staged_tree.addTopLevelItem(self._file_item(fs))
        self._staged_label.setText(f"Staged Changes ({len(staged)})")

        # Merge unstaged + untracked into one tree
        self._unstaged_tree.clear()
        for fs in unstaged:
            self._unstaged_tree.addTopLevelItem(self._file_item(fs))
        for fs in untracked:
            self._unstaged_tree.addTopLevelItem(self._file_item(fs))
        total = len(unstaged) + len(untracked)
        self._unstaged_label.setText(f"Working Directory Changes ({total})")

    def staged_count(self) -> int:
        return self._staged_tree.topLevelItemCount()

    # -- internal slots --------------------------------------------------------

    def _on_staged_sel(self):
        items = self._staged_tree.selectedItems()
        if items:
            self.file_selected.emit(items[0].data(0, _ROLE_PATH), True)

    def _on_unstaged_sel(self):
        items = self._unstaged_tree.selectedItems()
        if items:
            status = items[0].data(0, _ROLE_STATUS)
            path = items[0].data(0, _ROLE_PATH)
            if status == "??":
                self.untracked_selected.emit(path)
            else:
                self.file_selected.emit(path, False)

    # -- double-click to stage / unstage ---------------------------------------

    def _on_staged_dblclick(self, item: QTreeWidgetItem, _col: int):
        fp = item.data(0, _ROLE_PATH)
        if fp:
            try:
                git_ops.unstage_file(self._repo, fp)
            except Exception:
                pass
            self.refresh()
            self.status_changed.emit()

    def _on_unstaged_dblclick(self, item: QTreeWidgetItem, _col: int):
        fp = item.data(0, _ROLE_PATH)
        if fp:
            try:
                git_ops.stage_file(self._repo, fp)
            except Exception:
                pass
            self.refresh()
            self.status_changed.emit()

    # -- stage / unstage -------------------------------------------------------

    def _stage_selected(self):
        for item in self._unstaged_tree.selectedItems():
            fp = item.data(0, _ROLE_PATH)
            try:
                git_ops.stage_file(self._repo, fp)
            except Exception:
                pass
        self.refresh()
        self.status_changed.emit()

    def _unstage_selected(self):
        for item in self._staged_tree.selectedItems():
            fp = item.data(0, _ROLE_PATH)
            try:
                git_ops.unstage_file(self._repo, fp)
            except Exception:
                pass
        self.refresh()
        self.status_changed.emit()

    def _stage_all(self):
        try:
            git_ops.stage_all(self._repo)
        except Exception:
            pass
        self.refresh()
        self.status_changed.emit()

    def _unstage_all(self):
        try:
            git_ops.unstage_all(self._repo)
        except Exception:
            pass
        self.refresh()
        self.status_changed.emit()

    # -- context menus ---------------------------------------------------------

    def _ctx_menu(self, pos, staged: bool):
        tree = self._staged_tree if staged else self._unstaged_tree
        item = tree.itemAt(pos)
        if not item:
            return
        menu = QMenu(self)
        if staged:
            act = QAction("Unstage", self)
            act.triggered.connect(self._unstage_selected)
        else:
            act = QAction("Stage", self)
            act.triggered.connect(self._stage_selected)
        menu.addAction(act)
        menu.exec(tree.viewport().mapToGlobal(pos))
