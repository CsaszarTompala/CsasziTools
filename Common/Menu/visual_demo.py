"""
Visual demonstration of the menu system's capabilities.
"""

import sys
import os

# Add parent directory to path to import Common modules
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from Common.Menu import TerminalMenu


def demo_comparison_table():
    """Demo: Feature comparison table."""
    print("=" * 80)
    print("DEMO 1: Feature Comparison Table")
    print("=" * 80 + "\n")
    
    menu = TerminalMenu(85)
    menu.create_simple_menu([
        "CENTER:SUBSCRIPTION PLANS",
        ["CENTER:Feature", "CENTER:Starter, CENTER:$0/mo", "CENTER:Professional, CENTER:$29/mo", "CENTER:Enterprise, CENTER:Custom"],
        "LEFT:Core Features",
        ["LEFT:Mouse Recording", "CENTER:✓", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Keyboard Recording", "CENTER:✓", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Basic Playback", "CENTER:✓", "CENTER:✓", "CENTER:✓"],
        "LEFT:Advanced Features",
        ["LEFT:Loop Mode", "CENTER:✗", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Speed Control", "CENTER:✗", "CENTER:✓", "CENTER:✓"],
        ["LEFT:Cloud Storage", "CENTER:✗", "CENTER:10 GB", "CENTER:Unlimited"],
        ["LEFT:Priority Support", "CENTER:✗", "CENTER:✗", "CENTER:✓"],
        ["LEFT:API Access", "CENTER:✗", "CENTER:✗", "CENTER:✓"],
        ["OPTION:[1]|Select", "OPTION:[2]|Select", "OPTION:[3]|Contact Us"]
    ])
    
    input("\nPress ENTER to continue...\n\n")


def demo_dashboard():
    """Demo: System dashboard."""
    print("=" * 80)
    print("DEMO 2: System Dashboard")
    print("=" * 80 + "\n")
    
    menu = TerminalMenu(90)
    menu.create_simple_menu([
        "CENTER:RECORDING SYSTEM DASHBOARD",
        ["LEFT:System Health, CENTER:Performance, RIGHT:Activity"],
        ["CENTER:✓ All Systems OK, CENTER:CPU: 15%, RIGHT:Last 24h: 142 recordings"],
        "LEFT:Quick Stats",
        [
            "LEFT:Total Recordings, LEFT:1,247 files, LEFT:Storage: 2.3 GB",
            "LEFT:Most Used, LEFT:Daily automation, LEFT:Run 342 times",
            "LEFT:Latest Activity, LEFT:Recording completed, LEFT:2 minutes ago"
        ],
        "LEFT:Available Actions",
        ["OPTION:[R]|Record New", "OPTION:[P]|Play Last", "OPTION:[S]|Settings", "OPTION:[V]|View All"]
    ])
    
    input("\nPress ENTER to continue...\n\n")


def demo_detailed_menu():
    """Demo: Menu with detailed descriptions."""
    print("=" * 80)
    print("DEMO 3: Menu with Detailed Descriptions")
    print("=" * 80 + "\n")
    
    menu = TerminalMenu(95)
    menu.create_simple_menu([
        "CENTER:MOUSE AND KEYBOARD REPLAYER",
        "CENTER:Professional Recording and Playback Tool",
        "LEFT:Recording Options",
        [
            "OPTION:[1]|Start New Recording, LEFT:• Capture all mouse clicks and movements, LEFT:• Record keyboard keystrokes, LEFT:• Press END key to finish, LEFT:• Automatically save to JSON format",
            "OPTION:[2]|Resume Previous Session, LEFT:• Continue from last recording, LEFT:• Append new events to existing file, LEFT:• Useful for building complex workflows"
        ],
        "LEFT:Playback Options",
        [
            "OPTION:[3]|Play Recording Once, LEFT:• Execute recording from start to end, LEFT:• Stop automatically when complete",
            "OPTION:[4]|Loop Recording, LEFT:• Repeat recording continuously, LEFT:• Press END key to stop, LEFT:• Perfect for automation tasks"
        ],
        "LEFT:System",
        ["OPTION:[S]|Settings & Configuration", "OPTION:[H]|Help & Documentation", "OPTION:[Q]|Quit Application"]
    ])
    
    input("\nPress ENTER to exit...\n\n")


def main():
    """Run all visual demos."""
    print("\n")
    print("╔════════════════════════════════════════════════════════════════════════════════╗")
    print("║                                                                                ║")
    print("║                   TERMINAL MENU SYSTEM - VISUAL DEMOS                          ║")
    print("║                                                                                ║")
    print("║              Demonstrating Comma-Separated Multi-Line Columns                 ║")
    print("║                                                                                ║")
    print("╚════════════════════════════════════════════════════════════════════════════════╝")
    print("\n")
    input("Press ENTER to start demos...\n\n")
    
    demo_comparison_table()
    demo_dashboard()
    demo_detailed_menu()
    
    print("=" * 80)
    print("✓ All visual demos completed!")
    print("=" * 80)
    print("\nKey Takeaways:")
    print("  • Use commas (,) for multi-line content within a column")
    print("  • Use lists [...] for side-by-side columns with vertical dividers")
    print("  • Top-level elements are separated by horizontal lines")
    print("  • Combine both for unlimited layout flexibility")
    print()


if __name__ == "__main__":
    main()
