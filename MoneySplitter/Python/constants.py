"""Constants and configuration for the Money Splitter application."""

VERSION = "0.0.1"
APP_NAME = "Money Splitter"
BRAND = "CsasziTools"

# Currencies
CURRENCIES = ["HUF", "EUR", "USD"]
DEFAULT_CURRENCY = "HUF"

# Default conversion rates (how many HUF per 1 unit of foreign currency)
DEFAULT_CONVERSION_RATES = {
    "USD": 380.0,
    "EUR": 410.0,
}

# Table defaults
DEFAULT_ROW_COUNT = 6

# Cell text colours keyed by currency
CURRENCY_COLORS = {
    "HUF": "#000000",   # Black
    "EUR": "#0000CC",   # Blue
    "USD": "#008800",   # Green
}

# Background colour when an expense is NOT split among everyone
PARTIAL_SPLIT_BG = "#E8D0FF"   # Pale purple
DEFAULT_BG = "#FFFFFF"          # White
