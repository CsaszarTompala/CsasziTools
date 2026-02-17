"""
Quick demo of the comma-separated multi-line column feature.
"""

import sys
import os

# Add parent directory to path to import Common modules
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from Common.Menu import TerminalMenu


def main():
    menu = TerminalMenu(90)
    
    # Example 1: Simple multi-line in single column
    print("Example 1: Multi-line content in columns\n")
    menu.create_simple_menu([
        "CENTER:RECORDING FEATURES",
        [
            "LEFT:Mouse Events, LEFT:• Click tracking, LEFT:• Position recording, LEFT:• Button detection",
            "LEFT:Keyboard Events, LEFT:• Key press capture, LEFT:• Key release tracking, LEFT:• Special keys"
        ]
    ])
    input("Press ENTER to continue...\n")
    
    # Example 2: Product comparison
    print("Example 2: Product comparison with multi-line\n")
    menu.create_simple_menu([
        "CENTER:CHOOSE YOUR PLAN",
        [
            "CENTER:FREE, CENTER:$0/month, CENTER:✓ Basic Recording, CENTER:✗ Cloud Save, CENTER:✗ Advanced Features",
            "CENTER:PRO, CENTER:$9.99/month, CENTER:✓ Basic Recording, CENTER:✓ Cloud Save, CENTER:✓ Advanced Features",
            "CENTER:ENTERPRISE, CENTER:Custom, CENTER:✓ Basic Recording, CENTER:✓ Cloud Save, CENTER:✓ Everything"
        ],
        ["OPTION:[1]|Select Free", "OPTION:[2]|Select Pro", "OPTION:[3]|Contact Sales"]
    ])
    input("Press ENTER to continue...\n")
    
    # Example 3: Menu with descriptions
    print("Example 3: Menu with detailed descriptions\n")
    menu.create_simple_menu([
        "CENTER:MAIN MENU",
        [
            "OPTION:[R]|Start Recording, LEFT:Begin capturing mouse and keyboard events, LEFT:Press END key to stop recording",
            "OPTION:[P]|Playback, LEFT:Replay a saved recording file, LEFT:Loops continuously until stopped"
        ],
        [
            "OPTION:[S]|Settings, LEFT:Configure application preferences",
            "OPTION:[Q]|Quit, LEFT:Exit the application"
        ]
    ])
    input("Press ENTER to exit...\n")
    
    print("✓ Demo completed!")


if __name__ == "__main__":
    main()
