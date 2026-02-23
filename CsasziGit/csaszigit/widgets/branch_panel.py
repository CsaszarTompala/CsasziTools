"""
Branch management panel — tree of local and remote branches with
checkout, create, merge, and delete actions.
"""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTreeWidget, QTreeWidgetItem,
    QPushButton, QLabel, QInputDialog, QMenu, QMessageBox,
    QAbstractItemView, QHeaderView,
)
from PyQt6.QtGui import QColor, QFont, QAction, QIcon
from PyQt6.QtCore import Qt, pyqtSignal

from csaszigit import git_ops
from csaszigit.themes import palette


class BranchPanel(QWidget):
    """Shows local and remote branches with management actions."""

    branch_changed = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._repo = ""

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(2)

        header = QHBoxLayout()
        header.setContentsMargins(4, 4, 4, 0)
        lbl = QLabel("Branches")
        lbl.setProperty("accent", True)
        lbl.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        header.addWidget(lbl)
        header.addStretch()

        btn_new = QPushButton("+ New")
        btn_new.setFixedHeight(24)
        btn_new.clicked.connect(self._create_branch)
        header.addWidget(btn_new)
        lay.addLayout(header)

        self._tree = QTreeWidget()
        self._tree.setHeaderLabels(["Branch", "Last Commit"])
        self._tree.setRootIsDecorated(True)
        self._tree.setAlternatingRowColors(True)
        self._tree.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self._tree.setFont(QFont("Consolas", 10))
        self._tree.itemDoubleClicked.connect(self._on_double_click)
        self._tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self._tree.customContextMenuRequested.connect(self._ctx_menu)
        h = self._tree.header()
        h.setStretchLastSection(True)
        h.setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        lay.addWidget(self._tree)

        # Tags section
        tag_header = QHBoxLayout()
        tag_header.setContentsMargins(4, 4, 4, 0)
        lbl_tags = QLabel("Tags")
        lbl_tags.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        tag_header.addWidget(lbl_tags)
        lay.addLayout(tag_header)

        self._tag_tree = QTreeWidget()
        self._tag_tree.setHeaderLabels(["Tag"])
        self._tag_tree.setRootIsDecorated(False)
        self._tag_tree.setAlternatingRowColors(True)
        self._tag_tree.setFont(QFont("Consolas", 10))
        self._tag_tree.setMaximumHeight(100)
        lay.addWidget(self._tag_tree)

    # -- public ----------------------------------------------------------------

    def load(self, repo: str):
        self._repo = repo
        self.refresh()

    def refresh(self):
        if not self._repo:
            return
        self._tree.clear()
        self._tag_tree.clear()

        try:
            branches = git_ops.get_branches(self._repo)
        except Exception:
            branches = []

        p = palette()
        local_root = QTreeWidgetItem(self._tree, ["Local Branches", ""])
        local_root.setExpanded(True)
        local_root.setFont(0, QFont("Segoe UI", 10, QFont.Weight.Bold))
        remote_root = QTreeWidgetItem(self._tree, ["Remote Branches", ""])
        remote_root.setExpanded(True)
        remote_root.setFont(0, QFont("Segoe UI", 10, QFont.Weight.Bold))

        for b in branches:
            parent = remote_root if b.is_remote else local_root
            item = QTreeWidgetItem(parent, [b.name, b.last_commit[:60]])
            item.setData(0, Qt.ItemDataRole.UserRole, b.name)
            item.setData(0, Qt.ItemDataRole.UserRole + 1, b.is_remote)
            if b.is_current:
                item.setForeground(0, QColor(p["green"]))
                item.setFont(0, QFont("Consolas", 10, QFont.Weight.Bold))
            if b.is_remote:
                item.setForeground(0, QColor(p["comment"]))

        # Tags
        try:
            tags = git_ops.get_tags(self._repo)
        except Exception:
            tags = []
        for t in tags:
            item = QTreeWidgetItem(self._tag_tree, [t])
            item.setForeground(0, QColor(p["yellow"]))

    # -- actions ---------------------------------------------------------------

    def _create_branch(self):
        if not self._repo:
            return
        name, ok = QInputDialog.getText(self, "New Branch", "Branch name:")
        if ok and name.strip():
            try:
                git_ops.create_branch(self._repo, name.strip(), checkout=True)
                self.branch_changed.emit()
            except git_ops.GitError as e:
                QMessageBox.warning(self, "Error", str(e))

    def _on_double_click(self, item, column):
        name = item.data(0, Qt.ItemDataRole.UserRole)
        is_remote = item.data(0, Qt.ItemDataRole.UserRole + 1)
        if name is None:
            return
        if is_remote:
            # Checkout remote as local tracking branch
            local_name = name.split("/", 1)[-1] if "/" in name else name
            try:
                git_ops.checkout_branch(self._repo, local_name)
                self.branch_changed.emit()
            except git_ops.GitError as e:
                QMessageBox.warning(self, "Checkout Error", str(e))
        else:
            try:
                git_ops.checkout_branch(self._repo, name)
                self.branch_changed.emit()
            except git_ops.GitError as e:
                QMessageBox.warning(self, "Checkout Error", str(e))

    def _ctx_menu(self, pos):
        item = self._tree.itemAt(pos)
        if not item:
            return
        name = item.data(0, Qt.ItemDataRole.UserRole)
        is_remote = item.data(0, Qt.ItemDataRole.UserRole + 1)
        if name is None:
            return

        menu = QMenu(self)

        act_checkout = QAction("Checkout", self)
        act_checkout.triggered.connect(lambda: self._checkout(name, is_remote))
        menu.addAction(act_checkout)

        if not is_remote:
            act_merge = QAction("Merge into current…", self)
            act_merge.triggered.connect(lambda: self._merge(name))
            menu.addAction(act_merge)

            menu.addSeparator()
            act_del = QAction("Delete", self)
            act_del.triggered.connect(lambda: self._delete(name))
            menu.addAction(act_del)

        menu.exec(self._tree.viewport().mapToGlobal(pos))

    def _checkout(self, name, is_remote):
        target = name.split("/", 1)[-1] if is_remote and "/" in name else name
        try:
            git_ops.checkout_branch(self._repo, target)
            self.branch_changed.emit()
        except git_ops.GitError as e:
            QMessageBox.warning(self, "Checkout Error", str(e))

    def _merge(self, name):
        reply = QMessageBox.question(
            self, "Merge",
            f"Merge '{name}' into the current branch?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if reply == QMessageBox.StandardButton.Yes:
            try:
                msg = git_ops.merge_branch(self._repo, name)
                QMessageBox.information(self, "Merge", msg or "Merge complete.")
                self.branch_changed.emit()
            except git_ops.GitError as e:
                QMessageBox.warning(self, "Merge Error", str(e))

    def _delete(self, name):
        reply = QMessageBox.question(
            self, "Delete Branch",
            f"Delete local branch '{name}'?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if reply == QMessageBox.StandardButton.Yes:
            try:
                git_ops.delete_branch(self._repo, name)
                self.branch_changed.emit()
            except git_ops.GitError as e:
                # Offer force delete
                reply2 = QMessageBox.question(
                    self, "Force Delete?",
                    f"Branch not fully merged.\n{e}\n\nForce delete?",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                )
                if reply2 == QMessageBox.StandardButton.Yes:
                    try:
                        git_ops.delete_branch(self._repo, name, force=True)
                        self.branch_changed.emit()
                    except git_ops.GitError as e2:
                        QMessageBox.warning(self, "Error", str(e2))
