"""Save / load trip data to JSON files."""

from __future__ import annotations

import json
from typing import Optional

from data.models import TripData


def save_trip(trip: TripData, filepath: str) -> bool:
    """Persist *trip* to *filepath* as pretty-printed JSON."""
    try:
        with open(filepath, "w", encoding="utf-8") as fh:
            json.dump(trip.to_dict(), fh, indent=2, ensure_ascii=False)
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"Error saving trip: {exc}")
        return False


def load_trip(filepath: str) -> Optional[TripData]:
    """Load a TripData from a JSON file, or *None* on error."""
    try:
        with open(filepath, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        return TripData.from_dict(data)
    except Exception as exc:  # noqa: BLE001
        print(f"Error loading trip: {exc}")
        return None
