"""
Commit history log — shows commit graph, hash, author, date, message.

Uses a custom QStyledItemDelegate for the graph column to draw coloured
lines and dots, similar to Git Extensions.  Dots appear **only** at
branch tips (no visible child) and root commits (no parents).  All other
commits are just the intersection of their lane lines.
"""

from typing import List, Tuple, NamedTuple

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTreeWidget, QTreeWidgetItem,
    QStyledItemDelegate, QStyle, QAbstractItemView, QLabel, QPushButton,
    QHeaderView, QLineEdit, QMenu,
)
from PyQt6.QtGui import QPainter, QColor, QPen, QBrush, QFont, QAction
from PyQt6.QtCore import Qt, QRect, pyqtSignal, QSize, QTimer, QEvent

from csaszigit import git_ops
from csaszigit.themes import palette

# Colours for graph lanes (cycling)
LANE_COLOURS = [
    "#BD93F9", "#50FA7B", "#FF79C6", "#8BE9FD", "#FFB86C",
    "#FF5555", "#F1FA8C", "#6272A4", "#E0AEF0", "#70D6FF",
]

DOT_RADIUS = 5
LANE_WIDTH = 16


# -- data --------------------------------------------------------------------

class GraphRow(NamedTuple):
    """Pre-computed rendering data for one commit row."""
    commit_lane: int
    top_lines: List[Tuple[int, int, int]]   # (from_lane, to_lane, colour_idx)
    bot_lines: List[Tuple[int, int, int]]   # (from_lane, to_lane, colour_idx)
    is_tip: bool       # branch tip — no visible child references this commit
    is_root: bool      # root commit — no parents
    total_lanes: int


# -- delegate ----------------------------------------------------------------

class _GraphDelegate(QStyledItemDelegate):
    """Draws the branch graph in the first column of the commit table."""

    def __init__(self, graph_data_func, parent=None):
        super().__init__(parent)
        self._graph_data = graph_data_func   # callable() -> List[GraphRow]

    def sizeHint(self, option, index):
        base = super().sizeHint(option, index)
        gdata = self._graph_data()
        orig_idx = index.data(Qt.ItemDataRole.UserRole + 1)
        if orig_idx is not None and orig_idx < len(gdata):
            total = gdata[orig_idx].total_lanes
            width = max((total + 1) * LANE_WIDTH, LANE_WIDTH * 2)
        else:
            width = LANE_WIDTH * 2
        return QSize(width, max(base.height(), 24))

    def paint(self, painter: QPainter, option, index):
        # Selection background
        if option.state & QStyle.StateFlag.State_Selected:
            p = palette()
            painter.fillRect(option.rect, QColor(p["selection"]))

        gdata = self._graph_data()
        orig_idx = index.data(Qt.ItemDataRole.UserRole + 1)
        if orig_idx is None or orig_idx >= len(gdata):
            return

        row = gdata[orig_idx]
        rect = option.rect
        mid_y = rect.top() + rect.height() // 2

        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        # --- top-half lines (top of cell → centre) ---
        for from_lane, to_lane, colour_idx in row.top_lines:
            colour = QColor(LANE_COLOURS[colour_idx % len(LANE_COLOURS)])
            painter.setPen(QPen(colour, 2))
            x_from = rect.left() + from_lane * LANE_WIDTH + LANE_WIDTH // 2
            x_to   = rect.left() + to_lane   * LANE_WIDTH + LANE_WIDTH // 2
            painter.drawLine(x_from, rect.top(), x_to, mid_y)

        # --- bottom-half lines (centre → bottom of cell) ---
        for from_lane, to_lane, colour_idx in row.bot_lines:
            colour = QColor(LANE_COLOURS[colour_idx % len(LANE_COLOURS)])
            painter.setPen(QPen(colour, 2))
            x_from = rect.left() + from_lane * LANE_WIDTH + LANE_WIDTH // 2
            x_to   = rect.left() + to_lane   * LANE_WIDTH + LANE_WIDTH // 2
            painter.drawLine(x_from, mid_y, x_to, rect.bottom())

        # --- commit marker (dot on its lane so the branch is visible) ---
        x_dot = rect.left() + row.commit_lane * LANE_WIDTH + LANE_WIDTH // 2
        colour = QColor(LANE_COLOURS[row.commit_lane % len(LANE_COLOURS)])
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(colour))
        painter.drawEllipse(
            x_dot - DOT_RADIUS, mid_y - DOT_RADIUS,
            DOT_RADIUS * 2, DOT_RADIUS * 2,
        )


# -- graph builder -----------------------------------------------------------

def _build_graph_data(commits: list) -> List[GraphRow]:
    """Build lane layout for the commit graph.

    For every commit produces a :class:`GraphRow` with explicit top-half
    and bottom-half line segments so the delegate can draw continuous,
    gap-free connections between rows.
    """
    lanes: list = []          # lanes[i] = expected commit hash, or None
    result: List[GraphRow] = []

    for ci in commits:
        h = ci.hash

        # --- locate or allocate the lane for this commit ----------------
        commit_lane = None
        for i, lh in enumerate(lanes):
            if lh == h:
                commit_lane = i
                break

        is_tip = commit_lane is None

        if is_tip:
            # New branch tip — reuse a free slot or append
            for i, lh in enumerate(lanes):
                if lh is None:
                    commit_lane = i
                    break
            if commit_lane is None:
                commit_lane = len(lanes)
                lanes.append(None)

        # --- TOP HALF: lines from top-of-cell to centre -----------------
        top_lines: List[Tuple[int, int, int]] = []
        converging: List[int] = []

        for i, lh in enumerate(lanes):
            if lh is None:
                continue
            if lh == h:
                # This lane expected this commit — converge to commit_lane
                converging.append(i)
                top_lines.append((i, commit_lane, i))
            else:
                # Unrelated lane — straight pass-through
                top_lines.append((i, i, i))

        # Free all converging lanes (they delivered their commit)
        for i in converging:
            lanes[i] = None

        # --- process parents --------------------------------------------
        is_root = len(ci.parents) == 0
        new_parent_lanes: set = set()
        existing_parent_lanes: set = set()

        if ci.parents:
            # First parent keeps the commit lane
            lanes[commit_lane] = ci.parents[0]

            # Additional parents (merges)
            for ph in ci.parents[1:]:
                found = None
                for i, lh in enumerate(lanes):
                    if lh == ph:
                        found = i
                        break
                if found is not None:
                    existing_parent_lanes.add(found)
                else:
                    allocated = None
                    for i, lh in enumerate(lanes):
                        if lh is None:
                            allocated = i
                            lanes[i] = ph
                            break
                    if allocated is None:
                        allocated = len(lanes)
                        lanes.append(ph)
                    new_parent_lanes.add(allocated)
        # else: root — lane stays None (already freed above)

        # --- BOTTOM HALF: lines from centre to bottom-of-cell -----------
        bot_lines: List[Tuple[int, int, int]] = []

        for i, lh in enumerate(lanes):
            if lh is None:
                continue
            if i == commit_lane:
                # First parent continues straight down
                bot_lines.append((commit_lane, commit_lane, commit_lane))
            elif i in new_parent_lanes:
                # Newly-created merge-parent lane — diagonal from commit
                bot_lines.append((commit_lane, i, i))
            elif i in existing_parent_lanes:
                # Lane was already active AND is a merge parent —
                # keep the pass-through AND add a merge diagonal
                bot_lines.append((i, i, i))
                bot_lines.append((commit_lane, i, i))
            else:
                # Regular pass-through
                bot_lines.append((i, i, i))

        # Trim trailing empty lanes
        while lanes and lanes[-1] is None:
            lanes.pop()

        result.append(GraphRow(
            commit_lane=commit_lane,
            top_lines=top_lines,
            bot_lines=bot_lines,
            is_tip=is_tip,
            is_root=is_root,
            total_lanes=max(len(lanes), commit_lane + 1),
        ))

    return result


class CommitLogPanel(QWidget):
    """Commit history panel with graph visualisation."""

    commit_selected = pyqtSignal(str)  # commit hash
    compare_requested = pyqtSignal(str, str)  # two commit hashes

    def __init__(self, parent=None):
        super().__init__(parent)
        self._commits = []
        self._graph_data = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(2)

        # Search / filter bar
        filter_row = QHBoxLayout()
        filter_row.setContentsMargins(4, 4, 4, 0)
        self._filter_edit = QLineEdit()
        self._filter_edit.setPlaceholderText("Filter commits…")
        self._filter_edit.setClearButtonEnabled(True)
        self._filter_edit.textChanged.connect(self._apply_filter)
        filter_row.addWidget(self._filter_edit)
        layout.addLayout(filter_row)

        # Commit table
        self._tree = QTreeWidget()
        self._tree.setHeaderLabels(["Graph", "Refs", "Hash", "Message", "Author", "Date"])
        self._tree.setRootIsDecorated(False)
        self._tree.setAlternatingRowColors(True)
        self._tree.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self._tree.setUniformRowHeights(True)
        self._tree.setFont(QFont("Consolas", 10))
        self._tree.itemSelectionChanged.connect(self._on_selection)
        self._tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self._tree.customContextMenuRequested.connect(self._on_context_menu)

        header = self._tree.header()
        header.setStretchLastSection(False)
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.Fixed)
        header.setSectionResizeMode(2, QHeaderView.ResizeMode.Fixed)
        header.setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        header.setSectionResizeMode(4, QHeaderView.ResizeMode.Fixed)
        header.setSectionResizeMode(5, QHeaderView.ResizeMode.Fixed)
        self._tree.setColumnWidth(0, LANE_WIDTH * 2)
        self._tree.setColumnWidth(1, 180)
        self._tree.setColumnWidth(2, 80)
        self._tree.setColumnWidth(4, 130)
        self._tree.setColumnWidth(5, 150)

        self._tree.verticalScrollBar().valueChanged.connect(self._update_graph_column_width)
        self._tree.verticalScrollBar().rangeChanged.connect(
            lambda *_: self._update_graph_column_width()
        )
        self._tree.viewport().installEventFilter(self)

        # Graph delegate
        delegate = _GraphDelegate(lambda: self._graph_data, self._tree)
        self._tree.setItemDelegateForColumn(0, delegate)

        layout.addWidget(self._tree)

    # -- public ----------------------------------------------------------------

    def load(self, repo: str):
        """Load commit log from *repo*."""
        try:
            self._commits = git_ops.get_log(repo, count=500)
        except Exception:
            self._commits = []
        self._graph_data = _build_graph_data(self._commits)
        self._populate()
        QTimer.singleShot(0, self._update_graph_column_width)

    def _populate(self, filter_text: str = ""):
        self._tree.clear()
        flt = filter_text.lower()
        for idx, ci in enumerate(self._commits):
            if flt:
                haystack = f"{ci.short_hash} {ci.message} {ci.author} {ci.refs}".lower()
                if flt not in haystack:
                    continue
            item = QTreeWidgetItem([
                "",           # graph drawn by delegate
                ci.refs,
                ci.short_hash,
                ci.message,
                ci.author,
                ci.date[:16],  # trim seconds/tz
            ])
            item.setData(0, Qt.ItemDataRole.UserRole, ci.hash)
            # Store original index for graph alignment
            item.setData(0, Qt.ItemDataRole.UserRole + 1, idx)

            # Colour refs
            if ci.refs:
                item.setForeground(1, QColor(palette()["cyan"]))

            self._tree.addTopLevelItem(item)

        self._update_graph_column_width()

    # -- slots -----------------------------------------------------------------

    def _on_selection(self):
        items = self._tree.selectedItems()
        if items:
            h = items[0].data(0, Qt.ItemDataRole.UserRole)
            if h:
                self.commit_selected.emit(h)

    def _apply_filter(self, text: str):
        self._populate(text.strip())

    def _on_context_menu(self, pos):
        """Right-click context menu — Compare is enabled when exactly 2 commits are selected."""
        items = self._tree.selectedItems()
        menu = QMenu(self)

        act_compare = QAction("🔍 Compare", self)
        act_compare.setEnabled(len(items) == 2)
        act_compare.triggered.connect(self._on_compare)
        menu.addAction(act_compare)

        menu.exec(self._tree.viewport().mapToGlobal(pos))

    def _on_compare(self):
        items = self._tree.selectedItems()
        if len(items) != 2:
            return
        h1 = items[0].data(0, Qt.ItemDataRole.UserRole)
        h2 = items[1].data(0, Qt.ItemDataRole.UserRole)
        if h1 and h2:
            self.compare_requested.emit(h1, h2)

    def eventFilter(self, obj, event):
        if obj is self._tree.viewport() and event.type() in (
            QEvent.Type.Resize,
            QEvent.Type.Show,
        ):
            QTimer.singleShot(0, self._update_graph_column_width)
        return super().eventFilter(obj, event)

    def _visible_original_indexes(self) -> List[int]:
        count = self._tree.topLevelItemCount()
        if count == 0:
            return []

        viewport = self._tree.viewport()
        top_row = self._tree.indexAt(viewport.rect().topLeft()).row()
        bottom_row = self._tree.indexAt(viewport.rect().bottomLeft()).row()

        if top_row < 0:
            top_row = 0
        if bottom_row < 0:
            bottom_row = count - 1

        visible: List[int] = []
        for row in range(top_row, min(bottom_row, count - 1) + 1):
            item = self._tree.topLevelItem(row)
            if not item:
                continue
            orig_idx = item.data(0, Qt.ItemDataRole.UserRole + 1)
            if isinstance(orig_idx, int):
                visible.append(orig_idx)
        return visible

    def _update_graph_column_width(self):
        visible = self._visible_original_indexes()
        if not visible:
            target = LANE_WIDTH * 2
        else:
            max_lanes = 1
            for idx in visible:
                if 0 <= idx < len(self._graph_data):
                    max_lanes = max(max_lanes, self._graph_data[idx].total_lanes)
            target = max((max_lanes + 1) * LANE_WIDTH, LANE_WIDTH * 2)

        if self._tree.columnWidth(0) != target:
            self._tree.setColumnWidth(0, target)
