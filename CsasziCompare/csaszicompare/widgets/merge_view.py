"""
Three-way merge view — for rebase / merge conflict resolution.

Layout:  Left (ours) | Centre (result) | Right (theirs)

Column headers adapt to the operation:
- Rebase:  "Rebasing"  | "Result"  | "Rebasing onto"
- Merge:   "Merging"   | "Result"  | "Merging into"
"""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPlainTextEdit, QLabel,
    QPushButton, QSplitter, QMessageBox,
)
from PyQt6.QtGui import (
    QFont, QColor, QTextCharFormat, QTextCursor, QTextBlockUserData,
    QPainter,
)
from PyQt6.QtCore import Qt, QRect, QSize, pyqtSignal

from csaszicompare.diff_engine import merge3, MergeChunk, MergeTag
from csaszicompare.themes import palette


# ═══════════════════════════════════════════════════════════════════════════
# Line number gutter (reused from diff_view pattern)
# ═══════════════════════════════════════════════════════════════════════════

class _LineNumberArea(QWidget):
    def __init__(self, editor: "_MergeTextEdit"):
        super().__init__(editor)
        self._editor = editor

    def sizeHint(self):
        return QSize(self._editor.line_number_area_width(), 0)

    def paintEvent(self, event):
        self._editor.line_number_area_paint(event)


class _BlockData(QTextBlockUserData):
    def __init__(self):
        super().__init__()
        self.lineno: int | None = None
        self.is_conflict: bool = False


class _MergeTextEdit(QPlainTextEdit):
    """Text editor with line numbers for merge view."""

    def __init__(self, readonly: bool = True, parent=None):
        super().__init__(parent)
        self.setReadOnly(readonly)
        self.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self.setFont(QFont("Consolas", 10))

        self._line_area = _LineNumberArea(self)
        self.blockCountChanged.connect(self._update_line_area_width)
        self.updateRequest.connect(self._update_line_area)
        self._update_line_area_width(0)

    def line_number_area_width(self) -> int:
        digits = max(1, len(str(self.blockCount())))
        return 12 + self.fontMetrics().horizontalAdvance("9") * max(digits, 4)

    def _update_line_area_width(self, _):
        self.setViewportMargins(self.line_number_area_width(), 0, 0, 0)

    def _update_line_area(self, rect, dy):
        if dy:
            self._line_area.scroll(0, dy)
        else:
            self._line_area.update(0, rect.y(), self._line_area.width(), rect.height())
        if rect.contains(self.viewport().rect()):
            self._update_line_area_width(0)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        cr = self.contentsRect()
        self._line_area.setGeometry(
            QRect(cr.left(), cr.top(), self.line_number_area_width(), cr.height())
        )

    def line_number_area_paint(self, event):
        p = palette()
        painter = QPainter(self._line_area)
        painter.fillRect(event.rect(), QColor(p["bg_alt"]))
        block = self.firstVisibleBlock()
        top = round(self.blockBoundingGeometry(block).translated(self.contentOffset()).top())
        bottom = top + round(self.blockBoundingRect(block).height())
        painter.setPen(QColor(p["fg_dim"]))
        painter.setFont(QFont("Consolas", 10))

        while block.isValid() and top <= event.rect().bottom():
            if block.isVisible() and bottom >= event.rect().top():
                data = block.userData()
                num = str(data.lineno) if isinstance(data, _BlockData) and data.lineno else ""
                painter.drawText(
                    0, top, self._line_area.width() - 4,
                    self.fontMetrics().height(),
                    Qt.AlignmentFlag.AlignRight, num,
                )
            block = block.next()
            top = bottom
            bottom = top + round(self.blockBoundingRect(block).height())
        painter.end()


# ═══════════════════════════════════════════════════════════════════════════
# Three-way merge widget
# ═══════════════════════════════════════════════════════════════════════════

class ThreeWayMergeView(QWidget):
    """Three-column merge/rebase view: ours | result | theirs."""

    merge_saved = pyqtSignal(str)  # emitted with merged content when saved

    def __init__(self, parent=None):
        super().__init__(parent)
        self._chunks: list[MergeChunk] = []

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(0)

        # ── Header row ────────────────────────────────────────────────────
        hdr = QHBoxLayout()
        hdr.setContentsMargins(4, 4, 4, 2)

        self._left_label = QLabel("Ours")
        self._left_label.setProperty("accent", True)
        self._left_label.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))

        self._center_label = QLabel("Result")
        self._center_label.setProperty("accent", True)
        self._center_label.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))

        self._right_label = QLabel("Theirs")
        self._right_label.setProperty("accent", True)
        self._right_label.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))

        self._conflict_label = QLabel("")
        self._conflict_label.setFont(QFont("Consolas", 10))

        self._btn_use_left = QPushButton("◀ Use Left")
        self._btn_use_left.setFixedWidth(100)
        self._btn_use_left.clicked.connect(self._use_left_for_conflict)

        self._btn_use_right = QPushButton("Use Right ▶")
        self._btn_use_right.setFixedWidth(100)
        self._btn_use_right.clicked.connect(self._use_right_for_conflict)

        self._btn_save = QPushButton("💾 Save Result")
        self._btn_save.setProperty("accent", True)
        self._btn_save.clicked.connect(self._on_save)

        hdr.addWidget(self._left_label, 1)
        hdr.addWidget(self._btn_use_left)
        hdr.addWidget(self._center_label, 1)
        hdr.addWidget(self._btn_use_right)
        hdr.addWidget(self._right_label, 1)
        hdr.addWidget(self._conflict_label)
        hdr.addWidget(self._btn_save)
        lay.addLayout(hdr)

        # ── Three editors ─────────────────────────────────────────────────
        splitter = QSplitter(Qt.Orientation.Horizontal)
        self._left_edit = _MergeTextEdit(readonly=True)
        self._center_edit = _MergeTextEdit(readonly=False)   # editable result
        self._right_edit = _MergeTextEdit(readonly=True)

        splitter.addWidget(self._left_edit)
        splitter.addWidget(self._center_edit)
        splitter.addWidget(self._right_edit)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 1)
        splitter.setStretchFactor(2, 1)
        lay.addWidget(splitter, 1)

        # Sync scrolling
        self._syncing = False
        for editor in (self._left_edit, self._center_edit, self._right_edit):
            editor.verticalScrollBar().valueChanged.connect(
                lambda v, src=editor: self._sync_scroll(src, v)
            )

    # ── Public API ────────────────────────────────────────────────────────

    def set_mode(self, mode: str):
        """Set column labels. mode = 'rebase' or 'merge'."""
        if mode == "rebase":
            self._left_label.setText("This is what I am rebasing")
            self._center_label.setText("Result")
            self._right_label.setText("This is what I am rebasing on")
        elif mode == "merge":
            self._left_label.setText("This is what I am merging")
            self._center_label.setText("Result")
            self._right_label.setText("This is what I am merging into")
        else:
            self._left_label.setText("Left")
            self._center_label.setText("Result")
            self._right_label.setText("Right")

    def load_files(self, base_path: str, left_path: str, right_path: str, mode: str = "merge"):
        """Load three files and perform three-way merge."""
        self.set_mode(mode)

        def _read(p):
            try:
                with open(p, encoding="utf-8", errors="replace") as f:
                    return f.read().splitlines()
            except FileNotFoundError:
                return []

        base = _read(base_path)
        left = _read(left_path)
        right = _read(right_path)
        self._chunks = merge3(base, left, right)
        self._render()

    def load_texts(
        self,
        base_text: str, left_text: str, right_text: str,
        mode: str = "merge",
    ):
        """Three-way merge from text strings."""
        self.set_mode(mode)
        self._chunks = merge3(
            base_text.splitlines(),
            left_text.splitlines(),
            right_text.splitlines(),
        )
        self._render()

    def get_result_text(self) -> str:
        return self._center_edit.toPlainText()

    def clear(self):
        self._left_edit.clear()
        self._center_edit.clear()
        self._right_edit.clear()
        self._chunks = []
        self._conflict_label.setText("")

    # ── Rendering ─────────────────────────────────────────────────────────

    def _render(self):
        p = palette()
        self._left_edit.clear()
        self._center_edit.clear()
        self._right_edit.clear()

        bg_ok        = QColor(p["diff_equal_bg"])
        bg_add       = QColor(p["diff_add_bg"])
        bg_del       = QColor(p["diff_del_bg"])
        bg_conflict  = QColor(p["diff_conflict_bg"])
        fg           = QColor(p["fg"])
        fg_dim       = QColor(p["fg_dim"])

        left_cursor   = QTextCursor(self._left_edit.document())
        center_cursor = QTextCursor(self._center_edit.document())
        right_cursor  = QTextCursor(self._right_edit.document())

        first_block = True
        n_conflicts = 0
        lineno_l = lineno_c = lineno_r = 0

        for chunk in self._chunks:
            is_conflict = chunk.tag == MergeTag.CONFLICT
            if is_conflict:
                n_conflicts += 1

            # Determine max lines across all three columns for alignment
            max_lines = max(
                len(chunk.left_lines),
                len(chunk.result_lines),
                len(chunk.right_lines),
                1,
            )

            for k in range(max_lines):
                if not first_block:
                    left_cursor.insertBlock()
                    center_cursor.insertBlock()
                    right_cursor.insertBlock()
                first_block = False

                # Left
                lt = chunk.left_lines[k] if k < len(chunk.left_lines) else ""
                has_left = k < len(chunk.left_lines)
                # Right
                rt = chunk.right_lines[k] if k < len(chunk.right_lines) else ""
                has_right = k < len(chunk.right_lines)
                # Centre
                ct = chunk.result_lines[k] if k < len(chunk.result_lines) else ""
                has_center = k < len(chunk.result_lines)

                # Backgrounds
                if is_conflict:
                    lbg = rbg = cbg = bg_conflict
                elif chunk.tag == MergeTag.RESOLVED and chunk.base_lines != chunk.left_lines:
                    lbg = bg_add if has_left else bg_ok
                    cbg = bg_ok
                    rbg = bg_ok
                elif chunk.tag == MergeTag.RESOLVED and chunk.base_lines != chunk.right_lines:
                    lbg = bg_ok
                    cbg = bg_ok
                    rbg = bg_add if has_right else bg_ok
                else:
                    lbg = rbg = cbg = bg_ok

                for cursor, text, bg, has_line, is_center in [
                    (left_cursor, lt, lbg, has_left, False),
                    (center_cursor, ct, cbg, has_center, True),
                    (right_cursor, rt, rbg, has_right, False),
                ]:
                    bf = cursor.blockFormat()
                    bf.setBackground(bg)
                    cursor.setBlockFormat(bf)
                    fmt = QTextCharFormat()
                    fmt.setForeground(fg if has_line else fg_dim)
                    cursor.insertText(text, fmt)

                    data = _BlockData()
                    data.is_conflict = is_conflict
                    if has_line:
                        if cursor is left_cursor:
                            lineno_l += 1
                            data.lineno = lineno_l
                        elif cursor is right_cursor:
                            lineno_r += 1
                            data.lineno = lineno_r
                        elif cursor is center_cursor:
                            lineno_c += 1
                            data.lineno = lineno_c
                    cursor.block().setUserData(data)

                # Add conflict markers to centre if conflict
                if is_conflict and k == 0:
                    if not chunk.result_lines:
                        # First line of conflict — show placeholder
                        cbf = center_cursor.blockFormat()
                        cbf.setBackground(bg_conflict)
                        center_cursor.setBlockFormat(cbf)
                        fmt = QTextCharFormat()
                        fmt.setForeground(fg_dim)
                        center_cursor.insertText("<<<< CONFLICT — resolve manually >>>>", fmt)

        self._conflict_label.setText(
            f"  {n_conflicts} conflict{'s' if n_conflicts != 1 else ''}"
            if n_conflicts else "  No conflicts ✓"
        )

    # ── Actions ───────────────────────────────────────────────────────────

    def _use_left_for_conflict(self):
        """Replace the first unresolved conflict in the result with left side."""
        for chunk in self._chunks:
            if chunk.tag == MergeTag.CONFLICT and not chunk.result_lines:
                chunk.result_lines = list(chunk.left_lines)
                chunk.tag = MergeTag.RESOLVED
                self._render()
                return

    def _use_right_for_conflict(self):
        """Replace the first unresolved conflict in the result with right side."""
        for chunk in self._chunks:
            if chunk.tag == MergeTag.CONFLICT and not chunk.result_lines:
                chunk.result_lines = list(chunk.right_lines)
                chunk.tag = MergeTag.RESOLVED
                self._render()
                return

    def _on_save(self):
        text = self.get_result_text()
        self.merge_saved.emit(text)

    # ── Scroll sync ───────────────────────────────────────────────────────

    def _sync_scroll(self, source, value):
        if self._syncing:
            return
        self._syncing = True
        for editor in (self._left_edit, self._center_edit, self._right_edit):
            if editor is not source:
                editor.verticalScrollBar().setValue(value)
        self._syncing = False
