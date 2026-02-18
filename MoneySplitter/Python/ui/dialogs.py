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
    QMessageBox,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)
from PyQt5.QtCore import Qt

from logic.constants import DEFAULT_BASE_CURRENCY, DRACULA_RED
from data.models import CellData


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
        self.warning_label.setStyleSheet(f"color: {DRACULA_RED}; font-weight: bold;")
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

    def __init__(
        self,
        cell_data: CellData,
        all_people: list,
        currencies: list,
        parent=None,
    ):
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
        self.currency_combo.addItems(currencies)
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

        sel_btn_row = QHBoxLayout()
        sel_all_btn = QPushButton("Select All")
        sel_all_btn.clicked.connect(self._on_select_all)
        sel_none_btn = QPushButton("Select None")
        sel_none_btn.clicked.connect(self._on_select_none)
        sel_btn_row.addWidget(sel_all_btn)
        sel_btn_row.addWidget(sel_none_btn)
        group_layout.addLayout(sel_btn_row)

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

    def _on_select_all(self):
        for cb in self.checkboxes.values():
            cb.setChecked(True)

    def _on_select_none(self):
        for cb in self.checkboxes.values():
            cb.setChecked(False)


# ======================================================================
# Conversion Rates
# ======================================================================
class ConversionRateDialog(QDialog):
    """Edit conversion rates for all non-base currencies.

    Also allows switching the base currency — rates are recalculated
    automatically by the TripData model.
    """

    def __init__(
        self,
        currencies: list,
        base_currency: str,
        conversion_rates: dict,
        parent=None,
    ):
        super().__init__(parent)
        self.setWindowTitle("Conversion Rates")
        self.setMinimumWidth(400)

        self.currencies = list(currencies)
        self.base_currency = base_currency
        self.result_rates: dict | None = None
        self.result_base: str | None = None

        layout = QVBoxLayout(self)

        # ---- Base currency selector --------------------------------
        base_row = QHBoxLayout()
        base_row.addWidget(QLabel("Base currency:"))
        self.base_combo = QComboBox()
        self.base_combo.addItems(self.currencies)
        self.base_combo.setCurrentText(self.base_currency)
        base_row.addWidget(self.base_combo)
        layout.addLayout(base_row)

        layout.addSpacing(8)
        self.desc_label = QLabel()
        layout.addWidget(self.desc_label)

        # ---- Dynamic rate inputs -----------------------------------
        self.form = QFormLayout()
        self.rate_inputs: dict[str, QDoubleSpinBox] = {}
        layout.addLayout(self.form)

        self._rebuild_rate_fields(conversion_rates)

        self.base_combo.currentTextChanged.connect(self._on_base_changed)

        # ---- Buttons -----------------------------------------------
        layout.addSpacing(12)
        btn_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self._on_save)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(cancel_btn)
        layout.addLayout(btn_layout)

    # ------------------------------------------------------------------
    def _rebuild_rate_fields(self, rates: dict) -> None:
        """(Re)create spin-boxes for each non-base currency."""
        # Clear old widgets
        while self.form.rowCount():
            self.form.removeRow(0)
        self.rate_inputs.clear()

        base = self.base_combo.currentText()
        self.desc_label.setText(
            f"Set how many {base} equals 1 unit of each currency:"
        )

        for cur in self.currencies:
            if cur == base:
                continue
            spin = QDoubleSpinBox()
            spin.setRange(0.0001, 999_999_999.99)
            spin.setDecimals(4)
            spin.setValue(rates.get(cur, 1.0))
            self.rate_inputs[cur] = spin
            self.form.addRow(f"1 {cur} = {base}:", spin)

    def _on_base_changed(self, new_base: str) -> None:
        """Recalculate rates when the user picks a different base currency."""
        old_base = self.base_currency
        if new_base == old_base:
            return

        # Collect current spin-box values
        old_rates = {cur: spin.value() for cur, spin in self.rate_inputs.items()}
        pivot = old_rates.get(new_base, 1.0)

        new_rates: dict[str, float] = {}
        for cur in self.currencies:
            if cur == new_base:
                continue
            if cur == old_base:
                new_rates[cur] = round(1.0 / pivot, 4) if pivot else 1.0
            else:
                old_rate = old_rates.get(cur, 1.0)
                new_rates[cur] = round(old_rate / pivot, 4) if pivot else old_rate

        self.base_currency = new_base
        self._rebuild_rate_fields(new_rates)

    # ------------------------------------------------------------------
    def _on_save(self):
        self.result_rates = {
            cur: spin.value() for cur, spin in self.rate_inputs.items()
        }
        self.result_base = self.base_combo.currentText()
        self.accept()


# ======================================================================
# Manage Currencies
# ======================================================================
class ManageCurrenciesDialog(QDialog):
    """Add or remove currencies from the trip's currency list."""

    def __init__(
        self,
        currencies: list,
        base_currency: str,
        conversion_rates: dict,
        parent=None,
    ):
        super().__init__(parent)
        self.setWindowTitle("Manage Currencies")
        self.setMinimumWidth(400)

        self._currencies = list(currencies)
        self._base = base_currency
        self._rates = dict(conversion_rates)

        # Public results — set on accept
        self.result_currencies: list | None = None
        self.result_rates: dict | None = None

        layout = QVBoxLayout(self)

        # ---- Current currencies list -------------------------------
        self.list_group = QGroupBox("Current currencies")
        self.list_layout = QVBoxLayout()
        self.list_group.setLayout(self.list_layout)
        layout.addWidget(self.list_group)
        self._rebuild_currency_list()

        # ---- Add new currency --------------------------------------
        add_group = QGroupBox("Add new currency")
        add_layout = QHBoxLayout()
        self.new_code_input = QLineEdit()
        self.new_code_input.setPlaceholderText("Code (e.g. GBP)")
        self.new_code_input.setMaxLength(5)
        add_layout.addWidget(self.new_code_input)

        self.new_rate_input = QDoubleSpinBox()
        self.new_rate_input.setRange(0.0001, 999_999_999.99)
        self.new_rate_input.setDecimals(4)
        self.new_rate_input.setValue(1.0)
        self.new_rate_input.setPrefix(f"1 unit = ")
        self.new_rate_input.setSuffix(f" {self._base}")
        add_layout.addWidget(self.new_rate_input)

        add_btn = QPushButton("Add")
        add_btn.clicked.connect(self._on_add_currency)
        add_layout.addWidget(add_btn)

        add_group.setLayout(add_layout)
        layout.addWidget(add_group)

        # ---- OK / Cancel -------------------------------------------
        btn_layout = QHBoxLayout()
        ok_btn = QPushButton("OK")
        ok_btn.clicked.connect(self._on_ok)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(ok_btn)
        btn_layout.addWidget(cancel_btn)
        layout.addLayout(btn_layout)

    # ------------------------------------------------------------------
    def _rebuild_currency_list(self) -> None:
        # Clear
        while self.list_layout.count():
            item = self.list_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        for cur in self._currencies:
            row = QHBoxLayout()
            label_text = cur
            if cur == self._base:
                label_text += "  (base)"
            row.addWidget(QLabel(label_text))
            row.addStretch()

            if cur != self._base:
                rm_btn = QPushButton("Remove")
                rm_btn.setFixedWidth(70)
                rm_btn.clicked.connect(lambda checked, c=cur: self._on_remove(c))
                row.addWidget(rm_btn)

            container = QWidget()
            container.setLayout(row)
            self.list_layout.addWidget(container)

    def _on_add_currency(self) -> None:
        code = self.new_code_input.text().strip().upper()
        if not code:
            return
        if code in self._currencies:
            QMessageBox.warning(self, "Duplicate", f"{code} already exists.")
            return
        rate = self.new_rate_input.value()
        self._currencies.append(code)
        self._rates[code] = rate
        self.new_code_input.clear()
        self._rebuild_currency_list()

    def _on_remove(self, code: str) -> None:
        if code in self._currencies:
            self._currencies.remove(code)
        self._rates.pop(code, None)
        self._rebuild_currency_list()

    def _on_ok(self) -> None:
        self.result_currencies = list(self._currencies)
        self.result_rates = dict(self._rates)
        self.accept()
