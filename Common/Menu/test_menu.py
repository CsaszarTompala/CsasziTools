"""
Test script to demonstrate the Common Menu capabilities.
"""

import sys
import os

# Add parent directory to path to import Common modules
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from Common.Menu import TerminalMenu


def test_simple_vertical_menu():
    """Test a simple vertical menu with horizontal separators."""
    menu = TerminalMenu(70)
    menu.create_simple_menu([
        "CENTER:Welcome to the Menu System",
        "LEFT:Option 1: Start Recording",
        "LEFT:Option 2: Load Recording",
        "LEFT:Option 3: Exit"
    ])
    input("\nPress ENTER to continue to next test...")


def test_horizontal_columns():
    """Test horizontal columns with vertical dividers."""
    menu = TerminalMenu(70)
    menu.create_simple_menu([
        "CENTER:Two Column Layout",
        ["LEFT:Left Column", "RIGHT:Right Column"],
        "CENTER:Three Column Layout",
        ["CENTER:Column 1", "CENTER:Column 2", "CENTER:Column 3"]
    ])
    input("\nPress ENTER to continue to next test...")


def test_mixed_layout():
    """Test mixed layout with full-width and columns."""
    menu = TerminalMenu(80)
    menu.create_simple_menu([
        "CENTER:MOUSE AND KEYBOARD REPLAYER",
        "CENTER:Advanced Recording System",
        ["LEFT:Recording", "LEFT:Playback", "LEFT:Settings"],
        ["CENTER:✓ Mouse Events", "CENTER:✓ Loop Mode", "CENTER:⚙ Configure"],
        ["CENTER:✓ Keyboard Events", "CENTER:✓ Speed Control", "CENTER:⚙ Preferences"],
        "CENTER:Press any key to start"
    ])
    input("\nPress ENTER to continue to next test...")


def test_menu_with_options():
    """Test menu with formatted options."""
    menu = TerminalMenu(70)
    menu.create_simple_menu([
        "CENTER:MAIN MENU",
        "OPTION:[1]|Start New Recording",
        "OPTION:[2]|Load Existing Recording",
        "OPTION:[3]|Settings and Configuration",
        "OPTION:[Q]|Quit Application"
    ])
    input("\nPress ENTER to continue to next test...")


def test_complex_columns():
    """Test complex column layout."""
    menu = TerminalMenu(80)
    menu.create_simple_menu([
        "CENTER:FEATURE COMPARISON",
        ["CENTER:Feature", "CENTER:Basic", "CENTER:Pro", "CENTER:Enterprise"],
        ["LEFT:Mouse Recording", "CENTER:✓", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Keyboard Recording", "CENTER:✓", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Loop Playback", "CENTER:✗", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Speed Control", "CENTER:✗", "CENTER:✗", "CENTER:✓"],
        ["LEFT:Cloud Sync", "CENTER:✗", "CENTER:✗", "CENTER:✓"]
    ])
    input("\nPress ENTER to continue to next test...")


def test_multiline_columns():
    """Test multi-line content within columns using commas."""
    menu = TerminalMenu(80)
    menu.create_simple_menu([
        "CENTER:MULTI-LINE COLUMN DEMO",
        ["LEFT:Column 1, LEFT:Line 2, LEFT:Line 3", "CENTER:Middle, CENTER:Line 2, CENTER:Line 3", "RIGHT:Right, RIGHT:Line 2, RIGHT:Line 3"],
        "CENTER:Mixed heights example",
        ["LEFT:Short column", "LEFT:Tall column, LEFT:Line 2, LEFT:Line 3, LEFT:Line 4, LEFT:Line 5"]
    ])
    input("\nPress ENTER to continue to next test...")


def test_menu_with_multiline_options():
    """Test menu options with multi-line descriptions."""
    menu = TerminalMenu(80)
    menu.create_simple_menu([
        "CENTER:RECORDING OPTIONS",
        ["OPTION:[1]|Start Recording, LEFT:Captures mouse and keyboard, LEFT:Press End to stop", "OPTION:[2]|Load Recording, LEFT:Browse saved recordings, LEFT:Select file to replay"],
        "CENTER:SYSTEM",
        ["OPTION:[Q]|Quit Application, LEFT:Exit the program"]
    ])
    input("\nPress ENTER to continue to next test...")


def test_with_title_helper():
    """Test using the helper method with title."""
    menu = TerminalMenu(70)
    menu.create_menu_with_title(
        "APPLICATION MENU",
        [
            "LEFT:Available Actions:",
            "OPTION:[R]|Record new session",
            "OPTION:[P]|Playback recording",
            "OPTION:[S]|Settings",
            ["CENTER:Status: Ready", "RIGHT:Version: 2.0"]
        ],
        "Choose an option below"
    )
    input("\nPress ENTER to exit...")


def main():
    """Run all menu tests."""
    print("Testing new TerminalMenu system...\n")
    
    test_simple_vertical_menu()
    test_horizontal_columns()
    test_mixed_layout()
    test_menu_with_options()
    test_complex_columns()
    test_multiline_columns()
    test_menu_with_multiline_options()
    test_with_title_helper()
    
    print("\n✓ All tests completed!")


if __name__ == "__main__":
    main()
