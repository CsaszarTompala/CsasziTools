"""Main application window for the Remote Desktop Connector."""

from __future__ import annotations

import os
import sys
import tkinter as tk
from tkinter import messagebox, ttk
from PIL import Image, ImageTk  # type: ignore[import-untyped]

from logic.themes import (
    ALL_THEME_NAMES,
    DEFAULT_THEME,
    THEMES,
    load_theme_name,
    save_theme_name,
)
from logic.rdp import connect_to_remote_desktop, store_credentials
from data.connections import load_connections, save_connections
from ui.dialogs import AddConnectionDialog


class RemoteDesktopApp(tk.Tk):
    """Top-level window that hosts the connection list and controls."""

    def __init__(self) -> None:
        super().__init__()
        self.title("Remote Desktop Connector")
        self.geometry("400x520")
        self.minsize(340, 400)
        self.resizable(True, True)

        # Window icon
        self._set_window_icon()

        # Theme
        self._current_theme_name = load_theme_name()
        self._apply_theme(self._current_theme_name)

        # Connections
        self.connections = load_connections()

        # Build UI
        self._build_menu_bar()
        self._create_widgets()
        self._center_window()

    # ==================================================================
    # Logo / icon helpers
    # ==================================================================
    @staticmethod
    def _resolve_asset(name: str) -> str | None:
        """Find an asset by *name*.  Works in dev and PyInstaller builds."""
        if getattr(sys, "frozen", False):
            path = os.path.join(sys._MEIPASS, name)  # type: ignore[attr-defined]
            return path if os.path.isfile(path) else None

        project_root = os.path.dirname(
            os.path.dirname(os.path.abspath(__file__))
        )
        path = os.path.join(project_root, name)
        return path if os.path.isfile(path) else None

    def _set_window_icon(self) -> None:
        icon_path = self._resolve_asset("logo_RD.png")
        if icon_path:
            img = Image.open(icon_path)
            self._icon_photo = ImageTk.PhotoImage(img.resize((64, 64), Image.LANCZOS))
            self.iconphoto(True, self._icon_photo)

    # ==================================================================
    # Theme helpers
    # ==================================================================
    def _apply_theme(self, name: str) -> None:
        """Apply a colour theme to the entire application."""
        t = THEMES[name]
        self._current_theme_name = name
        self.configure(bg=t["bg"])

        style = ttk.Style(self)
        style.theme_use("clam")

        style.configure(
            ".", background=t["bg"], foreground=t["fg"],
            fieldbackground=t["entry_bg"], troughcolor=t["current"],
            selectbackground=t["select_bg"], selectforeground=t["fg"],
        )
        style.configure("TFrame", background=t["bg"])
        style.configure("TLabel", background=t["bg"], foreground=t["fg"])
        style.configure(
            "TButton", background=t["btn_bg"], foreground=t["btn_fg"],
            padding=(8, 4), borderwidth=1, relief="flat",
        )
        style.map(
            "TButton",
            background=[("active", t["btn_active"]), ("pressed", t["accent"])],
            foreground=[("active", t["fg"])],
        )
        style.configure(
            "Title.TLabel", font=("Segoe UI", 14, "bold"),
            foreground=t["accent"],
        )
        style.configure("TCheckbutton", background=t["bg"], foreground=t["fg"])
        style.map("TCheckbutton", background=[("active", t["bg"])])
        style.configure(
            "TEntry", fieldbackground=t["entry_bg"],
            foreground=t["entry_fg"], insertcolor=t["fg"],
        )
        style.configure(
            "Vertical.TScrollbar", background=t["current"],
            troughcolor=t["bg"], arrowcolor=t["fg"],
        )
        style.map("Vertical.TScrollbar", background=[("active", t["btn_active"])])

    def _switch_theme(self, name: str) -> None:
        self._apply_theme(name)
        save_theme_name(name)
        for n in ALL_THEME_NAMES:
            var = self._theme_vars.get(n)
            if var:
                var.set(n == name)
        self._refresh_buttons()
        self.canvas.configure(bg=THEMES[name]["bg"])

    # ==================================================================
    # Menu bar
    # ==================================================================
    def _build_menu_bar(self) -> None:
        t = THEMES[self._current_theme_name]
        kw = dict(bg=t["current"], fg=t["fg"],
                  activebackground=t["select_bg"], activeforeground=t["fg"])

        mb = tk.Menu(self, relief="flat", borderwidth=0, **kw)

        view_menu = tk.Menu(mb, tearoff=0, **kw)
        theme_menu = tk.Menu(view_menu, tearoff=0, **kw)

        self._theme_vars: dict[str, tk.BooleanVar] = {}
        for name in ALL_THEME_NAMES:
            var = tk.BooleanVar(value=(name == self._current_theme_name))
            self._theme_vars[name] = var
            theme_menu.add_checkbutton(
                label=name, variable=var,
                command=lambda n=name: self._switch_theme(n),
            )

        view_menu.add_cascade(label="Theme", menu=theme_menu)
        mb.add_cascade(label="View", menu=view_menu)
        self.config(menu=mb)

    # ==================================================================
    # Widget construction
    # ==================================================================
    def _create_widgets(self) -> None:
        # ---- Header: logo image instead of text ----------------------
        hdr_frame = ttk.Frame(self)
        hdr_frame.pack(pady=(14, 6))

        logo_path = self._resolve_asset("RemoteDesktopConnector_logo.png")
        if logo_path:
            img = Image.open(logo_path)
            # Scale to ~300px width keeping aspect ratio
            w, h = img.size
            target_w = 300
            ratio = target_w / w
            img = img.resize((target_w, int(h * ratio)), Image.LANCZOS)
            self._header_logo = ImageTk.PhotoImage(img)
            logo_lbl = tk.Label(
                hdr_frame, image=self._header_logo,
                bg=THEMES[self._current_theme_name]["bg"],
            )
            logo_lbl.pack()
        else:
            # Fallback: plain text
            ttk.Label(
                hdr_frame, text="Remote Desktop Connector",
                style="Title.TLabel",
            ).pack()

        # ---- Scrollable connection list ------------------------------
        container = ttk.Frame(self)
        container.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)

        t = THEMES[self._current_theme_name]
        self.canvas = tk.Canvas(container, highlightthickness=0, bg=t["bg"])
        scrollbar = ttk.Scrollbar(
            container, orient=tk.VERTICAL, command=self.canvas.yview,
        )
        self.scrollable_frame = ttk.Frame(self.canvas)
        self.scrollable_frame.bind(
            "<Configure>",
            lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")),
        )
        self.canvas.create_window((0, 0), window=self.scrollable_frame, anchor=tk.NW)
        self.canvas.configure(yscrollcommand=scrollbar.set)
        self.canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.canvas.bind_all("<MouseWheel>", self._on_mousewheel)

        self._refresh_buttons()

        # ---- Bottom: Add button --------------------------------------
        bottom = ttk.Frame(self)
        bottom.pack(fill=tk.X, padx=20, pady=(10, 20))
        ttk.Button(
            bottom, text="➕ Add New Connection",
            command=self._add_connection,
        ).pack(fill=tk.X, pady=5)

    # ==================================================================
    # Helpers
    # ==================================================================
    def _center_window(self) -> None:
        self.update_idletasks()
        w, h = self.winfo_width(), self.winfo_height()
        x = (self.winfo_screenwidth() // 2) - (w // 2)
        y = (self.winfo_screenheight() // 2) - (h // 2)
        self.geometry(f"+{x}+{y}")

    def _on_mousewheel(self, event: tk.Event) -> None:
        self.canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

    def _refresh_buttons(self) -> None:
        for w in self.scrollable_frame.winfo_children():
            w.destroy()

        for idx, conn in enumerate(self.connections):
            row = ttk.Frame(self.scrollable_frame)
            row.pack(fill=tk.X, pady=3)

            ttk.Button(
                row, text=f"🖥️ {conn['name']}",
                command=lambda c=conn: self._connect(c),
            ).pack(side=tk.LEFT, fill=tk.X, expand=True)

            ttk.Button(
                row, text="✏️", width=3,
                command=lambda i=idx: self._edit_connection(i),
            ).pack(side=tk.LEFT, padx=(5, 0))

            ttk.Button(
                row, text="🗑️", width=3,
                command=lambda i=idx: self._delete_connection(i),
            ).pack(side=tk.LEFT, padx=(2, 0))

        self.scrollable_frame.update_idletasks()
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    # ==================================================================
    # Connection actions
    # ==================================================================
    def _connect(self, connection: dict) -> None:
        computer = connection["computer"]
        if not store_credentials(computer, connection["username"], connection["password"]):
            messagebox.showerror(
                "Error",
                f"Failed to store credentials for {connection['name']}.",
                parent=self,
            )
            return
        if not connect_to_remote_desktop(computer):
            messagebox.showerror(
                "Error",
                f"Failed to launch Remote Desktop for {connection['name']}.",
                parent=self,
            )

    def _add_connection(self) -> None:
        dialog = AddConnectionDialog(self)
        self.wait_window(dialog)
        if dialog.result:
            self.connections.append(dialog.result)
            if save_connections(self.connections):
                self._refresh_buttons()
            else:
                messagebox.showerror("Error", "Failed to save connections.", parent=self)

    def _edit_connection(self, index: int) -> None:
        dialog = AddConnectionDialog(self, self.connections[index])
        self.wait_window(dialog)
        if dialog.result:
            self.connections[index] = dialog.result
            if save_connections(self.connections):
                self._refresh_buttons()
            else:
                messagebox.showerror("Error", "Failed to save connections.", parent=self)

    def _delete_connection(self, index: int) -> None:
        name = self.connections[index]["name"]
        if messagebox.askyesno(
            "Confirm Delete",
            f"Are you sure you want to delete '{name}'?",
            parent=self,
        ):
            del self.connections[index]
            if save_connections(self.connections):
                self._refresh_buttons()
            else:
                messagebox.showerror("Error", "Failed to save connections.", parent=self)
