"""Remote Desktop Connector — GUI entry point."""

import sys

from ui.main_window import RemoteDesktopApp


def main() -> int:
    app = RemoteDesktopApp()
    app.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
