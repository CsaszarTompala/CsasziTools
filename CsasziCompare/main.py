"""
CsasziCompare — entry point.

Usage
-----
  # Compare two files
  python main.py --left file_a.py --right file_b.py

  # Compare two directories
  python main.py --left dir_a/ --right dir_b/

  # Three-way merge
  python main.py --base ancestor.py --left ours.py --right theirs.py --mode merge

  # Three-way rebase
  python main.py --base ancestor.py --left ours.py --right theirs.py --mode rebase

  # Git commit comparison (launched by CsasziGit)
  python main.py --repo /path/to/repo --commit1 abc123 --commit2 def456

  # Choose theme
  python main.py --theme dark
"""

import sys
import argparse

from PyQt6.QtWidgets import QApplication

from csaszicompare.themes import apply_theme
from csaszicompare.main_window import MainWindow


def main():
    parser = argparse.ArgumentParser(
        description="CsasziCompare — file / directory / Git comparison tool",
    )
    parser.add_argument("--left", default="", help="Left file or directory")
    parser.add_argument("--right", default="", help="Right file or directory")
    parser.add_argument("--base", default="", help="Base/ancestor file (for 3-way merge)")
    parser.add_argument(
        "--mode", default="compare",
        choices=["compare", "merge", "rebase"],
        help="Comparison mode",
    )
    parser.add_argument("--repo", default="", help="Git repository path")
    parser.add_argument("--commit1", default="", help="First commit hash")
    parser.add_argument("--commit2", default="", help="Second commit hash")
    parser.add_argument(
        "--theme", default="dracula",
        choices=["dracula", "dark", "bright"],
        help="Colour theme (default: dracula)",
    )

    args = parser.parse_args()

    app = QApplication(sys.argv)
    app.setApplicationName("CsasziCompare")
    apply_theme(app, args.theme)

    win = MainWindow(
        left=args.left,
        right=args.right,
        base=args.base,
        mode=args.mode,
        repo=args.repo,
        commit1=args.commit1,
        commit2=args.commit2,
        theme=args.theme,
    )
    win.mark_theme(args.theme)
    win.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
