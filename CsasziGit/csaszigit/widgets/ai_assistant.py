"""
AI Assistant panel — natural-language Git command interface powered by GPT.

The user types what they want to do, GPT suggests exact ``git`` commands,
and the user can *Approve* (execute them) or *Rephrase* their question.
Approved commands run in a log window at the bottom.
"""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTextEdit, QPlainTextEdit,
    QPushButton, QLabel, QGroupBox, QScrollArea, QFrame,
    QSizePolicy,
)
from PyQt6.QtGui import QFont, QColor, QTextCharFormat
from PyQt6.QtCore import Qt, pyqtSignal, QThread, pyqtSlot

from csaszigit import git_ops
from csaszigit.gpt_helper import GptHelper
from csaszigit.themes import palette


class _GptWorker(QThread):
    """Runs GPT request off the main thread."""
    finished = pyqtSignal(dict)

    def __init__(self, helper: GptHelper, request: str,
                 branch: str, status: str, parent=None):
        super().__init__(parent)
        self._helper = helper
        self._request = request
        self._branch = branch
        self._status = status

    def run(self):
        result = self._helper.suggest_commands(
            self._request, self._branch, self._status,
        )
        self.finished.emit(result)


class AiAssistantPanel(QWidget):
    """Dockable AI assistant for natural-language Git operations."""

    commands_executed = pyqtSignal()  # emitted after approved commands run

    def __init__(self, parent=None):
        super().__init__(parent)
        self._repo = ""
        self._api_key = ""
        self._model = "gpt-4o-mini"
        self._pending_commands: list = []
        self._worker = None

        lay = QVBoxLayout(self)
        lay.setContentsMargins(6, 6, 6, 6)
        lay.setSpacing(6)

        # Title
        title = QLabel("🤖  AI Git Assistant")
        title.setProperty("accent", True)
        title.setFont(QFont("Segoe UI", 13, QFont.Weight.Bold))
        lay.addWidget(title)

        # Input area
        self._input = QTextEdit()
        self._input.setPlaceholderText(
            "Describe what you want to do…\n\n"
            "Examples:\n"
            "  • Commit all changes with message 'fix login bug'\n"
            "  • Create a feature branch called 'new-ui'\n"
            "  • Undo the last commit but keep the changes\n"
            "  • Squash the last 3 commits"
        )
        self._input.setFont(QFont("Consolas", 11))
        self._input.setMaximumHeight(120)
        lay.addWidget(self._input)

        # Buttons row
        btn_row = QHBoxLayout()
        self._btn_ask = QPushButton("Ask GPT")
        self._btn_ask.setProperty("accent", True)
        self._btn_ask.clicked.connect(self._on_ask)
        btn_row.addWidget(self._btn_ask)
        btn_row.addStretch()
        lay.addLayout(btn_row)

        # Response area
        self._response_group = QGroupBox("GPT Suggestion")
        resp_lay = QVBoxLayout(self._response_group)

        self._explanation_lbl = QLabel("")
        self._explanation_lbl.setWordWrap(True)
        self._explanation_lbl.setFont(QFont("Segoe UI", 11))
        resp_lay.addWidget(self._explanation_lbl)

        self._commands_display = QPlainTextEdit()
        self._commands_display.setReadOnly(True)
        self._commands_display.setFont(QFont("Consolas", 11))
        self._commands_display.setMaximumHeight(100)
        resp_lay.addWidget(self._commands_display)

        self._warning_lbl = QLabel("")
        self._warning_lbl.setWordWrap(True)
        self._warning_lbl.setStyleSheet("color: #FF5555; font-weight: bold;")
        self._warning_lbl.hide()
        resp_lay.addWidget(self._warning_lbl)

        # Approve / Rephrase buttons
        action_row = QHBoxLayout()
        self._btn_approve = QPushButton("✓  Approve && Run")
        self._btn_approve.setProperty("accent", True)
        self._btn_approve.clicked.connect(self._on_approve)
        self._btn_approve.setEnabled(False)
        action_row.addWidget(self._btn_approve)

        self._btn_rephrase = QPushButton("↻  Rephrase")
        self._btn_rephrase.clicked.connect(self._on_rephrase)
        self._btn_rephrase.setEnabled(False)
        action_row.addWidget(self._btn_rephrase)
        action_row.addStretch()
        resp_lay.addLayout(action_row)

        self._response_group.hide()
        lay.addWidget(self._response_group)

        # Execution output
        self._output_group = QGroupBox("Command Output")
        out_lay = QVBoxLayout(self._output_group)
        self._output_log = QPlainTextEdit()
        self._output_log.setReadOnly(True)
        self._output_log.setFont(QFont("Consolas", 10))
        self._output_log.setMaximumHeight(150)
        out_lay.addWidget(self._output_log)
        self._output_group.hide()
        lay.addWidget(self._output_group)

        lay.addStretch()

        # No-key message
        self._no_key_lbl = QLabel(
            "⚠  Set your OpenAI API key in <b>Tools → Settings</b> to enable the AI assistant."
        )
        self._no_key_lbl.setWordWrap(True)
        self._no_key_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._no_key_lbl.setStyleSheet("padding: 20px; color: #6272A4;")
        lay.addWidget(self._no_key_lbl)

    # -- public ----------------------------------------------------------------

    def configure(self, api_key: str, model: str):
        self._api_key = api_key
        self._model = model
        has_key = bool(api_key.strip())
        self._no_key_lbl.setVisible(not has_key)
        self._input.setVisible(has_key)
        self._btn_ask.setVisible(has_key)

    def set_repo(self, repo: str):
        self._repo = repo

    # -- slots -----------------------------------------------------------------

    def _on_ask(self):
        text = self._input.toPlainText().strip()
        if not text or not self._api_key:
            return

        self._btn_ask.setEnabled(False)
        self._btn_ask.setText("Thinking…")
        self._response_group.hide()
        self._output_group.hide()

        # Get repo context
        branch = ""
        status = ""
        if self._repo:
            try:
                branch = git_ops.get_current_branch(self._repo)
            except Exception:
                pass
            try:
                r = git_ops.run_git(self._repo, "status", "-s", check=False)
                status = r.stdout[:500]
            except Exception:
                pass

        helper = GptHelper(self._api_key, self._model)
        self._worker = _GptWorker(helper, text, branch, status, self)
        self._worker.finished.connect(self._on_gpt_response)
        self._worker.start()

    @pyqtSlot(dict)
    def _on_gpt_response(self, result: dict):
        self._btn_ask.setEnabled(True)
        self._btn_ask.setText("Ask GPT")

        explanation = result.get("explanation", "")
        commands = result.get("commands", [])
        warning = result.get("warning", "")

        self._explanation_lbl.setText(explanation)
        self._commands_display.setPlainText("\n".join(commands))
        self._pending_commands = commands

        if warning:
            self._warning_lbl.setText(f"⚠  {warning}")
            self._warning_lbl.show()
        else:
            self._warning_lbl.hide()

        has_cmds = bool(commands)
        self._btn_approve.setEnabled(has_cmds)
        self._btn_rephrase.setEnabled(True)
        self._response_group.show()

    def _on_rephrase(self):
        """Clear response, focus input for rephrasing."""
        self._response_group.hide()
        self._input.setFocus()
        self._pending_commands = []

    def _on_approve(self):
        """Execute the approved commands and show output."""
        if not self._pending_commands or not self._repo:
            return

        self._output_log.clear()
        self._output_group.show()

        for cmd in self._pending_commands:
            self._output_log.appendPlainText(f"$ {cmd}")

            # Strip leading "git " if present
            if cmd.strip().lower().startswith("git "):
                git_cmd = cmd.strip()[4:]
            else:
                git_cmd = cmd.strip()

            try:
                out = git_ops.run_arbitrary(self._repo, git_cmd)
                if out:
                    self._output_log.appendPlainText(out)
                else:
                    self._output_log.appendPlainText("(no output)")
            except Exception as e:
                self._output_log.appendPlainText(f"ERROR: {e}")

            self._output_log.appendPlainText("")

        self._pending_commands = []
        self._btn_approve.setEnabled(False)
        self._btn_rephrase.setEnabled(False)
        self.commands_executed.emit()
