"""
Two-way side-by-side diff view — Meld-style.

Shows left and right file content side-by-side with:
- Faint green background for added lines, faint red for deleted lines.
- Character-level highlighting within changed lines (less-faint bg).
- Synchronised scrolling between both panes.
- Line numbers in the gutter.
- Navigation buttons for jumping between changes.
"""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QWidget, QHBoxLayout, QVBoxLayout, QPlainTextEdit, QLabel,
    QScrollBar, QPushButton, QSplitter, QTextEdit, QCheckBox,
)
from PyQt6.QtGui import (
    QFont, QColor, QTextCharFormat, QTextCursor, QSyntaxHighlighter,
    QTextDocument, QPainter, QPalette, QTextFormat, QTextBlockUserData,
)
from PyQt6.QtCore import Qt, QRect, QSize, pyqtSignal, QTimer

from csaszicompare.diff_engine import (
    DiffLine, LineTag, CharSpan, diff_lines, diff_files,
)
from csaszicompare.themes import palette

# ═══════════════════════════════════════════════════════════════════════════
# Line-number gutter
# ═══════════════════════════════════════════════════════════════════════════

class _LineNumberArea(QWidget):
    """Gutter that shows line numbers beside a QPlainTextEdit."""

    def __init__(self, editor: "_DiffTextEdit"):
        super().__init__(editor)
        self._editor = editor

    def sizeHint(self):
        return QSize(self._editor.line_number_area_width(), 0)

    def paintEvent(self, event):
        self._editor.line_number_area_paint(event)


# ═══════════════════════════════════════════════════════════════════════════
# Block user data (stores line-level metadata per text block)
# ═══════════════════════════════════════════════════════════════════════════

class _BlockData(QTextBlockUserData):
    def __init__(self):
        super().__init__()
        self.tag: LineTag = LineTag.EQUAL
        self.lineno: int | None = None
        self.char_spans: list[CharSpan] = []


# ═══════════════════════════════════════════════════════════════════════════
# Diff text editor (one side)
# ═══════════════════════════════════════════════════════════════════════════

class _DiffTextEdit(QPlainTextEdit):
    """Read-only code viewer with line numbers and diff-coloured backgrounds."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setReadOnly(True)
        self.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self.setFont(QFont("Consolas", 10))

        self._line_area = _LineNumberArea(self)
        self.blockCountChanged.connect(self._update_line_area_width)
        self.updateRequest.connect(self._update_line_area)
        self._update_line_area_width(0)

    # ── line number gutter ────────────────────────────────────────────────

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
        fg = QColor(p["fg_dim"])
        painter.setPen(fg)
        font = QFont("Consolas", 10)
        painter.setFont(font)

        while block.isValid() and top <= event.rect().bottom():
            if block.isVisible() and bottom >= event.rect().top():
                data = block.userData()
                if isinstance(data, _BlockData) and data.lineno is not None:
                    num_text = str(data.lineno)
                else:
                    num_text = ""
                painter.drawText(
                    0, top, self._line_area.width() - 4,
                    self.fontMetrics().height(),
                    Qt.AlignmentFlag.AlignRight, num_text,
                )
            block = block.next()
            top = bottom
            bottom = top + round(self.blockBoundingRect(block).height())

        painter.end()


# ═══════════════════════════════════════════════════════════════════════════
# Two-way diff widget (the full side-by-side view)
# ═══════════════════════════════════════════════════════════════════════════

class TwoWayDiffView(QWidget):
    """Side-by-side diff view with synchronised scrolling."""

    DEFAULT_CONTEXT = 10  # lines of context around each change group

    def __init__(self, parent=None):
        super().__init__(parent)
        self._raw_diff_data: list[DiffLine] = []   # full file diff (no context folding)
        self._diff_data: list[DiffLine] = []        # currently displayed (may be folded)
        self._change_groups: list[int] = []          # block index of *first* line in each group
        self._current_group: int = -1
        self._show_full = False

        # Store current paths/titles for re-render on theme change
        self._cur_left_title = "Left"
        self._cur_right_title = "Right"

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(0)

        # ── Top bar: checkbox + nav on LEFT, column labels on RIGHT ───────
        top_bar = QHBoxLayout()
        top_bar.setContentsMargins(4, 4, 4, 2)
        top_bar.setSpacing(6)

        # Left controls column: checkbox, then nav row
        ctrl_col = QVBoxLayout()
        ctrl_col.setSpacing(2)

        self._chk_full = QCheckBox("Show full file")
        self._chk_full.setFont(QFont("Segoe UI", 9))
        self._chk_full.setChecked(False)
        self._chk_full.setStyleSheet(
            "QCheckBox::indicator { border: 2px solid #6272A4; border-radius: 3px;"
            " width: 14px; height: 14px; background: transparent; }"
            "QCheckBox::indicator:checked { background: #BD93F9; border-color: #BD93F9; }"
        )
        self._chk_full.toggled.connect(self._on_full_toggled)
        ctrl_col.addWidget(self._chk_full)

        nav_row = QHBoxLayout()
        nav_row.setSpacing(4)
        self._btn_prev = QPushButton("▲ Prev")
        self._btn_prev.setFixedWidth(72)
        self._btn_prev.setFixedHeight(22)
        self._btn_prev.clicked.connect(self._go_prev_change)
        self._btn_next = QPushButton("▼ Next")
        self._btn_next.setFixedWidth(72)
        self._btn_next.setFixedHeight(22)
        self._btn_next.clicked.connect(self._go_next_change)
        self._change_label = QLabel("")
        self._change_label.setFont(QFont("Consolas", 9))
        nav_row.addWidget(self._btn_prev)
        nav_row.addWidget(self._btn_next)
        nav_row.addWidget(self._change_label)
        nav_row.addStretch()
        ctrl_col.addLayout(nav_row)

        top_bar.addLayout(ctrl_col)

        # Spacer between controls and column labels
        top_bar.addSpacing(12)

        # Left column label (multi-line: hash, date, path)
        self._left_label = QLabel("Left")
        self._left_label.setProperty("accent", True)
        self._left_label.setFont(QFont("Consolas", 9))
        self._left_label.setWordWrap(True)
        self._left_label.setTextFormat(Qt.TextFormat.PlainText)
        top_bar.addWidget(self._left_label, 1)

        # Right column label (multi-line)
        self._right_label = QLabel("Right")
        self._right_label.setProperty("accent", True)
        self._right_label.setFont(QFont("Consolas", 9))
        self._right_label.setWordWrap(True)
        self._right_label.setTextFormat(Qt.TextFormat.PlainText)
        top_bar.addWidget(self._right_label, 1)

        lay.addLayout(top_bar)

        # ── Editors ───────────────────────────────────────────────────────
        splitter = QSplitter(Qt.Orientation.Horizontal)
        self._left_edit = _DiffTextEdit()
        self._right_edit = _DiffTextEdit()
        splitter.addWidget(self._left_edit)
        splitter.addWidget(self._right_edit)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 1)
        lay.addWidget(splitter, 1)

        # ── Synchronise scrolling ─────────────────────────────────────────
        self._syncing = False
        self._left_edit.verticalScrollBar().valueChanged.connect(self._sync_scroll_left)
        self._right_edit.verticalScrollBar().valueChanged.connect(self._sync_scroll_right)

    # ── Public API ────────────────────────────────────────────────────────

    def set_labels(self, left: str, right: str):
        self._left_label.setText(left)
        self._right_label.setText(right)

    def show_diff(
        self,
        diff_data: list[DiffLine],
        left_title: str = "Left",
        right_title: str = "Right",
    ):
        """Populate both panes from pre-computed diff data (full, no context folding)."""
        self._raw_diff_data = diff_data
        self._cur_left_title = left_title
        self._cur_right_title = right_title
        self._left_label.setText(left_title)
        self._right_label.setText(right_title)
        self._apply_view()

    def show_files(
        self, left_path: str, right_path: str,
        context: int | None = None,
        left_title: str = "", right_title: str = "",
    ):
        """Diff two files and display."""
        # Always compute full diff (context=None), folding is handled in _apply_view
        data = diff_files(left_path, right_path, context=None)
        self.show_diff(
            data,
            left_title or left_path,
            right_title or right_path,
        )

    def show_texts(
        self,
        left_text: str, right_text: str,
        left_title: str = "Left", right_title: str = "Right",
        context: int | None = None,
    ):
        """Diff two strings and display."""
        left_lines = left_text.splitlines()
        right_lines = right_text.splitlines()
        data = diff_lines(left_lines, right_lines, context=None)
        self.show_diff(data, left_title, right_title)

    def clear(self):
        self._left_edit.clear()
        self._right_edit.clear()
        self._raw_diff_data = []
        self._diff_data = []
        self._change_groups = []
        self._current_group = -1
        self._change_label.setText("")

    def rerender(self):
        """Re-render with current data (e.g. after theme change)."""
        if self._raw_diff_data:
            self._apply_view()

    # ── Full-file toggle ──────────────────────────────────────────────────

    def _on_full_toggled(self, checked: bool):
        self._show_full = checked
        if self._raw_diff_data:
            self._apply_view()

    def _apply_view(self):
        """Apply context folding (or full) and render."""
        if self._show_full:
            self._diff_data = self._raw_diff_data
        else:
            self._diff_data = self._fold_context(self._raw_diff_data, self.DEFAULT_CONTEXT)
        self._render()

    # ── Rendering ─────────────────────────────────────────────────────────

    @staticmethod
    def _fold_context(raw: list[DiffLine], context: int) -> list[DiffLine]:
        """Collapse equal runs into hunk separators, keeping *context* lines."""
        if not raw:
            return raw
        interesting = [i for i, d in enumerate(raw) if d.tag != LineTag.EQUAL]
        if not interesting:
            # All equal — show summary
            if len(raw) > context * 2:
                result = list(raw[:context])
                result.append(DiffLine(
                    tag=LineTag.HUNK, left_lineno=None, right_lineno=None,
                    left_text="", right_text="",
                ))
                result.extend(raw[-context:])
                return result
            return list(raw)

        result: list[DiffLine] = []
        last = -1
        for idx in interesting:
            start = max(last + 1, idx - context)
            if result and start > last + 1:
                result.append(DiffLine(
                    tag=LineTag.HUNK, left_lineno=None, right_lineno=None,
                    left_text="", right_text="",
                ))
            for i in range(start, min(idx + context + 1, len(raw))):
                if i > last:
                    result.append(raw[i])
            last = max(last, idx + context)

        remaining = last + 1
        if remaining < len(raw):
            tail = raw[remaining: remaining + context]
            if tail:
                result.extend(tail)
            if remaining + context < len(raw):
                result.append(DiffLine(
                    tag=LineTag.HUNK, left_lineno=None, right_lineno=None,
                    left_text="", right_text="",
                ))
        return result

    def _render(self):
        p = palette()
        self._left_edit.clear()
        self._right_edit.clear()

        bg_add   = QColor(p["diff_add_bg"])
        bg_del   = QColor(p["diff_del_bg"])
        bg_eq    = QColor(p["diff_equal_bg"])
        bg_hunk  = QColor(p["diff_hunk_bg"])
        char_add = QColor(p["diff_add_char_bg"])
        char_del = QColor(p["diff_del_char_bg"])
        fg       = QColor(p["fg"])
        fg_dim   = QColor(p["fg_dim"])

        left_cursor = QTextCursor(self._left_edit.document())
        right_cursor = QTextCursor(self._right_edit.document())

        self._change_indices = []

        for i, dl in enumerate(self._diff_data):
            if i > 0:
                left_cursor.insertBlock()
                right_cursor.insertBlock()

            # Track changes for navigation
            if dl.tag not in (LineTag.EQUAL, LineTag.HUNK):
                self._change_indices.append(i)

            # Determine background colours
            if dl.tag == LineTag.EQUAL:
                left_bg, right_bg = bg_eq, bg_eq
            elif dl.tag == LineTag.INSERT:
                left_bg, right_bg = bg_eq, bg_add
            elif dl.tag == LineTag.DELETE:
                left_bg, right_bg = bg_del, bg_eq
            elif dl.tag == LineTag.REPLACE:
                left_bg, right_bg = bg_del, bg_add
            elif dl.tag == LineTag.HUNK:
                left_bg, right_bg = bg_hunk, bg_hunk
            else:
                left_bg, right_bg = bg_eq, bg_eq

            # Set block background via block format
            lbf = left_cursor.blockFormat()
            lbf.setBackground(left_bg)
            left_cursor.setBlockFormat(lbf)

            rbf = right_cursor.blockFormat()
            rbf.setBackground(right_bg)
            right_cursor.setBlockFormat(rbf)

            # Store metadata
            left_data = _BlockData()
            left_data.tag = dl.tag
            left_data.lineno = dl.left_lineno
            left_data.char_spans = dl.left_char_spans

            right_data = _BlockData()
            right_data.tag = dl.tag
            right_data.lineno = dl.right_lineno
            right_data.char_spans = dl.right_char_spans

            # Insert text with character-level highlighting
            if dl.tag == LineTag.HUNK:
                fmt = QTextCharFormat()
                fmt.setForeground(fg_dim)
                left_cursor.insertText("─── ⋯ ───", fmt)
                right_cursor.insertText("─── ⋯ ───", fmt)
            else:
                self._insert_with_char_highlights(
                    left_cursor, dl.left_text, dl.left_char_spans,
                    fg, left_bg, char_del,
                )
                self._insert_with_char_highlights(
                    right_cursor, dl.right_text, dl.right_char_spans,
                    fg, right_bg, char_add,
                )

            # Assign block user data
            left_cursor.block().setUserData(left_data)
            right_cursor.block().setUserData(right_data)

        self._current_change = -1
        # Build change groups: a contiguous run of changed lines = one group
        self._change_groups = []
        in_group = False
        for idx in self._change_indices:
            if not in_group or (self._change_groups and idx > self._change_indices[self._change_indices.index(idx) - 1] + 1):
                self._change_groups.append(idx)
                in_group = True
        # Deduplicate: only first block index of each contiguous run
        groups: list[int] = []
        prev = -2
        for idx in self._change_indices:
            if idx != prev + 1:
                groups.append(idx)
            prev = idx
        self._change_groups = groups
        self._current_group = -1

        n = len(self._change_groups)
        self._change_label.setText(f" {n} change{'s' if n != 1 else ''}")

    def _insert_with_char_highlights(
        self,
        cursor: QTextCursor,
        text: str,
        spans: list[CharSpan],
        fg_color: QColor,
        line_bg: QColor,
        highlight_bg: QColor,
    ):
        if not spans:
            fmt = QTextCharFormat()
            fmt.setForeground(fg_color)
            cursor.insertText(text, fmt)
            return

        for span in spans:
            fmt = QTextCharFormat()
            fmt.setForeground(fg_color)
            if span.tag in ("delete", "insert", "replace"):
                fmt.setBackground(highlight_bg)
            chunk = text[span.start:span.end]
            if chunk:
                cursor.insertText(chunk, fmt)

        # Any remaining text past spans
        max_end = max((s.end for s in spans), default=0)
        if max_end < len(text):
            fmt = QTextCharFormat()
            fmt.setForeground(fg_color)
            cursor.insertText(text[max_end:], fmt)

    # ── Navigation ────────────────────────────────────────────────────────

    def _go_prev_change(self):
        if not self._change_groups:
            return
        if self._current_group <= 0:
            self._current_group = len(self._change_groups) - 1
        else:
            self._current_group -= 1
        self._scroll_to_group()

    def _go_next_change(self):
        if not self._change_groups:
            return
        if self._current_group >= len(self._change_groups) - 1:
            self._current_group = 0
        else:
            self._current_group += 1
        self._scroll_to_group()

    def _scroll_to_group(self):
        if self._current_group < 0 or self._current_group >= len(self._change_groups):
            return
        block_idx = self._change_groups[self._current_group]
        block = self._left_edit.document().findBlockByNumber(block_idx)
        if block.isValid():
            cursor = QTextCursor(block)
            self._left_edit.setTextCursor(cursor)
            self._left_edit.centerCursor()
        block = self._right_edit.document().findBlockByNumber(block_idx)
        if block.isValid():
            cursor = QTextCursor(block)
            self._right_edit.setTextCursor(cursor)
            self._right_edit.centerCursor()

    # ── Scroll sync ───────────────────────────────────────────────────────

    def _sync_scroll_left(self, value):
        if self._syncing:
            return
        self._syncing = True
        self._right_edit.verticalScrollBar().setValue(value)
        self._syncing = False

    def _sync_scroll_right(self, value):
        if self._syncing:
            return
        self._syncing = True
        self._left_edit.verticalScrollBar().setValue(value)
        self._syncing = False
