"""Money Splitter — entry point."""

import sys

from PyQt5.QtCore import Qt
from PyQt5.QtGui import QColor, QPalette
from PyQt5.QtWidgets import QApplication

from logic.constants import (
    DRACULA_BG,
    DRACULA_COMMENT,
    DRACULA_CURRENT,
    DRACULA_CYAN,
    DRACULA_FG,
    DRACULA_PURPLE,
    DRACULA_RED,
)
from ui.main_window import MainWindow


def _apply_dracula_palette(app: QApplication) -> None:
    """Set a dark Dracula palette so QMessageBox / native dialogs also look dark."""
    palette = QPalette()
    palette.setColor(QPalette.Window, QColor(DRACULA_BG))
    palette.setColor(QPalette.WindowText, QColor(DRACULA_FG))
    palette.setColor(QPalette.Base, QColor(DRACULA_CURRENT))
    palette.setColor(QPalette.AlternateBase, QColor(DRACULA_BG))
    palette.setColor(QPalette.ToolTipBase, QColor(DRACULA_CURRENT))
    palette.setColor(QPalette.ToolTipText, QColor(DRACULA_FG))
    palette.setColor(QPalette.Text, QColor(DRACULA_FG))
    palette.setColor(QPalette.Button, QColor(DRACULA_CURRENT))
    palette.setColor(QPalette.ButtonText, QColor(DRACULA_FG))
    palette.setColor(QPalette.BrightText, QColor(DRACULA_RED))
    palette.setColor(QPalette.Link, QColor(DRACULA_CYAN))
    palette.setColor(QPalette.Highlight, QColor(DRACULA_PURPLE))
    palette.setColor(QPalette.HighlightedText, QColor(DRACULA_FG))
    palette.setColor(QPalette.Disabled, QPalette.Text, QColor(DRACULA_COMMENT))
    palette.setColor(QPalette.Disabled, QPalette.ButtonText, QColor(DRACULA_COMMENT))
    app.setPalette(palette)


def main() -> int:
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    _apply_dracula_palette(app)
    window = MainWindow()
    window.show()
    return app.exec_()


if __name__ == "__main__":
    sys.exit(main())
