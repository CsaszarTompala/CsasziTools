"""
Remote Desktop Connection GUI Application.

A windowed application that provides buttons to connect to multiple remote desktops.
Allows adding new remote desktop connections through a configuration dialog.

Usage:
    python remote_desktop_gui.py
"""

import json
import os
import subprocess
import sys
import tkinter as tk
from tkinter import messagebox, ttk
from typing import Optional


# Configuration file path
CONFIG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "connections.json")

# Default connections from existing batch files
DEFAULT_CONNECTIONS = [
    {
        "name": "uuddcrzw",
        "computer": "uuddcrzw",
        "username": "AUTOMOTIVE-WAN\\uidr1926",
        "password": "Conti00019"
    },
    {
        "name": "aqlde70w",
        "computer": "aqlde70w",
        "username": "AUTOMOTIVE-WAN\\uig40210",
        "password": "DamonHIL24!"
    },
    {
        "name": "Chewie",
        "computer": "LUDC0PVN",
        "username": "AUTOMOTIVE-WAN\\uie20103",
        "password": "?Jenk#Test1313?"
    },
    {
        "name": "Luke",
        "computer": "ludfl3dn",
        "username": "AUTOMOTIVE-WAN\\uie20103",
        "password": "?Jenk#Test1313?"
    }
]


def load_connections() -> list:
    """
    Load connections from the JSON configuration file.

    Arguments:
        None

    Returns:
        list: List of connection dictionaries. Returns default connections if file doesn't exist.
    """
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return DEFAULT_CONNECTIONS.copy()
    return DEFAULT_CONNECTIONS.copy()


def save_connections(connections: list) -> bool:
    """
    Save connections to the JSON configuration file.

    Arguments:
        connections (list): List of connection dictionaries to save.

    Returns:
        bool: True if saved successfully, False otherwise.
    """
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(connections, f, indent=4)
        return True
    except IOError:
        return False


def store_credentials(computer_name: str, username: str, password: str) -> bool:
    """
    Store credentials using Windows cmdkey utility.

    Arguments:
        computer_name (str): The name or IP address of the remote computer.
        username (str): The username for authentication.
        password (str): The password for authentication.

    Returns:
        bool: True if credentials were stored successfully, False otherwise.
    """
    cmd = f'cmdkey /generic:TERMSRV/{computer_name} /user:{username} /pass:{password}'
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0


def connect_to_remote_desktop(computer_name: str) -> bool:
    """
    Launch Remote Desktop Connection to the specified computer.

    Arguments:
        computer_name (str): The name or IP address of the remote computer.

    Returns:
        bool: True if the connection was initiated successfully, False otherwise.
    """
    try:
        # Use Popen instead of run to avoid blocking the GUI thread
        # mstsc runs independently and we don't wait for it to finish
        subprocess.Popen(
            f'mstsc /v:{computer_name}',
            shell=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        return True
    except Exception:
        return False


class AddConnectionDialog(tk.Toplevel):
    """Dialog window for adding a new remote desktop connection."""

    def __init__(self, parent: tk.Tk, edit_connection: Optional[dict] = None) -> None:
        """
        Initialize the Add Connection dialog.

        Arguments:
            parent (tk.Tk): The parent window.
            edit_connection (Optional[dict]): Connection to edit, None for new connection.

        Returns:
            None
        """
        super().__init__(parent)
        self.parent = parent
        self.result = None
        self.edit_mode = edit_connection is not None
        # Window configuration
        self.title("Edit Connection" if self.edit_mode else "Add New Connection")
        self.geometry("400x280")
        self.resizable(False, False)
        self.transient(parent)
        self.grab_set()
        # Center the dialog
        self.update_idletasks()
        x = parent.winfo_x() + (parent.winfo_width() // 2) - (400 // 2)
        y = parent.winfo_y() + (parent.winfo_height() // 2) - (280 // 2)
        self.geometry(f"+{x}+{y}")
        # Create widgets
        self._create_widgets(edit_connection)
        # Focus on first entry
        self.name_entry.focus_set()

    def _create_widgets(self, edit_connection: Optional[dict]) -> None:
        """
        Create all widgets for the dialog.

        Arguments:
            edit_connection (Optional[dict]): Connection data to populate fields with.

        Returns:
            None
        """
        main_frame = ttk.Frame(self, padding=20)
        main_frame.pack(fill=tk.BOTH, expand=True)
        # Button Name
        ttk.Label(main_frame, text="Button Name:").grid(row=0, column=0, sticky=tk.W, pady=5)
        self.name_entry = ttk.Entry(main_frame, width=35)
        self.name_entry.grid(row=0, column=1, pady=5, padx=(10, 0))
        # Computer Name/IP
        ttk.Label(main_frame, text="Computer Name/IP:").grid(row=1, column=0, sticky=tk.W, pady=5)
        self.computer_entry = ttk.Entry(main_frame, width=35)
        self.computer_entry.grid(row=1, column=1, pady=5, padx=(10, 0))
        # Username
        ttk.Label(main_frame, text="Username:").grid(row=2, column=0, sticky=tk.W, pady=5)
        self.username_entry = ttk.Entry(main_frame, width=35)
        self.username_entry.grid(row=2, column=1, pady=5, padx=(10, 0))
        # Password
        ttk.Label(main_frame, text="Password:").grid(row=3, column=0, sticky=tk.W, pady=5)
        self.password_entry = ttk.Entry(main_frame, width=35, show="*")
        self.password_entry.grid(row=3, column=1, pady=5, padx=(10, 0))
        # Show password checkbox
        self.show_password_var = tk.BooleanVar(value=False)
        show_password_cb = ttk.Checkbutton(
            main_frame,
            text="Show password",
            variable=self.show_password_var,
            command=self._toggle_password_visibility
        )
        show_password_cb.grid(row=4, column=1, sticky=tk.W, pady=5, padx=(10, 0))
        # Populate fields if editing
        if edit_connection:
            self.name_entry.insert(0, edit_connection.get("name", ""))
            self.computer_entry.insert(0, edit_connection.get("computer", ""))
            self.username_entry.insert(0, edit_connection.get("username", ""))
            self.password_entry.insert(0, edit_connection.get("password", ""))
        # Buttons frame
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=5, column=0, columnspan=2, pady=20)
        ttk.Button(button_frame, text="Save", command=self._on_save, width=12).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="Cancel", command=self._on_cancel, width=12).pack(side=tk.LEFT, padx=5)
        # Bind Enter key
        self.bind("<Return>", lambda e: self._on_save())
        self.bind("<Escape>", lambda e: self._on_cancel())

    def _toggle_password_visibility(self) -> None:
        """
        Toggle password visibility in the password entry field.

        Arguments:
            None

        Returns:
            None
        """
        if self.show_password_var.get():
            self.password_entry.config(show="")
        else:
            self.password_entry.config(show="*")

    def _on_save(self) -> None:
        """
        Handle save button click.

        Arguments:
            None

        Returns:
            None
        """
        name = self.name_entry.get().strip()
        computer = self.computer_entry.get().strip()
        username = self.username_entry.get().strip()
        password = self.password_entry.get()
        # Validate inputs
        if not name:
            messagebox.showerror("Error", "Button name is required.", parent=self)
            self.name_entry.focus_set()
            return
        if not computer:
            messagebox.showerror("Error", "Computer name/IP is required.", parent=self)
            self.computer_entry.focus_set()
            return
        if not username:
            messagebox.showerror("Error", "Username is required.", parent=self)
            self.username_entry.focus_set()
            return
        if not password:
            messagebox.showerror("Error", "Password is required.", parent=self)
            self.password_entry.focus_set()
            return
        self.result = {
            "name": name,
            "computer": computer,
            "username": username,
            "password": password
        }
        self.destroy()

    def _on_cancel(self) -> None:
        """
        Handle cancel button click.

        Arguments:
            None

        Returns:
            None
        """
        self.result = None
        self.destroy()


class RemoteDesktopApp(tk.Tk):
    """Main application window for Remote Desktop connections."""

    def __init__(self) -> None:
        """
        Initialize the main application window.

        Arguments:
            None

        Returns:
            None
        """
        super().__init__()
        self.title("Remote Desktop Connector")
        self.geometry("350x450")
        self.minsize(300, 350)
        self.resizable(True, True)
        # Load connections
        self.connections = load_connections()
        # Create widgets
        self._create_widgets()
        # Center the window on screen
        self._center_window()

    def _center_window(self) -> None:
        """
        Center the window on the screen.

        Arguments:
            None

        Returns:
            None
        """
        self.update_idletasks()
        width = self.winfo_width()
        height = self.winfo_height()
        screen_width = self.winfo_screenwidth()
        screen_height = self.winfo_screenheight()
        x = (screen_width // 2) - (width // 2)
        y = (screen_height // 2) - (height // 2)
        self.geometry(f"+{x}+{y}")

    def _create_widgets(self) -> None:
        """
        Create all widgets for the main window.

        Arguments:
            None

        Returns:
            None
        """
        # Title label
        title_label = ttk.Label(
            self,
            text="Remote Desktop Connector",
            font=("Segoe UI", 14, "bold")
        )
        title_label.pack(pady=(20, 10))
        # Connections frame with scrollbar
        container = ttk.Frame(self)
        container.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)
        # Canvas for scrolling
        self.canvas = tk.Canvas(container, highlightthickness=0)
        scrollbar = ttk.Scrollbar(container, orient=tk.VERTICAL, command=self.canvas.yview)
        self.scrollable_frame = ttk.Frame(self.canvas)
        self.scrollable_frame.bind(
            "<Configure>",
            lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all"))
        )
        self.canvas.create_window((0, 0), window=self.scrollable_frame, anchor=tk.NW)
        self.canvas.configure(yscrollcommand=scrollbar.set)
        self.canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        # Bind mouse wheel
        self.canvas.bind_all("<MouseWheel>", self._on_mousewheel)
        # Create connection buttons
        self._refresh_buttons()
        # Bottom buttons frame
        bottom_frame = ttk.Frame(self)
        bottom_frame.pack(fill=tk.X, padx=20, pady=(10, 20))
        # Add connection button
        add_btn = ttk.Button(
            bottom_frame,
            text="➕ Add New Connection",
            command=self._add_connection
        )
        add_btn.pack(fill=tk.X, pady=5)

    def _on_mousewheel(self, event: tk.Event) -> None:
        """
        Handle mouse wheel scrolling.

        Arguments:
            event (tk.Event): The mouse wheel event.

        Returns:
            None
        """
        self.canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

    def _refresh_buttons(self) -> None:
        """
        Refresh all connection buttons in the scrollable frame.

        Arguments:
            None

        Returns:
            None
        """
        # Clear existing buttons
        for widget in self.scrollable_frame.winfo_children():
            widget.destroy()
        # Create buttons for each connection
        for idx, conn in enumerate(self.connections):
            btn_frame = ttk.Frame(self.scrollable_frame)
            btn_frame.pack(fill=tk.X, pady=3)
            # Connection button
            connect_btn = ttk.Button(
                btn_frame,
                text=f"🖥️ {conn['name']}",
                command=lambda c=conn: self._connect(c)
            )
            connect_btn.pack(side=tk.LEFT, fill=tk.X, expand=True)
            # Edit button
            edit_btn = ttk.Button(
                btn_frame,
                text="✏️",
                width=3,
                command=lambda i=idx: self._edit_connection(i)
            )
            edit_btn.pack(side=tk.LEFT, padx=(5, 0))
            # Delete button
            delete_btn = ttk.Button(
                btn_frame,
                text="🗑️",
                width=3,
                command=lambda i=idx: self._delete_connection(i)
            )
            delete_btn.pack(side=tk.LEFT, padx=(2, 0))
        # Update canvas scroll region
        self.scrollable_frame.update_idletasks()
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _connect(self, connection: dict) -> None:
        """
        Connect to a remote desktop.

        Arguments:
            connection (dict): The connection details dictionary.

        Returns:
            None
        """
        computer = connection["computer"]
        username = connection["username"]
        password = connection["password"]
        # Store credentials and connect
        if not store_credentials(computer, username, password):
            messagebox.showerror(
                "Error",
                f"Failed to store credentials for {connection['name']}.",
                parent=self
            )
            return
        if not connect_to_remote_desktop(computer):
            messagebox.showerror(
                "Error",
                f"Failed to launch Remote Desktop for {connection['name']}.",
                parent=self
            )
            return

    def _add_connection(self) -> None:
        """
        Open dialog to add a new connection.

        Arguments:
            None

        Returns:
            None
        """
        dialog = AddConnectionDialog(self)
        self.wait_window(dialog)
        if dialog.result:
            self.connections.append(dialog.result)
            if save_connections(self.connections):
                self._refresh_buttons()
            else:
                messagebox.showerror(
                    "Error",
                    "Failed to save connections.",
                    parent=self
                )

    def _edit_connection(self, index: int) -> None:
        """
        Open dialog to edit an existing connection.

        Arguments:
            index (int): The index of the connection to edit.

        Returns:
            None
        """
        dialog = AddConnectionDialog(self, self.connections[index])
        self.wait_window(dialog)
        if dialog.result:
            self.connections[index] = dialog.result
            if save_connections(self.connections):
                self._refresh_buttons()
            else:
                messagebox.showerror(
                    "Error",
                    "Failed to save connections.",
                    parent=self
                )

    def _delete_connection(self, index: int) -> None:
        """
        Delete a connection after confirmation.

        Arguments:
            index (int): The index of the connection to delete.

        Returns:
            None
        """
        conn_name = self.connections[index]["name"]
        if messagebox.askyesno(
            "Confirm Delete",
            f"Are you sure you want to delete '{conn_name}'?",
            parent=self
        ):
            del self.connections[index]
            if save_connections(self.connections):
                self._refresh_buttons()
            else:
                messagebox.showerror(
                    "Error",
                    "Failed to save connections.",
                    parent=self
                )


def main() -> int:
    """
    Main function to run the Remote Desktop GUI application.

    Arguments:
        None

    Returns:
        int: Exit code (0 for success).
    """
    app = RemoteDesktopApp()
    app.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
