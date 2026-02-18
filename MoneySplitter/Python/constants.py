"""Constants and configuration for the Money Splitter application."""

VERSION = "0.0.2"
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

# ---- Colour palette for currencies ----------------------------------
# The first three entries correspond to the default currencies above.
# Any extra currencies cycle through the palette from index 3 onwards.
CURRENCY_COLOR_PALETTE = [
    "#000000",  # 0  Black    (HUF default)
    "#0000CC",  # 1  Blue     (EUR default)
    "#008800",  # 2  Green    (USD default)
    "#CC6600",  # 3  Orange
    "#990099",  # 4  Purple
    "#CC0066",  # 5  Magenta
    "#006699",  # 6  Teal
    "#996600",  # 7  Brown
]

# Mapping seeded for the built-in currencies; others are assigned at runtime
# by get_currency_color().
CURRENCY_COLORS = {
    "HUF": CURRENCY_COLOR_PALETTE[0],
    "EUR": CURRENCY_COLOR_PALETTE[1],
    "USD": CURRENCY_COLOR_PALETTE[2],
}


def get_currency_color(currency: str) -> str:
    """Return a deterministic text colour for *currency*.

    Known currencies come from CURRENCY_COLORS; unknown ones are assigned
    from the palette and cached.
    """
    if currency in CURRENCY_COLORS:
        return CURRENCY_COLORS[currency]
    idx = len(CURRENCY_COLORS) % len(CURRENCY_COLOR_PALETTE)
    colour = CURRENCY_COLOR_PALETTE[idx]
    CURRENCY_COLORS[currency] = colour
    return colour


# Background colour when an expense is NOT split among everyone
PARTIAL_SPLIT_BG = "#E8D0FF"   # Pale purple
DEFAULT_BG = "#FFFFFF"          # White
