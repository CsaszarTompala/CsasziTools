# Common Menu Module

A reusable terminal menu system for creating styled console menus with ASCII borders.

## Features

- ✨ Flexible layout system with horizontal and vertical divisions
- 📐 Automatic column width calculation
- 🎨 Multiple text formatting options (CENTER, LEFT, RIGHT, OPTION)
- 📊 Multi-line content support using comma separators
- 🔄 Reusable across multiple projects

## Installation

Simply import the module from any project in the CsasziTools workspace:

```python
import sys
import os

# Add Common folder to Python path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from Common.Menu import TerminalMenu
```

## Basic Usage

### Simple Menu

```python
from Common.Menu import TerminalMenu

menu = TerminalMenu(width=70)
menu.create_simple_menu([
    "CENTER:Welcome",
    "LEFT:Option 1",
    "LEFT:Option 2",
    "LEFT:Option 3"
])
```

### Menu with Columns

```python
menu.create_simple_menu([
    "CENTER:FEATURE COMPARISON",
    ["CENTER:Basic", "CENTER:Pro", "CENTER:Enterprise"],
    ["CENTER:$9", "CENTER:$29", "CENTER:$99"]
])
```

### Multi-line Content in Columns

Use commas to create multiple lines within a single column:

```python
menu.create_simple_menu([
    "CENTER:PLANS",
    [
        "CENTER:Basic,CENTER:$9/mo,CENTER:10 users",
        "CENTER:Pro,CENTER:$29/mo,CENTER:50 users"
    ]
])
```

### Menu with Title

```python
menu.create_menu_with_title(
    "MY APPLICATION",
    [
        "OPTION:[1]|Start",
        "OPTION:[2]|Settings",
        "OPTION:[3]|Exit"
    ],
    "Choose an option"  # Optional subtitle
)
```

## Helper Methods

### Information Window
```python
menu.create_info_window("INFO", "Operation completed successfully!", width=50)
```

### Error Window
```python
menu.create_error_window("An error occurred during processing")
```

### Goodbye Window
```python
menu.create_goodbye_window()
```

## Formatting Options

- `CENTER:text` - Center-aligned text
- `LEFT:text` - Left-aligned text with indentation
- `RIGHT:text` - Right-aligned text
- `OPTION:[key]|description` - Formatted menu option with arrow

## Structure Rules

| Element | Separator | Result |
|---------|-----------|--------|
| Top-level strings | Horizontal line (═) | Full-width lines separated by dividers |
| Lists `[...]` | Vertical line (│) | Side-by-side columns |
| Commas `,` | New line in same column | Multiple lines vertically stacked |

## Examples

See the following files for complete examples:
- `Mouse_and_keyboard_replayer/demo_multiline.py` - Multi-line demos
- `Mouse_and_keyboard_replayer/test_menu.py` - Various menu styles
- `Mouse_and_keyboard_replayer/visual_demo.py` - Complex layouts

## Version

Current version: 1.0.0
