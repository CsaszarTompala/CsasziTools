"""Constants and configuration for the Money Splitter application."""

from __future__ import annotations

VERSION = "0.0.4"
APP_NAME = "Money Splitter"
BRAND = "CsasziTools"

# Default currencies shipped out-of-the-box.
# Users can add / remove currencies at runtime; these are the initial set.
DEFAULT_CURRENCIES = ["HUF", "EUR", "USD"]
DEFAULT_BASE_CURRENCY = "HUF"

# Default conversion rates (how many units of the base currency per 1 unit
# of the foreign currency).  Base currency itself is always 1.0 implicitly.
DEFAULT_CONVERSION_RATES = {
    "USD": 380.0,
    "EUR": 410.0,
}

# Table defaults
DEFAULT_ROW_COUNT = 6

# =====================================================================
# Theme-aware colour helpers
# =====================================================================
# Import *after* the basic constants above to avoid circular deps.
from logic.themes import get_active_theme, get_currency_palette  # noqa: E402


# ---- Currency colour cache (rebuilt on theme change) -----------------
CURRENCY_COLORS: dict[str, str] = {}


def _seed_currency_colors() -> None:
    """(Re)populate the currency colour cache from the active theme."""
    palette = get_currency_palette()
    CURRENCY_COLORS.clear()
    CURRENCY_COLORS["HUF"] = palette[0]
    CURRENCY_COLORS["EUR"] = palette[1]
    CURRENCY_COLORS["USD"] = palette[2]


# Seed once at import time
_seed_currency_colors()


def get_currency_color(currency: str) -> str:
    """Return a deterministic text colour for *currency*.

    Known currencies come from CURRENCY_COLORS; unknown ones are assigned
    from the palette and cached.
    """
    if currency in CURRENCY_COLORS:
        return CURRENCY_COLORS[currency]
    palette = get_currency_palette()
    idx = len(CURRENCY_COLORS) % len(palette)
    colour = palette[idx]
    CURRENCY_COLORS[currency] = colour
    return colour


def refresh_theme_colors() -> None:
    """Call after switching themes to re-seed all colour caches."""
    _seed_currency_colors()


# ---- Convenience accessors that read from the active theme -----------

def partial_split_bg() -> str:
    return get_active_theme()["partial_split_bg"]


def default_bg() -> str:
    return get_active_theme()["bg"]


def balance_positive() -> str:
    return get_active_theme()["green"]


def balance_negative() -> str:
    return get_active_theme()["red"]


# Legacy aliases (kept for any remaining imports; point to theme values)
PARTIAL_SPLIT_BG = property(lambda _: partial_split_bg())  # type: ignore
DEFAULT_BG = property(lambda _: default_bg())              # type: ignore
