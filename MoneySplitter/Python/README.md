# Money Splitter

A PyQt5 desktop application for splitting shared expenses on trips — similar to Splitwise, but running locally as a windowed Python app.

## Features

- Add / remove people dynamically
- Excel-like expense table with right-click row management
- Per-cell configuration: amount, currency (HUF / EUR / USD), and people-split
- Visual indicators: text colour by currency, pale-purple background for partial splits
- One-click balance calculation with currency conversion
- Editable HUF-based conversion rates for USD and EUR
- Save / load trip data as JSON
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
4. **Set conversion rates** — click *Conversion Rates* to define HUF per USD / EUR.
5. **Calculate** — pick the result currency in the dropdown and press **CALCULATE**. Balances appear in the bottom table.
6. **Save / Load** — use *File → Save / Open* (Ctrl+S / Ctrl+O) to persist trip data as JSON.

## Cell colour scheme

| Currency | Text colour |
|----------|-------------|
| HUF      | Black       |
| EUR      | Blue        |
| USD      | Green       |

Cells where the expense is **not** split among everyone get a **pale purple** background.

## Building an executable

```bash
build_exe.bat
```

The resulting `MoneySplitter.exe` appears in the `dist/` folder.

## Project structure

```
MoneySplitter/Python/
├── main.py            # Entry point
├── main_window.py     # Main window UI
├── dialogs.py         # Pop-up dialogs (add/remove person, cell editor, rates)
├── models.py          # Data models (CellData, TripData)
├── calculator.py      # Balance calculation logic
├── persistence.py     # JSON save/load
├── constants.py       # App-wide constants and defaults
├── requirements.txt   # Python dependencies
├── run.bat            # Quick launcher
├── build_exe.bat      # PyInstaller build script
└── README.md          # This file
```

## Version

0.0.1
