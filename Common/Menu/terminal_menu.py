"""
Terminal Menu Module

This module provides a TerminalMenu class for creating styled terminal menus
with ASCII borders and formatted text display.

Features:
- Create bordered menus with custom titles
- Add text lines with automatic centering and padding
- Support for separators and sections
- Clear terminal functionality
- Flexible menu width configuration
"""

import os
from typing import List, Optional


class TerminalMenu:
    """
    A class for creating styled terminal menus with ASCII borders.
    """
    
    def __init__(self, width: int = 60) -> None:
        """
        Initialize the TerminalMenu with specified width.
        
        Arguments:
            width: Width of the menu including borders (minimum 20)
        """
        self.width = max(width, 20)  # Ensure minimum width
        self.content_width = self.width - 2  # Account for left and right borders
        self.lines: List[str] = []
    
    def clear_terminal(self) -> None:
        """Clear the terminal screen."""
        os.system('cls' if os.name == 'nt' else 'clear')
    
    def clear_menu(self) -> None:
        """Clear all lines from the current menu."""
        self.lines.clear()
    
    def add_title(self, title: str) -> None:
        """
        Add a centered title to the menu.
        
        Arguments:
            title: The title text to add
        """
        self.add_empty_line()
        self.add_centered_line(title)
        self.add_empty_line()
    
    def add_centered_line(self, text: str) -> None:
        """
        Add a centered line of text to the menu.
        
        Arguments:
            text: The text to center and add
        """
        if len(text) > self.content_width:
            # If text is too long, truncate with ellipsis
            text = text[:self.content_width - 3] + "..."
        
        centered_text = text.center(self.content_width)
        self.lines.append(f"║{centered_text}║")
    
    def add_left_aligned_line(self, text: str, indent: int = 2) -> None:
        """
        Add a left-aligned line of text to the menu.
        
        Arguments:
            text: The text to add
            indent: Number of spaces to indent from the left
        """
        available_width = self.content_width - indent
        if len(text) > available_width:
            # If text is too long, truncate with ellipsis
            text = text[:available_width - 3] + "..."
        
        padded_text = (" " * indent + text).ljust(self.content_width)
        self.lines.append(f"║{padded_text}║")
    
    def add_empty_line(self) -> None:
        """Add an empty line to the menu."""
        empty_line = " " * self.content_width
        self.lines.append(f"║{empty_line}║")
    
    def add_separator(self) -> None:
        """Add a horizontal separator line to the menu."""
        separator = "═" * self.content_width
        self.lines.append(f"╠{separator}╣")
    
    def add_vertical_divider_line(self, text_left: str = "", text_right: str = "", split_ratio: float = 0.5) -> None:
        """
        Add a line with vertical divider splitting content into left and right sections.
        
        Arguments:
            text_left: Text for the left section
            text_right: Text for the right section
            split_ratio: Ratio of width for left section (0.0 to 1.0)
        """
        split_ratio = max(0.1, min(0.9, split_ratio))  # Clamp between 0.1 and 0.9
        left_width = int(self.content_width * split_ratio) - 1  # -1 for divider
        right_width = self.content_width - left_width - 1  # -1 for divider
        
        # Truncate text if too long
        if len(text_left) > left_width:
            text_left = text_left[:left_width - 3] + "..."
        if len(text_right) > right_width:
            text_right = text_right[:right_width - 3] + "..."
        
        # Pad text to fill the width
        padded_left = text_left.ljust(left_width)
        padded_right = text_right.ljust(right_width)
        
        self.lines.append(f"║{padded_left}│{padded_right}║")
    
    def add_option(self, key: str, description: str, indent: int = 4) -> None:
        """
        Add a menu option with key and description.
        
        Arguments:
            key: The key/number for the option (e.g., "[0]")
            description: Description of the option
            indent: Number of spaces to indent from the left
        """
        option_text = f"{key} → {description}"
        self.add_left_aligned_line(option_text, indent)
    
    def create_window(self, title: Optional[str] = None, content_lines: Optional[List[str]] = None) -> str:
        """
        Create a complete window with borders, title, and content.
        
        Arguments:
            title: Optional title for the window
            content_lines: Optional list of content lines to add
            
        Returns:
            The complete window as a string
        """
        # Clear existing content
        self.clear_menu()
        
        # Add title if provided
        if title:
            self.add_title(title)
        
        # Add content lines if provided
        if content_lines:
            for line in content_lines:
                if line.strip() == "":
                    self.add_empty_line()
                elif line == "VERTICAL_START":
                    # Mark for vertical section (visual only, no action needed)
                    continue
                elif line == "VERTICAL_END":
                    # End of vertical section
                    continue
                elif line == "VERTICAL_SEP":
                    # Small visual separator within vertical section
                    self.add_empty_line()
                elif line.startswith("COLUMNS:"):
                    # Add pre-formatted column line
                    column_content = line[8:]  # Remove "COLUMNS:" prefix
                    self.lines.append(f"║{column_content}║")
                elif line.startswith("SEPARATOR"):
                    self.add_separator()
                elif line.startswith("CENTER:"):
                    self.add_centered_line(line[7:])
                elif line.startswith("LEFT:"):
                    parts = line[5:].split("|", 1)
                    text = parts[0] if parts else ""
                    indent = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 2
                    self.add_left_aligned_line(text, indent)
                elif line.startswith("OPTION:"):
                    parts = line[7:].split("|", 2)
                    key = parts[0] if len(parts) > 0 else ""
                    desc = parts[1] if len(parts) > 1 else ""
                    indent = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 4
                    self.add_option(key, desc, indent)
                else:
                    # Default to left-aligned with default indent
                    self.add_left_aligned_line(line)
        
        # Build the complete window
        window_lines = []
        
        # Top border
        window_lines.append("╔" + "═" * self.content_width + "╗")
        
        # Content lines
        window_lines.extend(self.lines)
        
        # Bottom border
        window_lines.append("╚" + "═" * self.content_width + "╝")
        
        return "\n".join(window_lines)
    
    def display_window(self, title: Optional[str] = None, content_lines: Optional[List[str]] = None, clear_first: bool = True) -> None:
        """
        Display a complete window in the terminal.
        
        Arguments:
            title: Optional title for the window
            content_lines: Optional list of content lines to add
            clear_first: Whether to clear the terminal before displaying
        """
        if clear_first:
            self.clear_terminal()
        
        window = self.create_window(title, content_lines)
        print(window)
        print()  # Add extra line after menu
    
    def create_simple_menu(self, content_structure: List) -> None:
        """
        Create a menu with flexible structure supporting horizontal and vertical divisions.
        
        Arguments:
            content_structure: List containing strings or nested lists
                - String elements are added as full-width lines
                - Nested lists create columns divided by vertical lines (│)
                - Each top-level element is separated by horizontal lines (═)
                - Use commas (,) within a column string to create multiple lines in that column
                
        Examples:
            Simple vertical menu:
                ["Line 1", "Line 2", "Line 3"]
                Creates three lines separated by horizontal dividers
            
            Horizontal columns:
                [["Column 1", "Column 2", "Column 3"]]
                Creates three columns side-by-side separated by vertical dividers
            
            Multi-line content in columns (using commas):
                [["Line 1, Line 2, Line 3", "Col2 Line1, Col2 Line2"]]
                Creates two columns where each contains multiple lines
            
            Mixed layout:
                [
                    "Full width title",
                    ["Left column", "Right column"],
                    "Full width footer"
                ]
                
            Multiple rows of columns:
                [
                    ["Col1 Row1", "Col2 Row1"],
                    ["Col1 Row2", "Col2 Row2"]
                ]
                
            Complex multi-line columns:
                [
                    "CENTER:MENU TITLE",
                    ["LEFT:Item 1, LEFT:Item 2, LEFT:Item 3", "RIGHT:Value A, RIGHT:Value B, RIGHT:Value C"]
                ]
        """
        content = []
        
        # Process the content structure
        for i, element in enumerate(content_structure):
            # Add horizontal separator before each element after the first
            if i > 0:
                content.append("SEPARATOR")
            
            if isinstance(element, str):
                # Simple string element - add as full-width line
                content.append(element)
            elif isinstance(element, list):
                # List element - create columns with vertical divisions
                self._add_columns(element, content)
        
        self.display_window(None, content)
    
    def _add_columns(self, columns: List[str], content: List[str]) -> None:
        """
        Add a row with multiple columns separated by vertical dividers.
        Supports multi-line content within columns using comma separators.
        
        Arguments:
            columns: List of strings to display as columns
                - Each string can contain commas to create multiple lines within that column
            content: The content list to append processed lines to
        """
        if not columns:
            return
        
        num_columns = len(columns)
        if num_columns == 1:
            # Single column, just add as normal line(s)
            # Check if it contains commas for multi-line content
            if "," in columns[0]:
                lines = [line.strip() for line in columns[0].split(",")]
                for line in lines:
                    content.append(line)
            else:
                content.append(columns[0])
            return
        
        # Calculate column widths (equal distribution with space for dividers)
        divider_space = num_columns - 1  # Number of │ characters needed
        available_width = self.content_width - divider_space
        col_width = available_width // num_columns
        
        # Parse each column to extract lines (comma-separated) and formatting
        parsed_columns = []
        max_lines = 1
        
        for col_text in columns:
            # Check if column contains commas (multi-line content)
            if "," in col_text:
                # Split by comma to get multiple lines
                lines = [line.strip() for line in col_text.split(",")]
                max_lines = max(max_lines, len(lines))
                parsed_columns.append(lines)
            else:
                # Single line
                parsed_columns.append([col_text])
        
        # Generate each row of the multi-line columns
        for line_idx in range(max_lines):
            column_parts = []
            
            for col_idx, col_lines in enumerate(parsed_columns):
                # Get the text for this line (or empty if this column has fewer lines)
                if line_idx < len(col_lines):
                    col_text = col_lines[line_idx]
                else:
                    col_text = ""
                
                # Extract formatting prefix if present
                if col_text and ":" in col_text and col_text.split(":", 1)[0] in ["CENTER", "LEFT", "RIGHT", "OPTION"]:
                    format_type, text = col_text.split(":", 1)
                    
                    # Handle OPTION format
                    if format_type == "OPTION":
                        parts = text.split("|", 2)
                        key = parts[0] if len(parts) > 0 else ""
                        desc = parts[1] if len(parts) > 1 else ""
                        text = f"{key} → {desc}"
                    
                    # Format the text based on type
                    if format_type == "CENTER":
                        formatted = text[:col_width].center(col_width)
                    elif format_type == "RIGHT":
                        formatted = text[:col_width].rjust(col_width)
                    else:  # LEFT or OPTION
                        formatted = ("  " + text)[:col_width].ljust(col_width)
                else:
                    # No format specified, default to left-aligned with indent
                    if col_text:
                        formatted = ("  " + col_text)[:col_width].ljust(col_width)
                    else:
                        # Empty cell
                        formatted = " " * col_width
                
                column_parts.append(formatted)
            
            # Join columns with vertical divider
            column_line = "│".join(column_parts)
            content.append(f"COLUMNS:{column_line}")
    
    def create_menu_with_title(self, title: str, content_structure: List, subtitle: Optional[str] = None) -> None:
        """
        Create a menu with title and optional subtitle.
        
        Arguments:
            title: Main title of the menu
            content_structure: Menu content structure (same as create_simple_menu)
            subtitle: Optional subtitle text
        """
        structure = []
        
        # Add title
        structure.append("")
        structure.append(f"CENTER:{title}")
        structure.append("")
        
        # Add subtitle if provided
        if subtitle:
            structure.append(f"CENTER:{subtitle}")
            structure.append("")
        
        # Add the rest of the content
        structure.extend(content_structure)
        
        self.create_simple_menu(structure)
    
    def create_info_window(self, title: str, message: str, width: Optional[int] = None) -> None:
        """
        Create a simple information window with a message.
        
        Arguments:
            title: Title of the window
            message: Message to display
            width: Optional custom width for this window
        """
        original_width = self.width
        if width:
            self.width = width
            self.content_width = self.width - 2
        
        content = [f"CENTER:{message}"]
        self.display_window(title, content)
        
        # Restore original width
        if width:
            self.width = original_width
            self.content_width = self.width - 2
    
    def create_error_window(self, error_message: str) -> None:
        """
        Create an error display window.
        
        Arguments:
            error_message: The error message to display
        """
        self.create_info_window("ERROR OCCURRED", error_message, 45)
    
    def create_goodbye_window(self) -> None:
        """Create a goodbye message window."""
        content = [
            "",
            "CENTER:Thank you for using",
            "CENTER:Mouse and Keyboard Replayer!",
            ""
        ]
        self.display_window("GOODBYE!", content, width=40)
