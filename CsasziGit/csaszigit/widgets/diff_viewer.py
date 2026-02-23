"""
Diff viewer — syntax-highlighted diff display.

Supports unified diff format with coloured +/- lines, @@ hunks, and
file headers.
"""

from PyQt6.QtWidgets import QWidget, QVBoxLayout, QPlainTextEdit, QLabel
from PyQt6.QtGui import (
    QColor, QFont, QSyntaxHighlighter, QTextCharFormat,
    QTextDocument,
)
from PyQt6.QtCore import Qt, QRegularExpression

from csaszigit.themes import palette


class _DiffHighlighter(QSyntaxHighlighter):
    """Syntax highlighter for unified-diff text."""

    def highlightBlock(self, text: str):
        p = palette()
        fmt = QTextCharFormat()

        if text.startswith("+++") or text.startswith("---"):
            fmt.setForeground(QColor(p["purple"]))
            fmt.setFontWeight(QFont.Weight.Bold)
            self.setFormat(0, len(text), fmt)
        elif text.startswith("@@"):
            fmt.setForeground(QColor(p["cyan"]))
            self.setFormat(0, len(text), fmt)
        elif text.startswith("+"):
            fmt.setForeground(QColor(p["green"]))
            self.setFormat(0, len(text), fmt)
        elif text.startswith("-"):
            fmt.setForeground(QColor(p["red"]))
            self.setFormat(0, len(text), fmt)
        elif text.startswith("diff ") or text.startswith("index "):
            fmt.setForeground(QColor(p["comment"]))
            fmt.setFontWeight(QFont.Weight.Bold)
            self.setFormat(0, len(text), fmt)
        elif text.startswith("commit "):
            fmt.setForeground(QColor(p["yellow"]))
            fmt.setFontWeight(QFont.Weight.Bold)
            self.setFormat(0, len(text), fmt)
        elif text.startswith("Author:") or text.startswith("Date:") or text.startswith("Commit:") or text.startswith("CommitDate:") or text.startswith("AuthorDate:"):
            fmt.setForeground(QColor(p["orange"]))
            self.setFormat(0, len(text), fmt)


class DiffViewer(QWidget):
    """Read-only diff viewer with syntax highlighting."""

    def __init__(self, parent=None):
        super().__init__(parent)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(0)

        self._header = QLabel("Diff")
        self._header.setProperty("accent", True)
        self._header.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self._header.setContentsMargins(4, 4, 4, 2)
        lay.addWidget(self._header)

        self._edit = QPlainTextEdit()
        self._edit.setReadOnly(True)
        self._edit.setFont(QFont("Consolas", 10))
        self._edit.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self._highlighter = _DiffHighlighter(self._edit.document())
        lay.addWidget(self._edit)

    def show_diff(self, text: str, title: str = "Diff"):
        self._header.setText(title)
        self._edit.setPlainText(text)

    def clear(self):
        self._header.setText("Diff")
        self._edit.clear()
