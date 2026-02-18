"""Constants and configuration for the Money Splitter application."""

VERSION = "0.0.3"
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
# Dracula colour scheme
# =====================================================================
DRACULA_BG = "#282a36"
DRACULA_CURRENT = "#44475a"
DRACULA_FG = "#f8f8f2"
DRACULA_COMMENT = "#6272a4"
DRACULA_CYAN = "#8be9fd"
DRACULA_GREEN = "#50fa7b"
DRACULA_ORANGE = "#ffb86c"
DRACULA_PINK = "#ff79c6"
DRACULA_PURPLE = "#bd93f9"
DRACULA_RED = "#ff5555"
DRACULA_YELLOW = "#f1fa8c"

# ---- Colour palette for currencies (Dracula-friendly) ----------------
# Bright, legible colours that contrast well against #282a36 / #44475a.
CURRENCY_COLOR_PALETTE = [
    DRACULA_FG,       # 0  White/foreground (HUF default — base currency)
    DRACULA_CYAN,     # 1  Cyan             (EUR default)
    DRACULA_GREEN,    # 2  Green            (USD default)
    DRACULA_ORANGE,   # 3  Orange
    DRACULA_PINK,     # 4  Pink
    DRACULA_YELLOW,   # 5  Yellow
    DRACULA_PURPLE,   # 6  Purple
    DRACULA_RED,      # 7  Red
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
PARTIAL_SPLIT_BG = "#3d2f58"    # Muted purple (Dracula accent)
DEFAULT_BG = DRACULA_BG         # Main background

# Balance positive / negative colours
BALANCE_POSITIVE = DRACULA_GREEN
BALANCE_NEGATIVE = DRACULA_RED
