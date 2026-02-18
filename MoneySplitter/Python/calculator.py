"""Balance-calculation logic for the Money Splitter application."""

from typing import Dict, List
from models import TripData


def convert_amount(
    amount: float,
    from_currency: str,
    to_currency: str,
    conversion_rates: Dict[str, float],
) -> float:
    """Convert *amount* between currencies via HUF as the pivot.

    *conversion_rates* maps a foreign currency code to its HUF equivalent
    (e.g. ``{"USD": 380, "EUR": 410}``).
    """
    if from_currency == to_currency:
        return amount

    # Step 1 – to HUF
    if from_currency == "HUF":
        amount_huf = amount
    else:
        rate = conversion_rates.get(from_currency, 1.0)
        amount_huf = amount * rate

    # Step 2 – from HUF to target
    if to_currency == "HUF":
        return amount_huf

    rate = conversion_rates.get(to_currency, 1.0)
    return amount_huf / rate if rate != 0 else 0.0


def calculate_balances(trip: TripData) -> Dict[str, float]:
    """Return the balance of every person in *trip.result_currency*.

    Positive  →  person is owed money (overpaid).
    Negative  →  person owes money (underpaid).
    """
    balances: Dict[str, float] = {name: 0.0 for name in trip.people}

    for row in trip.expenses:
        for col_idx, cell in enumerate(row):
            if cell.amount is None or cell.amount <= 0:
                continue
            if col_idx >= len(trip.people):
                continue

            payer = trip.people[col_idx]

            converted = convert_amount(
                cell.amount,
                cell.currency,
                trip.result_currency,
                trip.conversion_rates,
            )

            # Determine beneficiaries ----------------------------------
            beneficiaries: List[str] = [
                p for p in cell.checked_people if p in trip.people
            ]
            if not beneficiaries or set(beneficiaries) == set(trip.people):
                beneficiaries = list(trip.people)

            share = converted / len(beneficiaries)

            # Payer gets full credit; each beneficiary is debited.
            balances[payer] += converted
            for person in beneficiaries:
                balances[person] -= share

    return balances
