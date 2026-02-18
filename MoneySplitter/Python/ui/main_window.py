"""Main application window for the Money Splitter."""

from PyQt5.QtWidgets import (
    QAbstractItemView,
    QAction,
    QComboBox,
    QDialog,
    QFileDialog,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QMainWindow,
    QMenu,
    QMessageBox,
    QPushButton,
    QSizePolicy,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QBrush, QColor, QFont, QIcon, QPixmap

import os
import sys

from logic.constants import (
    APP_NAME,
    BALANCE_NEGATIVE,
    BALANCE_POSITIVE,
    BRAND,
    DEFAULT_BG,
    DRACULA_BG,
    DRACULA_COMMENT,
    DRACULA_CURRENT,
    DRACULA_CYAN,
    DRACULA_FG,
    DRACULA_GREEN,
    DRACULA_ORANGE,
    DRACULA_PINK,
    DRACULA_PURPLE,
    PARTIAL_SPLIT_BG,
    VERSION,
    get_currency_color,
)
from data.models import CellData, TripData
from ui.dialogs import (
    AddPersonDialog,
    CellEditorDialog,
    ConversionRateDialog,
    ManageCurrenciesDialog,
    RemovePersonDialog,
)
from logic.calculator import calculate_balances
from data.persistence import load_trip, save_trip

# =====================================================================
# Global Dracula stylesheet (applied to the entire QMainWindow)
# =====================================================================
DRACULA_STYLESHEET = f"""
/* ---- base ---- */
QMainWindow, QWidget {{
    background-color: {DRACULA_BG};
    color: {DRACULA_FG};
    font-family: 'Segoe UI';
}}
QMenuBar {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
}}
QMenuBar::item:selected {{
    background-color: {DRACULA_COMMENT};
}}
QMenu {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
    border: 1px solid {DRACULA_COMMENT};
}}
QMenu::item:selected {{
    background-color: {DRACULA_PURPLE};
}}

/* ---- buttons ---- */
QPushButton {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
    border: 1px solid {DRACULA_COMMENT};
    border-radius: 4px;
    padding: 6px 12px;
}}
QPushButton:hover {{
    background-color: {DRACULA_COMMENT};
}}
QPushButton:pressed {{
    background-color: {DRACULA_PURPLE};
}}

/* ---- table ---- */
QTableWidget {{
    background-color: {DRACULA_BG};
    color: {DRACULA_FG};
    gridline-color: {DRACULA_COMMENT};
    selection-background-color: {DRACULA_CURRENT};
    selection-color: {DRACULA_FG};
    border: 1px solid {DRACULA_COMMENT};
}}
QHeaderView::section {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_CYAN};
    border: 1px solid {DRACULA_COMMENT};
    padding: 4px;
    font-weight: bold;
}}

/* ---- inputs ---- */
QComboBox {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
    border: 1px solid {DRACULA_COMMENT};
    border-radius: 3px;
    padding: 4px;
}}
QComboBox QAbstractItemView {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
    selection-background-color: {DRACULA_PURPLE};
}}
QComboBox::drop-down {{
    border: none;
}}
QLineEdit, QDoubleSpinBox, QSpinBox {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
    border: 1px solid {DRACULA_COMMENT};
    border-radius: 3px;
    padding: 4px;
}}

/* ---- labels ---- */
QLabel {{
    color: {DRACULA_FG};
}}

/* ---- scroll area ---- */
QScrollArea {{
    border: none;
}}
QScrollBar:vertical {{
    background: {DRACULA_BG};
    width: 12px;
}}
QScrollBar::handle:vertical {{
    background: {DRACULA_COMMENT};
    border-radius: 4px;
    min-height: 20px;
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0;
}}

/* ---- status bar ---- */
QStatusBar {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_COMMENT};
}}

/* ---- group box ---- */
QGroupBox {{
    color: {DRACULA_PURPLE};
    border: 1px solid {DRACULA_COMMENT};
    border-radius: 4px;
    margin-top: 8px;
    padding-top: 14px;
}}
QGroupBox::title {{
    subcontrol-origin: margin;
    left: 10px;
    padding: 0 4px;
}}

/* ---- check box ---- */
QCheckBox {{
    color: {DRACULA_FG};
    spacing: 6px;
}}
QCheckBox::indicator {{
    width: 16px;
    height: 16px;
    border: 1px solid {DRACULA_COMMENT};
    border-radius: 3px;
    background-color: {DRACULA_BG};
}}
QCheckBox::indicator:checked {{
    background-color: {DRACULA_PURPLE};
    border-color: {DRACULA_PURPLE};
}}

/* ---- tooltip ---- */
QToolTip {{
    background-color: {DRACULA_CURRENT};
    color: {DRACULA_FG};
    border: 1px solid {DRACULA_COMMENT};
    padding: 4px;
}}
"""


class MainWindow(QMainWindow):
    """Top-level window that hosts the expense table, balance bar, and controls."""

    def __init__(self) -> None:
        super().__init__()
        self.trip = TripData()
        self.current_file: str | None = None
        self._build_ui()

    # ==================================================================
    # UI construction
    # ==================================================================
    def _build_ui(self) -> None:
        self.setWindowTitle(f"{BRAND} — {APP_NAME}  v{VERSION}")
        self._set_window_icon()
        self.setMinimumSize(850, 620)
        self.resize(1050, 720)
        self.setStyleSheet(DRACULA_STYLESHEET)

        self._build_menu_bar()

        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)

        # ---- Header (MS logo + name + version) -----------------------
        hdr_layout = QVBoxLayout()
        hdr_layout.setAlignment(Qt.AlignCenter)
        hdr_layout.setSpacing(2)

        logo_path = self._resolve_logo("MoneySplitter_logo.png")
        if logo_path:
            logo_lbl = QLabel()
            logo_lbl.setAlignment(Qt.AlignCenter)
            logo_pm = QPixmap(logo_path).scaled(
                280, 90, Qt.KeepAspectRatio, Qt.SmoothTransformation
            )
            logo_lbl.setPixmap(logo_pm)
            hdr_layout.addWidget(logo_lbl)

        ver_lbl = QLabel(f"v{VERSION}")
        ver_lbl.setFont(QFont("Segoe UI", 8))
        ver_lbl.setAlignment(Qt.AlignCenter)
        ver_lbl.setStyleSheet(f"color: {DRACULA_COMMENT};")
        hdr_layout.addWidget(ver_lbl)

        root.addLayout(hdr_layout)

        # ---- Top section: expense table  +  side panel ----------------
        top = QHBoxLayout()

        # Expense table
        self.expense_table = QTableWidget(0, 0)
        self.expense_table.setSelectionMode(QAbstractItemView.ExtendedSelection)
        self.expense_table.setContextMenuPolicy(Qt.CustomContextMenu)
        self.expense_table.customContextMenuRequested.connect(
            self._on_expense_ctx_menu
        )
        self.expense_table.cellDoubleClicked.connect(self._on_cell_dbl_click)
        self.expense_table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.expense_table.horizontalHeader().setSectionResizeMode(
            QHeaderView.Stretch
        )
        self.expense_table.verticalHeader().setDefaultSectionSize(28)
        self.expense_table.verticalHeader().sectionClicked.connect(
            self._on_row_header_clicked
        )
        top.addWidget(self.expense_table, stretch=5)

        # Side-panel (buttons + dropdown)
        side = QVBoxLayout()

        self.add_person_btn = QPushButton("Add Person")
        self.add_person_btn.setMinimumHeight(36)
        self.add_person_btn.clicked.connect(self._on_add_person)
        side.addWidget(self.add_person_btn)

        self.rm_person_btn = QPushButton("Remove Person")
        self.rm_person_btn.setMinimumHeight(36)
        self.rm_person_btn.clicked.connect(self._on_remove_person)
        side.addWidget(self.rm_person_btn)

        side.addSpacing(16)

        conv_row = QHBoxLayout()
        self.conv_btn = QPushButton("Conversion Rates")
        self.conv_btn.setMinimumHeight(36)
        self.conv_btn.clicked.connect(self._on_conv_rates)
        conv_row.addWidget(self.conv_btn)

        self.fetch_rates_btn = QPushButton("\U0001F310")
        self.fetch_rates_btn.setToolTip("Fetch live rates from the internet")
        self.fetch_rates_btn.setMinimumHeight(36)
        self.fetch_rates_btn.setFixedWidth(36)
        self.fetch_rates_btn.clicked.connect(self._on_fetch_rates)
        conv_row.addWidget(self.fetch_rates_btn)
        side.addLayout(conv_row)

        self.manage_cur_btn = QPushButton("Manage Currencies")
        self.manage_cur_btn.setMinimumHeight(36)
        self.manage_cur_btn.clicked.connect(self._on_manage_currencies)
        side.addWidget(self.manage_cur_btn)

        side.addSpacing(16)

        res_label = QLabel("Result currency:")
        res_label.setStyleSheet(f"color: {DRACULA_COMMENT};")
        side.addWidget(res_label)
        self.result_currency_combo = QComboBox()
        self._refresh_currency_combo()
        side.addWidget(self.result_currency_combo)

        side.addStretch()

        # ---- Save / Load quick-access buttons ----
        self.save_btn = QPushButton("Save")
        self.save_btn.setMinimumHeight(36)
        self.save_btn.clicked.connect(self._on_save)
        side.addWidget(self.save_btn)

        self.load_btn = QPushButton("Load")
        self.load_btn.setMinimumHeight(36)
        self.load_btn.clicked.connect(self._on_open)
        side.addWidget(self.load_btn)

        side.addSpacing(8)

        # CALCULATE — always at the very bottom
        self.calc_btn = QPushButton("CALCULATE")
        self.calc_btn.setMinimumHeight(54)
        self.calc_btn.setFont(QFont("Segoe UI", 13, QFont.Bold))
        self.calc_btn.setStyleSheet(
            f"QPushButton {{ background-color: {DRACULA_GREEN}; color: {DRACULA_BG};"
            f" border-radius: 6px; border: none; }}"
            f"QPushButton:hover {{ background-color: #69d97a; }}"
            f"QPushButton:pressed {{ background-color: #3fcf5e; }}"
        )
        self.calc_btn.clicked.connect(self._on_calculate)
        side.addWidget(self.calc_btn)

        side_widget = QWidget()
        side_widget.setLayout(side)
        side_widget.setFixedWidth(180)
        top.addWidget(side_widget)

        root.addLayout(top, stretch=5)

        # ---- Balance table --------------------------------------------
        bal_label = QLabel("Balances:")
        bal_label.setStyleSheet(f"color: {DRACULA_PURPLE}; font-weight: bold;")
        root.addWidget(bal_label)

        self.balance_table = QTableWidget(1, 0)
        self.balance_table.setFixedHeight(45)
        self.balance_table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.balance_table.horizontalHeader().setSectionResizeMode(
            QHeaderView.Stretch
        )
        self.balance_table.verticalHeader().setVisible(False)
        root.addWidget(self.balance_table)

        # ---- Status bar -----------------------------------------------
        self.statusBar().showMessage("Ready — no file loaded")
        self.statusBar().setStyleSheet(
            f"color: {DRACULA_COMMENT}; background-color: {DRACULA_CURRENT};"
        )

    # ------------------------------------------------------------------
    @staticmethod
    def _resolve_logo(name: str) -> str | None:
        """Find a logo file by *name*.  Works in dev and PyInstaller builds."""
        if getattr(sys, "frozen", False):
            path = os.path.join(sys._MEIPASS, name)  # type: ignore[attr-defined]
            return path if os.path.isfile(path) else None

        # Dev mode – __file__ lives in  ui/  so project root is one level up
        project_root = os.path.dirname(
            os.path.dirname(os.path.abspath(__file__))
        )
        # logo_MS.png lives in project root
        path = os.path.join(project_root, name)
        if os.path.isfile(path):
            return path

        # logo_CsT.png lives in  <repo>/Common/
        repo_root = os.path.dirname(os.path.dirname(project_root))
        path = os.path.join(repo_root, "Common", name)
        if os.path.isfile(path):
            return path

        return None

    # ------------------------------------------------------------------
    def _set_window_icon(self) -> None:
        """Set the window icon from logo_MS.png (works for dev and PyInstaller)."""
        icon_path = self._resolve_logo("logo_MS.png")
        if icon_path:
            pixmap = QPixmap(icon_path)
            icon = QIcon()
            icon.addPixmap(pixmap.scaled(256, 256, Qt.KeepAspectRatio, Qt.SmoothTransformation))
            self.setWindowIcon(icon)

    # ------------------------------------------------------------------
    def _build_menu_bar(self) -> None:
        mb = self.menuBar()
        fm = mb.addMenu("File")

        self._add_action(fm, "New Trip", "Ctrl+N", self._on_new_trip)
        self._add_action(fm, "Open…", "Ctrl+O", self._on_open)
        self._add_action(fm, "Save", "Ctrl+S", self._on_save)
        self._add_action(fm, "Save As…", "Ctrl+Shift+S", self._on_save_as)
        fm.addSeparator()
        self._add_action(fm, "Exit", "Alt+F4", self.close)

    @staticmethod
    def _add_action(menu, text, shortcut, slot):
        act = QAction(text, menu)
        act.setShortcut(shortcut)
        act.triggered.connect(slot)
        menu.addAction(act)

    # ==================================================================
    # Row-header click  (select entire row like Excel)
    # ==================================================================
    def _on_row_header_clicked(self, logical_index: int) -> None:
        self.expense_table.selectRow(logical_index)

    # ==================================================================
    # Person management
    # ==================================================================
    def _on_add_person(self) -> None:
        dlg = AddPersonDialog(self.trip.people, self)
        if dlg.exec_() == QDialog.Accepted and dlg.result_name:
            self.trip.add_person(dlg.result_name)
            self._refresh_all()

    def _on_remove_person(self) -> None:
        if not self.trip.people:
            QMessageBox.warning(
                self, "No People", "There are no people to remove."
            )
            return
        dlg = RemovePersonDialog(self.trip.people, self)
        if dlg.exec_() == QDialog.Accepted and dlg.result_name:
            self.trip.remove_person(dlg.result_name)
            self._refresh_all()

    # ==================================================================
    # Cell double-click → editor
    # ==================================================================
    def _on_cell_dbl_click(self, row: int, col: int) -> None:
        if col >= len(self.trip.people) or row >= len(self.trip.expenses):
            return
        cell = self.trip.expenses[row][col]
        dlg = CellEditorDialog(
            cell, self.trip.people, self.trip.currencies, self
        )
        if dlg.exec_() == QDialog.Accepted and dlg.result is not None:
            self.trip.expenses[row][col] = dlg.result
            self._refresh_expense_table()

    # ==================================================================
    # Context menu (add / delete rows)
    # ==================================================================
    def _on_expense_ctx_menu(self, pos) -> None:
        menu = QMenu(self)

        # "Edit Cell" — only when exactly one cell is selected
        selected = self.expense_table.selectedIndexes()
        edit_action = None
        if len(selected) == 1:
            edit_action = menu.addAction("Edit Cell")

        add_action = menu.addAction("Add Row")

        # Collect selected rows (any cell in the row counts)
        selected_rows = {idx.row() for idx in selected}

        del_action = None
        if selected_rows:
            n = len(selected_rows)
            del_action = menu.addAction(
                f"Delete {n} Selected Row{'s' if n > 1 else ''}"
            )

        action = menu.exec_(
            self.expense_table.viewport().mapToGlobal(pos)
        )

        if edit_action and action == edit_action:
            idx = selected[0]
            self._on_cell_dbl_click(idx.row(), idx.column())
        elif action == add_action:
            self.trip.add_row()
            self._refresh_expense_table()
        elif del_action and action == del_action:
            self.trip.remove_rows(list(selected_rows))
            self._refresh_expense_table()

    # ==================================================================
    # Calculate
    # ==================================================================
    def _on_calculate(self) -> None:
        self.trip.result_currency = self.result_currency_combo.currentText()
        balances = calculate_balances(self.trip)
        self._refresh_balance_table(balances)

    # ==================================================================
    # Conversion rates
    # ==================================================================
    def _on_conv_rates(self) -> None:
        dlg = ConversionRateDialog(
            self.trip.currencies,
            self.trip.base_currency,
            self.trip.conversion_rates,
            self,
        )
        if dlg.exec_() == QDialog.Accepted:
            if dlg.result_rates is not None:
                self.trip.conversion_rates = dlg.result_rates
            if dlg.result_base and dlg.result_base != self.trip.base_currency:
                self.trip.base_currency = dlg.result_base
                self._refresh_currency_combo()

    # ==================================================================
    # Fetch live rates from the internet
    # ==================================================================
    def _on_fetch_rates(self) -> None:
        """Fetch live exchange rates from open.er-api.com."""
        import json
        import ssl
        import urllib.request
        import urllib.error

        base = self.trip.base_currency
        others = [c for c in self.trip.currencies if c != base]
        if not others:
            QMessageBox.information(
                self, "Nothing to fetch",
                "Only the base currency exists — no rates to look up.",
            )
            return

        url = f"https://open.er-api.com/v6/latest/{base}"

        # Build an SSL context — try default first, fall back to unverified
        # (some corporate proxies replace certificates).
        ssl_ctx = None
        try:
            ssl_ctx = ssl.create_default_context()
        except Exception:
            pass

        self.statusBar().showMessage("Fetching live rates…")
        try:
            req = urllib.request.Request(url)
            resp = urllib.request.urlopen(req, timeout=10, context=ssl_ctx)
            data = json.loads(resp.read().decode())
        except (urllib.error.URLError, OSError) as first_err:
            # Retry with SSL verification disabled
            try:
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE
                resp = urllib.request.urlopen(req, timeout=10, context=ctx)
                data = json.loads(resp.read().decode())
            except (urllib.error.URLError, OSError, ValueError) as exc:
                QMessageBox.warning(
                    self, "Fetch failed",
                    f"Could not retrieve rates:\n{exc}",
                )
                self.statusBar().showMessage("Rate fetch failed")
                return
        except ValueError as exc:
            QMessageBox.warning(
                self, "Fetch failed",
                f"Invalid response:\n{exc}",
            )
            self.statusBar().showMessage("Rate fetch failed")
            return

        api_rates = data.get("rates", {})
        if not api_rates:
            QMessageBox.warning(
                self, "No data",
                "The API returned no rate data.\n"
                "Some exotic currency codes may not be supported.",
            )
            self.statusBar().showMessage("No rate data returned")
            return

        # The API returns: 1 BASE = X TARGET.
        # Our model stores: 1 TARGET = Y BASE (inverted).
        updated = []
        for cur in others:
            api_rate = api_rates.get(cur)
            if api_rate and api_rate > 0:
                inverted = round(1.0 / api_rate, 4)
                self.trip.conversion_rates[cur] = inverted
                updated.append(f"1 {cur} = {inverted:,.4f} {base}")

        missing = [c for c in others if c not in api_rates]
        msg = "Updated:\n" + "\n".join(updated)
        if missing:
            msg += "\n\nNot found (kept old rate): " + ", ".join(missing)

        QMessageBox.information(self, "Rates fetched", msg)
        self.statusBar().showMessage(
            f"Live rates fetched ({len(updated)} currencies)"
        )

    # ==================================================================
    # Manage currencies
    # ==================================================================
    def _on_manage_currencies(self) -> None:
        dlg = ManageCurrenciesDialog(
            self.trip.currencies,
            self.trip.base_currency,
            self.trip.conversion_rates,
            self,
        )
        if dlg.exec_() == QDialog.Accepted:
            if dlg.result_currencies is not None:
                self.trip.currencies = dlg.result_currencies
            if dlg.result_rates is not None:
                self.trip.conversion_rates = dlg.result_rates
            self._refresh_currency_combo()

    # ==================================================================
    # File operations
    # ==================================================================
    def _on_new_trip(self) -> None:
        self.trip = TripData()
        self.current_file = None
        self._refresh_all()
        self.statusBar().showMessage("New trip created")

    def _on_open(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self, "Open Trip", "", "JSON Files (*.json);;All Files (*)"
        )
        if not path:
            return
        trip = load_trip(path)
        if trip:
            self.trip = trip
            self.current_file = path
            self._refresh_all()
            self.statusBar().showMessage(f"Loaded: {path}")
        else:
            QMessageBox.critical(self, "Error", f"Failed to load\n{path}")

    def _on_save(self) -> None:
        if self.current_file:
            if save_trip(self.trip, self.current_file):
                self.statusBar().showMessage(f"Saved: {self.current_file}")
        else:
            self._on_save_as()

    def _on_save_as(self) -> None:
        path, _ = QFileDialog.getSaveFileName(
            self, "Save Trip As", "", "JSON Files (*.json);;All Files (*)"
        )
        if not path:
            return
        if not path.endswith(".json"):
            path += ".json"
        if save_trip(self.trip, path):
            self.current_file = path
            self.statusBar().showMessage(f"Saved: {path}")
        else:
            QMessageBox.critical(self, "Error", f"Failed to save\n{path}")

    # ==================================================================
    # Table refresh helpers
    # ==================================================================
    def _refresh_all(self) -> None:
        self._refresh_currency_combo()
        self._refresh_expense_table()
        self._refresh_balance_table()

    def _refresh_expense_table(self) -> None:
        people = self.trip.people
        rows = self.trip.expenses

        self.expense_table.setColumnCount(len(people))
        self.expense_table.setHorizontalHeaderLabels(people)
        self.expense_table.setRowCount(len(rows))

        for r, row in enumerate(rows):
            for c, cell in enumerate(row):
                self.expense_table.setItem(
                    r, c, self._make_expense_item(cell, people)
                )

    def _refresh_balance_table(self, balances: dict | None = None) -> None:
        people = self.trip.people
        self.balance_table.setColumnCount(len(people))
        self.balance_table.setHorizontalHeaderLabels(people)
        self.balance_table.setRowCount(1)

        currency = self.trip.result_currency

        for c, name in enumerate(people):
            if balances is None:
                self.balance_table.setItem(0, c, QTableWidgetItem(""))
                continue

            val = balances.get(name, 0.0)
            text = f"{val:+,.2f} {currency}"
            item = QTableWidgetItem(text)
            item.setTextAlignment(Qt.AlignRight | Qt.AlignVCenter)

            if val > 0.005:
                item.setForeground(QBrush(QColor(BALANCE_POSITIVE)))
            elif val < -0.005:
                item.setForeground(QBrush(QColor(BALANCE_NEGATIVE)))

            self.balance_table.setItem(0, c, item)

    def _refresh_currency_combo(self) -> None:
        """Rebuild the result-currency dropdown from the trip's currency list."""
        prev = self.result_currency_combo.currentText()
        self.result_currency_combo.blockSignals(True)
        self.result_currency_combo.clear()
        self.result_currency_combo.addItems(self.trip.currencies)
        if prev in self.trip.currencies:
            self.result_currency_combo.setCurrentText(prev)
        else:
            self.result_currency_combo.setCurrentText(self.trip.base_currency)
        self.result_currency_combo.blockSignals(False)

    # ------------------------------------------------------------------
    @staticmethod
    def _make_expense_item(
        cell: CellData, all_people: list
    ) -> QTableWidgetItem:
        """Build a styled QTableWidgetItem for one cell."""
        text = f"{cell.amount:,.2f}" if cell.amount and cell.amount > 0 else ""

        item = QTableWidgetItem(text)
        item.setTextAlignment(Qt.AlignRight | Qt.AlignVCenter)

        # Text colour → currency
        colour = get_currency_color(cell.currency)
        item.setForeground(QBrush(QColor(colour)))

        # Background → pale purple when not everyone is included
        if cell.amount and cell.amount > 0:
            if not cell.is_all_checked(all_people):
                item.setBackground(QBrush(QColor(PARTIAL_SPLIT_BG)))
            else:
                item.setBackground(QBrush(QColor(DEFAULT_BG)))

        return item
