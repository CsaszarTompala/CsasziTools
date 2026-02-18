"""Undo / Redo manager using TripData state snapshots."""

from __future__ import annotations

from data.models import TripData


class UndoRedoManager:
    """Stack-based undo/redo using serialised TripData snapshots.

    Call :meth:`snapshot` **before** every mutating operation so the
    previous state can be restored with :meth:`undo`.
    """

    def __init__(self, max_history: int = 50) -> None:
        self._history: list[dict] = []
        self._index: int = -1
        self._max = max_history

    # -----------------------------------------------------------------
    def snapshot(self, trip: TripData) -> None:
        """Record the current TripData state (call *before* mutating)."""
        # Discard any future redo states
        self._history = self._history[: self._index + 1]
        self._history.append(trip.to_dict())
        # Trim oldest entries if the stack grows too large
        if len(self._history) > self._max:
            self._history = self._history[-self._max :]
        self._index = len(self._history) - 1

    # -----------------------------------------------------------------
    def undo(self) -> TripData | None:
        """Return the previous state, or *None* if nothing to undo."""
        if self._index <= 0:
            return None
        self._index -= 1
        return TripData.from_dict(self._history[self._index])

    def redo(self) -> TripData | None:
        """Return the next state, or *None* if nothing to redo."""
        if self._index >= len(self._history) - 1:
            return None
        self._index += 1
        return TripData.from_dict(self._history[self._index])

    # -----------------------------------------------------------------
    def can_undo(self) -> bool:
        return self._index > 0

    def can_redo(self) -> bool:
        return self._index < len(self._history) - 1

    def clear(self, trip: TripData | None = None) -> None:
        """Reset the stack.  Optionally seed with an initial snapshot."""
        self._history.clear()
        self._index = -1
        if trip is not None:
            self.snapshot(trip)
