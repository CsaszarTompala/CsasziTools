"""
Settings dialog — theme selection, GPT API key, model choice.
"""

from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QFormLayout, QLabel,
    QLineEdit, QComboBox, QPushButton, QGroupBox, QWidget,
    QApplication,
)
from PyQt6.QtGui import QFont
from PyQt6.QtCore import Qt

from csaszigit.settings import SettingsManager
from csaszigit.themes import THEME_NAMES, THEME_LABELS, apply_theme


class SettingsDialog(QDialog):
    """Modal settings dialog."""

    def __init__(self, settings: SettingsManager, parent=None):
        super().__init__(parent)
        self._settings = settings
        self.setWindowTitle("Settings")
        self.setMinimumWidth(480)
        self.setModal(True)

        lay = QVBoxLayout(self)
        lay.setSpacing(12)

        # ── Appearance ───────────────────────────────────────
        grp_theme = QGroupBox("Appearance")
        form_theme = QFormLayout(grp_theme)

        self._theme_combo = QComboBox()
        for name in THEME_NAMES:
            self._theme_combo.addItem(THEME_LABELS[name], name)
        idx = THEME_NAMES.index(settings.theme) if settings.theme in THEME_NAMES else 0
        self._theme_combo.setCurrentIndex(idx)
        form_theme.addRow("Theme:", self._theme_combo)

        lay.addWidget(grp_theme)

        # ── OpenAI / GPT ─────────────────────────────────────
        grp_gpt = QGroupBox("AI Assistant (OpenAI)")
        form_gpt = QFormLayout(grp_gpt)

        self._key_edit = QLineEdit(settings.gpt_api_key)
        self._key_edit.setPlaceholderText("sk-…")
        self._key_edit.setEchoMode(QLineEdit.EchoMode.Password)
        form_gpt.addRow("API Key:", self._key_edit)

        self._model_combo = QComboBox()
        self._model_combo.setEditable(True)
        models = ["gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-4.1", "o4-mini"]
        self._model_combo.addItems(models)
        current_model = settings.gpt_model
        idx_m = models.index(current_model) if current_model in models else -1
        if idx_m >= 0:
            self._model_combo.setCurrentIndex(idx_m)
        else:
            self._model_combo.setCurrentText(current_model)
        form_gpt.addRow("Model:", self._model_combo)

        note = QLabel(
            "The AI assistant lets you describe Git operations in plain English.\n"
            "GPT will suggest the exact commands and you can approve or rephrase."
        )
        note.setWordWrap(True)
        note.setStyleSheet("color: #6272A4; font-size: 11px;")
        form_gpt.addRow(note)

        lay.addWidget(grp_gpt)

        # ── Buttons ──────────────────────────────────────────
        btn_row = QHBoxLayout()
        btn_row.addStretch()
        btn_cancel = QPushButton("Cancel")
        btn_cancel.clicked.connect(self.reject)
        btn_row.addWidget(btn_cancel)
        btn_save = QPushButton("Save")
        btn_save.setProperty("accent", True)
        btn_save.clicked.connect(self._save)
        btn_row.addWidget(btn_save)
        lay.addLayout(btn_row)

    def _save(self):
        theme_data = self._theme_combo.currentData()
        self._settings.theme = theme_data
        self._settings.gpt_api_key = self._key_edit.text().strip()
        self._settings.gpt_model = self._model_combo.currentText().strip()

        # Apply theme immediately
        app = QApplication.instance()
        if app:
            apply_theme(app, theme_data)

        self.accept()
