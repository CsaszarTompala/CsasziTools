"""Connection data persistence for the Remote Desktop Connector."""

from __future__ import annotations

import json
import os


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_FILE = os.path.join(_PROJECT_ROOT, "connections.json")

# ---------------------------------------------------------------------------
# Default connections shipped out-of-the-box
# ---------------------------------------------------------------------------
DEFAULT_CONNECTIONS: list[dict[str, str]] = [
    {
        "name": "uuddcrzw",
        "computer": "uuddcrzw",
        "username": "AUTOMOTIVE-WAN\\uidr1926",
        "password": "Conti00019",
    },
    {
        "name": "aqlde70w",
        "computer": "aqlde70w",
        "username": "AUTOMOTIVE-WAN\\uig40210",
        "password": "DamonHIL24!",
    },
    {
        "name": "Chewie",
        "computer": "LUDC0PVN",
        "username": "AUTOMOTIVE-WAN\\uie20103",
        "password": "?Jenk#Test1313?",
    },
    {
        "name": "Luke",
        "computer": "ludfl3dn",
        "username": "AUTOMOTIVE-WAN\\uie20103",
        "password": "?Jenk#Test1313?",
    },
]


# ---------------------------------------------------------------------------
# Load / Save
# ---------------------------------------------------------------------------
def load_connections() -> list[dict[str, str]]:
    """Load connections from the JSON configuration file.

    Returns:
        List of connection dictionaries.  Falls back to defaults on error.
    """
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return DEFAULT_CONNECTIONS.copy()
    return DEFAULT_CONNECTIONS.copy()


def save_connections(connections: list[dict[str, str]]) -> bool:
    """Save connections to the JSON configuration file.

    Returns:
        True if saved successfully, False otherwise.
    """
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(connections, f, indent=4)
        return True
    except IOError:
        return False
