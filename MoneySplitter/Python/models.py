"""Data models for the Money Splitter application."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional

from constants import DEFAULT_CONVERSION_RATES, DEFAULT_CURRENCY, DEFAULT_ROW_COUNT


@dataclass
class CellData:
    """A single expense cell in the table."""

    amount: Optional[float] = None
    currency: str = DEFAULT_CURRENCY
    checked_people: List[str] = field(default_factory=list)

    # ------------------------------------------------------------------
    def is_all_checked(self, all_people: List[str]) -> bool:
        """Return *True* when every person in *all_people* is checked."""
        return set(self.checked_people) >= set(all_people)

    # ------------------------------------------------------------------
    def to_dict(self) -> dict:
        """Serialise to a plain dict (JSON-friendly)."""
        return {
            "amount": self.amount,
            "currency": self.currency,
            "checked_people": list(self.checked_people),
        }

    @classmethod
    def from_dict(cls, data: dict) -> CellData:
        """Deserialise from a plain dict."""
        return cls(
            amount=data.get("amount"),
            currency=data.get("currency", DEFAULT_CURRENCY),
            checked_people=data.get("checked_people", []),
        )


@dataclass
class TripData:
    """All data for one trip / money-splitting session."""

    people: List[str] = field(default_factory=list)
    expenses: List[List[CellData]] = field(default_factory=list)
    conversion_rates: Dict[str, float] = field(
        default_factory=lambda: dict(DEFAULT_CONVERSION_RATES)
    )
    result_currency: str = DEFAULT_CURRENCY

    # ------------------------------------------------------------------
    # Person management
    # ------------------------------------------------------------------
    def add_person(self, name: str) -> None:
        """Add a person column and back-fill existing cells."""
        old_people = set(self.people)
        self.people.append(name)

        for row in self.expenses:
            # Existing cells: auto-check new person only when *all* old
            # people were already checked (i.e. it was a common-pool entry).
            for cell in row:
                if not old_people or set(cell.checked_people) >= old_people:
                    cell.checked_people.append(name)
            # New empty cell for the new person (all people checked).
            row.append(CellData(checked_people=list(self.people)))

        # Guarantee at least DEFAULT_ROW_COUNT rows.
        while len(self.expenses) < DEFAULT_ROW_COUNT:
            self._append_empty_row()

    def remove_person(self, name: str) -> None:
        """Remove a person column and update all cells."""
        if name not in self.people:
            return
        idx = self.people.index(name)
        self.people.remove(name)
        for row in self.expenses:
            if idx < len(row):
                row.pop(idx)
            for cell in row:
                if name in cell.checked_people:
                    cell.checked_people.remove(name)

    # ------------------------------------------------------------------
    # Row management
    # ------------------------------------------------------------------
    def add_row(self) -> None:
        """Add a new empty row (one cell per person, all checked)."""
        self._append_empty_row()

    def remove_rows(self, row_indices: List[int]) -> None:
        """Remove rows by index (safe for out-of-range)."""
        for idx in sorted(row_indices, reverse=True):
            if 0 <= idx < len(self.expenses):
                self.expenses.pop(idx)

    # ------------------------------------------------------------------
    # Serialisation
    # ------------------------------------------------------------------
    def to_dict(self) -> dict:
        return {
            "version": VERSION_TAG,
            "people": list(self.people),
            "expenses": [
                [cell.to_dict() for cell in row] for row in self.expenses
            ],
            "conversion_rates": dict(self.conversion_rates),
            "result_currency": self.result_currency,
        }

    @classmethod
    def from_dict(cls, data: dict) -> TripData:
        trip = cls()
        trip.people = data.get("people", [])
        trip.expenses = [
            [CellData.from_dict(c) for c in row]
            for row in data.get("expenses", [])
        ]
        trip.conversion_rates = data.get(
            "conversion_rates", dict(DEFAULT_CONVERSION_RATES)
        )
        trip.result_currency = data.get("result_currency", DEFAULT_CURRENCY)
        return trip

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    def _append_empty_row(self) -> None:
        row = [CellData(checked_people=list(self.people)) for _ in self.people]
        self.expenses.append(row)


VERSION_TAG = "0.0.1"
