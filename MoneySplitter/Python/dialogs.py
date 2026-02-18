"""Dialog windows for the Money Splitter application."""

from PyQt5.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDialog,
    QDoubleSpinBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)
from PyQt5.QtCore import Qt

from constants import CURRENCIES, DEFAULT_CURRENCY
from models import CellData


# ======================================================================
# Add Person
# ======================================================================
class AddPersonDialog(QDialog):
    """Small dialog: text-box for name + OK button."""

    def __init__(self, existing_names: list, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Add Person")
        self.setMinimumWidth(320)
        self.existing_names_lower = [n.lower() for n in existing_names]
        self.result_name: str | None = None

        layout = QVBoxLayout(self)

        # Warning (hidden until needed)
        self.warning_label = QLabel("")
        self.warning_label.setStyleSheet("color: red; font-weight: bold;")
        self.warning_label.setVisible(False)
        layout.addWidget(self.warning_label)

        layout.addWidget(QLabel("Enter person's name:"))
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("Name…")
        self.name_input.returnPressed.connect(self._on_ok)
        layout.addWidget(self.name_input)

        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self._on_ok)
        layout.addWidget(ok_btn)

    # ------------------------------------------------------------------
    def _on_ok(self):
        name = self.name_input.text().strip()
        if not name:
            self._warn("Name cannot be empty!")
            return
        if name.lower() in self.existing_names_lower:
            self._warn(f"'{name}' already exists!")
            return
        self.result_name = name
        self.accept()

    def _warn(self, msg: str):
        self.warning_label.setText(msg)
        self.warning_label.setVisible(True)


# ======================================================================
# Remove Person
# ======================================================================
class RemovePersonDialog(QDialog):
    """Drop-down of existing names + OK."""

    def __init__(self, existing_names: list, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Remove Person")
        self.setMinimumWidth(320)
        self.result_name: str | None = None

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("Select person to remove:"))

        self.combo = QComboBox()
        self.combo.addItems(existing_names)
        layout.addWidget(self.combo)

        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self._on_ok)
        layout.addWidget(ok_btn)

    def _on_ok(self):
        self.result_name = self.combo.currentText()
        if self.result_name:
            self.accept()


# ======================================================================
# Cell Editor
# ======================================================================
class CellEditorDialog(QDialog):
    """Edit the amount, currency, and person-split of a single cell."""

    def __init__(self, cell_data: CellData, all_people: list, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Edit Expense")
        self.setMinimumWidth(360)
        self.all_people = all_people
        self.result: CellData | None = None

        layout = QVBoxLayout(self)

        # ---- Amount + Currency ------------------------------------
        form = QFormLayout()

        self.amount_input = QDoubleSpinBox()
        self.amount_input.setRange(0.0, 999_999_999.99)
        self.amount_input.setDecimals(2)
        self.amount_input.setSpecialValueText("")
        if cell_data.amount is not None:
            self.amount_input.setValue(cell_data.amount)
        else:
            self.amount_input.setValue(0.0)
        form.addRow("Amount:", self.amount_input)

        self.currency_combo = QComboBox()
        self.currency_combo.addItems(CURRENCIES)
        self.currency_combo.setCurrentText(cell_data.currency)
        form.addRow("Currency:", self.currency_combo)

        layout.addLayout(form)

        # ---- People check-boxes -----------------------------------
        people_group = QGroupBox("Split among:")
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        inner = QWidget()
        people_layout = QVBoxLayout(inner)

        self.checkboxes: dict[str, QCheckBox] = {}
        for person in all_people:
            cb = QCheckBox(person)
            cb.setChecked(person in cell_data.checked_people)
            self.checkboxes[person] = cb
            people_layout.addWidget(cb)

        scroll.setWidget(inner)
        group_layout = QVBoxLayout()
        group_layout.addWidget(scroll)
        people_group.setLayout(group_layout)
        layout.addWidget(people_group)

        # ---- Buttons ----------------------------------------------
        btn_layout = QHBoxLayout()

        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self._on_ok)
        btn_layout.addWidget(ok_btn)

        clear_btn = QPushButton("Clear")
        clear_btn.setToolTip("Reset this cell to empty")
        clear_btn.clicked.connect(self._on_clear)
        btn_layout.addWidget(clear_btn)

        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(cancel_btn)

        layout.addLayout(btn_layout)

    # ------------------------------------------------------------------
    def _on_ok(self):
        amount = self.amount_input.value()
        currency = self.currency_combo.currentText()
        checked = [n for n, cb in self.checkboxes.items() if cb.isChecked()]

        # None checked  ≡  all checked (common pool)
        if not checked:
            checked = list(self.all_people)

        self.result = CellData(
            amount=amount if amount > 0 else None,
            currency=currency,
            checked_people=checked,
        )
        self.accept()

    def _on_clear(self):
        self.result = CellData(checked_people=list(self.all_people))
        self.accept()


# ======================================================================
# Conversion Rates
# ======================================================================
class ConversionRateDialog(QDialog):
    """Edit HUF-per-unit rates for USD and EUR."""

    def __init__(self, conversion_rates: dict, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Conversion Rates")
        self.setMinimumWidth(350)
        self.result_rates: dict | None = None

        layout = QVBoxLayout(self)
        layout.addWidget(
            QLabel("Set how many HUF equals 1 unit of each currency:")
        )

        form = QFormLayout()

        self.usd_input = QDoubleSpinBox()
        self.usd_input.setRange(0.01, 999_999.99)
        self.usd_input.setDecimals(2)
        self.usd_input.setValue(conversion_rates.get("USD", 380.0))
        form.addRow("1 USD =  HUF:", self.usd_input)

        self.eur_input = QDoubleSpinBox()
        self.eur_input.setRange(0.01, 999_999.99)
        self.eur_input.setDecimals(2)
        self.eur_input.setValue(conversion_rates.get("EUR", 410.0))
        form.addRow("1 EUR =  HUF:", self.eur_input)

        layout.addLayout(form)

        btn_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self._on_save)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(cancel_btn)
        layout.addLayout(btn_layout)

    def _on_save(self):
        self.result_rates = {
            "USD": self.usd_input.value(),
            "EUR": self.eur_input.value(),
        }
        self.accept()
