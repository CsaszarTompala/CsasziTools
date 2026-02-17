# TerminalMenu Documentation

## Overview
The `TerminalMenu` class provides a flexible system for creating styled terminal menus with ASCII borders, horizontal separators, and vertical column divisions.

## Basic Usage

```python
from terminal_menu import TerminalMenu

menu = TerminalMenu(width=70)
```

## Core Method: `create_simple_menu(content_structure)`

Takes a single list as argument. The list can contain:
- **Strings**: Display as full-width lines
- **Lists**: Display as columns with vertical dividers (│)
- **Commas in strings**: Create multiple lines within the same column

**All top-level elements are separated by horizontal lines (═)**

### Important: Comma vs List Separators
- **Commas (`,`)** within a string: Multiple lines in the SAME column (vertical stacking)
- **List elements**: Multiple columns SIDE-BY-SIDE (horizontal division)

## String Formatting Prefixes

Strings can include formatting prefixes:

- `CENTER:text` - Center the text
- `LEFT:text` - Left-align with indent (default)
- `RIGHT:text` - Right-align the text
- `OPTION:[key]|description` - Format as menu option with arrow

## Examples

### 1. Simple Vertical Menu
```python
menu.create_simple_menu([
    "CENTER:Welcome",
    "LEFT:Option 1",
    "LEFT:Option 2",
    "LEFT:Option 3"
])
```

Output:
```
╔════════════════════════════════════════════════╗
║                    Welcome                     ║
╠════════════════════════════════════════════════╣
║  Option 1                                      ║
╠════════════════════════════════════════════════╣
║  Option 2                                      ║
╠════════════════════════════════════════════════╣
║  Option 3                                      ║
╚════════════════════════════════════════════════╝
```

### 2. Horizontal Columns (Vertical Dividers)
```python
menu.create_simple_menu([
    "CENTER:Title",
    ["LEFT:Column 1", "CENTER:Column 2", "RIGHT:Column 3"]
])
```

Output:
```
╔════════════════════════════════════════════════╗
║                     Title                      ║
╠════════════════════════════════════════════════╣
║  Column 1        │     Column 2     │ Column 3║
╚════════════════════════════════════════════════╝
```

### 3. Mixed Layout
```python
menu.create_simple_menu([
    "CENTER:HEADER",
    ["LEFT:Col 1", "RIGHT:Col 2"],
    "CENTER:FOOTER"
])
```

Output:
```
╔════════════════════════════════════════════════╗
║                    HEADER                      ║
╠════════════════════════════════════════════════╣
║  Col 1                    │            Col 2   ║
╠════════════════════════════════════════════════╣
║                    FOOTER                      ║
╚════════════════════════════════════════════════╝
```

### 4. Menu Options
```python
menu.create_simple_menu([
    "CENTER:MAIN MENU",
    "OPTION:[1]|Start Recording",
    "OPTION:[2]|Load Recording",
    "OPTION:[Q]|Quit"
])
```

Output:
```
╔════════════════════════════════════════════════╗
║                  MAIN MENU                     ║
╠════════════════════════════════════════════════╣
║  [1] → Start Recording                         ║
╠════════════════════════════════════════════════╣
║  [2] → Load Recording                          ║
╠════════════════════════════════════════════════╣
║  [Q] → Quit                                    ║
╚════════════════════════════════════════════════╝
```

### 5. Complex Table Layout
```python
menu.create_simple_menu([
    "CENTER:FEATURE COMPARISON",
    ["CENTER:Feature", "CENTER:Basic", "CENTER:Pro"],
    ["LEFT:Recording", "CENTER:✓", "CENTER:✓"],
    ["LEFT:Playback", "CENTER:✓", "CENTER:✓"],
    ["LEFT:Cloud Sync", "CENTER:✗", "CENTER:✓"]
])
```

Output:
```
╔══════════════════════════════════════════════════════╗
║              FEATURE COMPARISON                      ║
╠══════════════════════════════════════════════════════╣
║     Feature       │      Basic       │      Pro      ║
╠══════════════════════════════════════════════════════╣
║  Recording        │        ✓         │       ✓       ║
╠══════════════════════════════════════════════════════╣
║  Playback         │        ✓         │       ✓       ║
╠══════════════════════════════════════════════════════╣
║  Cloud Sync       │        ✗         │       ✓       ║
╚══════════════════════════════════════════════════════╝
```

## Helper Method: `create_menu_with_title()`

For menus that need a title and subtitle:

```python
menu.create_menu_with_title(
    "MY APPLICATION",
    [
        "OPTION:[1]|Option One",
        "OPTION:[2]|Option Two"
    ],
    "Optional subtitle text"
)
```

## Other Methods

### Information Windows
```python
menu.create_info_window("TITLE", "Message text", width=50)
```

### Error Windows
```python
menu.create_error_window("Error message here")
```

### Goodbye Windows
```python
menu.create_goodbye_window()
```

## Tips

1. **Column widths**: Automatically calculated equally
2. **Text truncation**: Long text is automatically truncated with "..."
3. **Minimum width**: Menu width minimum is 20 characters
4. **Separators**: Automatically added between all top-level elements
5. **Formatting**: Combine prefixes with columns for complex layouts

## Advanced Example - Dashboard

```python
menu = TerminalMenu(90)
menu.create_simple_menu([
    "CENTER:DASHBOARD",
    ["LEFT:System Status", "CENTER:Active Users", "RIGHT:Uptime"],
    ["CENTER:✓ Online", "CENTER:42", "RIGHT:99.9%"],
    "CENTER:Recent Activity",
    "LEFT:User logged in at 10:45",
    "LEFT:Recording started at 11:20",
    ["OPTION:[V]|View Details", "OPTION:[R]|Refresh", "OPTION:[X]|Exit"]
])
```

This creates a multi-section dashboard with status indicators, information rows, activity log, and action buttons.

## Multi-Line Columns Example

```python
menu = TerminalMenu(80)
menu.create_simple_menu([
    "CENTER:PRODUCT COMPARISON",
    [
        "LEFT:Basic Plan, LEFT:$9/month, LEFT:✓ Recording, LEFT:✗ Cloud", 
        "LEFT:Pro Plan, LEFT:$19/month, LEFT:✓ Recording, LEFT:✓ Cloud"
    ],
    "CENTER:Choose your plan"
])
```

Output:
```
╔════════════════════════════════════════════════════════════════════════════╗
║                         PRODUCT COMPARISON                                 ║
╠════════════════════════════════════════════════════════════════════════════╣
║  Basic Plan                   │  Pro Plan                                  ║
║  $9/month                     │  $19/month                                 ║
║  ✓ Recording                  │  ✓ Recording                               ║
║  ✗ Cloud                      │  ✓ Cloud                                   ║
╠════════════════════════════════════════════════════════════════════════════╣
║                           Choose your plan                                 ║
╚════════════════════════════════════════════════════════════════════════════╝
```

Key: The commas create multiple lines within each column, while the list structure creates side-by-side columns.
