"""Pop-up dialogs for the Remote Desktop Connector."""

from __future__ import annotations

import tkinter as tk
from tkinter import messagebox, ttk
from typing import Optional

from logic.themes import DEFAULT_THEME, THEMES


class AddConnectionDialog(tk.Toplevel):
    """Dialog window for adding or editing a remote desktop connection."""

    def __init__(
        self,
        parent: tk.Tk,
        edit_connection: Optional[dict] = None,
    ) -> None:
        super().__init__(parent)
        self.parent = parent
        self.result: dict | None = None
        self.edit_mode = edit_connection is not None

        # Apply theme colours to Toplevel
        t = THEMES[getattr(parent, "_current_theme_name", DEFAULT_THEME)]
        self.configure(bg=t["bg"])

        self.title("Edit Connection" if self.edit_mode else "Add New Connection")
        self.geometry("400x280")
        self.resizable(False, False)
        self.transient(parent)
        self.grab_set()

        # Centre over parent
        self.update_idletasks()
        x = parent.winfo_x() + (parent.winfo_width() // 2) - 200
        y = parent.winfo_y() + (parent.winfo_height() // 2) - 140
        self.geometry(f"+{x}+{y}")

        self._create_widgets(edit_connection)
        self.name_entry.focus_set()

    # ------------------------------------------------------------------
    def _create_widgets(self, edit_connection: Optional[dict]) -> None:
        main_frame = ttk.Frame(self, padding=20)
        main_frame.pack(fill=tk.BOTH, expand=True)

        labels = ("Button Name:", "Computer Name/IP:", "Username:", "Password:")
        self.name_entry = ttk.Entry(main_frame, width=35)
        self.computer_entry = ttk.Entry(main_frame, width=35)
        self.username_entry = ttk.Entry(main_frame, width=35)
        self.password_entry = ttk.Entry(main_frame, width=35, show="*")
        entries = (self.name_entry, self.computer_entry,
                   self.username_entry, self.password_entry)

        for row, (lbl, ent) in enumerate(zip(labels, entries)):
            ttk.Label(main_frame, text=lbl).grid(
                row=row, column=0, sticky=tk.W, pady=5,
            )
            ent.grid(row=row, column=1, pady=5, padx=(10, 0))

        # Show-password toggle
        self.show_password_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            main_frame, text="Show password",
            variable=self.show_password_var,
            command=self._toggle_password_visibility,
        ).grid(row=4, column=1, sticky=tk.W, pady=5, padx=(10, 0))

        # Pre-fill when editing
        if edit_connection:
            self.name_entry.insert(0, edit_connection.get("name", ""))
            self.computer_entry.insert(0, edit_connection.get("computer", ""))
            self.username_entry.insert(0, edit_connection.get("username", ""))
            self.password_entry.insert(0, edit_connection.get("password", ""))

        # Buttons
        btn_frame = ttk.Frame(main_frame)
        btn_frame.grid(row=5, column=0, columnspan=2, pady=20)
        ttk.Button(btn_frame, text="Save", command=self._on_save, width=12
                   ).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Cancel", command=self._on_cancel, width=12
                   ).pack(side=tk.LEFT, padx=5)

        self.bind("<Return>", lambda e: self._on_save())
        self.bind("<Escape>", lambda e: self._on_cancel())

    # ------------------------------------------------------------------
    def _toggle_password_visibility(self) -> None:
        self.password_entry.config(show="" if self.show_password_var.get() else "*")

    def _on_save(self) -> None:
        name = self.name_entry.get().strip()
        computer = self.computer_entry.get().strip()
        username = self.username_entry.get().strip()
        password = self.password_entry.get()

        for val, label, widget in (
            (name, "Button name", self.name_entry),
            (computer, "Computer name/IP", self.computer_entry),
            (username, "Username", self.username_entry),
            (password, "Password", self.password_entry),
        ):
            if not val:
                messagebox.showerror("Error", f"{label} is required.", parent=self)
                widget.focus_set()
                return

        self.result = {
            "name": name, "computer": computer,
            "username": username, "password": password,
        }
        self.destroy()

    def _on_cancel(self) -> None:
        self.result = None
        self.destroy()
