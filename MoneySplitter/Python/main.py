"""Money Splitter — entry point."""

import sys

from PyQt5.QtWidgets import QApplication

from data.settings import load_theme_name
from logic.constants import refresh_theme_colors
from logic.themes import build_palette, set_active_theme, DEFAULT_THEME_NAME
from ui.main_window import MainWindow


def main() -> int:
    app = QApplication(sys.argv)
    app.setStyle("Fusion")

    # Restore the user's preferred theme (or fall back to default)
    saved = load_theme_name() or DEFAULT_THEME_NAME
    theme = set_active_theme(saved)
    refresh_theme_colors()
    app.setPalette(build_palette(theme))

    window = MainWindow()
    window.show()
    return app.exec_()


if __name__ == "__main__":
    sys.exit(main())
