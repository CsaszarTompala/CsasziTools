#!/usr/bin/env python3
"""CsasziGit — Git GUI with Dracula theme and AI-assisted commands."""

import sys
import os

# Ensure the package is importable when running from the script directory
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PyQt6.QtWidgets import QApplication
from PyQt6.QtGui import QIcon
from csaszigit.main_window import MainWindow
from csaszigit.settings import SettingsManager
from csaszigit.themes import apply_theme


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("CsasziGit")
    app.setOrganizationName("Csaszi")
    app.setStyle("Fusion")  # Fusion base for consistent cross-platform look

    settings = SettingsManager()
    apply_theme(app, settings.theme)

    window = MainWindow(settings)
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
