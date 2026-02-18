# Money Splitter

A PyQt5 desktop application for splitting shared expenses on trips — similar to Splitwise, but running locally as a windowed Python app.

## Features

- Add / remove people dynamically
- Excel-like expense table with right-click row management
- Per-cell configuration: amount, currency, and people-split
- **Fully dynamic currencies** — add, remove, and configure currencies at runtime
- **Configurable base currency** with automatic rate recalculation when switching
- Visual indicators: text colour by currency (auto-assigned theme palette), muted background for partial splits
- One-click balance calculation with currency conversion via any base-currency pivot
- **Fetch live exchange rates** from the internet (🌐 button) — powered by open.er-api.com
- **Branded header** with MoneySplitter logo and version display
- **4 colour themes** — Dracula, Monokai, Nord, Solarized Light — selectable from *View → Theme* (remembered across sessions)
- Save / Load buttons on the side panel plus File menu shortcuts (Ctrl+S / Ctrl+O)
- Buildable to a standalone `.exe` via PyInstaller

## Requirements

- Python 3.9+
- PyQt5 >= 5.15

Install dependencies:

```bash
pip install -r requirements.txt
```

## Run

```bash
python main.py
```

Or on Windows:

```bash
run.bat
```

## Usage

1. **Add people** — click *Add Person* on the right panel.
2. **Enter expenses** — double-click any cell to set the amount, currency, and who the expense is split among.
3. **Manage rows** — right-click the table to add a row. Select row(s) on the left header and right-click to delete.
4. **Manage currencies** — click *Manage Currencies* to add / remove currencies with their conversion rate to the base currency.
5. **Set conversion rates** — click *Conversion Rates* to edit rates and optionally switch the base currency (rates recalculate automatically).
6. **Calculate** — pick the result currency in the dropdown and press **CALCULATE**. Balances appear in the compact bottom table.
7. **Save / Load** — use the *Save* / *Load* buttons on the side panel, or *File → Save / Open* (Ctrl+S / Ctrl+O).

## Cell colour scheme

Currencies are coloured automatically from the active theme’s palette:

| Index | Slot        | Default currency |
|-------|-------------|------------------|
| 0     | Foreground  | HUF              |
| 1     | Cyan        | EUR              |
| 2     | Green       | USD              |
| 3+    | Orange, Pink, Yellow, Purple, Red … | user-added |

Cells where the expense is **not** split among everyone get a **muted accent** background.

## Building an executable

```bash
build_exe.bat
```

The resulting `MoneySplitter.exe` appears in the `dist/` folder.

## Project structure

```
MoneySplitter/Python/
├── main.py                  # Entry point
├── logo_MS.png              # Window icon
├── MoneySplitter_logo.png   # Header logo (full text)
├── settings.json            # Auto-generated user preferences (theme, etc.)
├── ui/                      # Frontend — GUI layer
│   ├── __init__.py
│   ├── main_window.py       # Main window UI + View menu
│   └── dialogs.py           # Pop-up dialogs
├── logic/                   # Middle layer — business logic
│   ├── __init__.py
│   ├── calculator.py        # Balance calculation & currency conversion
│   ├── constants.py         # App-wide constants & theme-aware colour helpers
│   └── themes.py            # Colour theme definitions, stylesheet & palette builders
├── data/                    # Backend — data models & persistence
│   ├── __init__.py
│   ├── models.py            # Data models (CellData, TripData)
│   ├── persistence.py       # JSON save/load
│   └── settings.py          # User settings persistence (theme, etc.)
├── json_saves/              # User-saved trip files
├── requirements.txt         # Python dependencies
├── run.bat                  # Quick launcher
├── build_exe.bat            # PyInstaller build script
└── README.md                # This file
```

## Version

0.0.4
