"""Balance-calculation logic for the Money Splitter application."""

from typing import Dict, List
from data.models import TripData


def convert_amount(
    amount: float,
    from_currency: str,
    to_currency: str,
    base_currency: str,
    conversion_rates: Dict[str, float],
) -> float:
    """Convert *amount* between currencies via the trip's base currency as pivot.

    *conversion_rates* maps a non-base currency code to its base-currency
    equivalent (e.g. ``{"USD": 380, "EUR": 410}`` when base is HUF).
    The base currency itself is always implicitly 1.0.
    """
    if from_currency == to_currency:
        return amount

    # Step 1 – to base currency
    if from_currency == base_currency:
        amount_base = amount
    else:
        rate = conversion_rates.get(from_currency, 1.0)
        amount_base = amount * rate

    # Step 2 – from base currency to target
    if to_currency == base_currency:
        return amount_base

    rate = conversion_rates.get(to_currency, 1.0)
    return amount_base / rate if rate != 0 else 0.0


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
                trip.base_currency,
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
