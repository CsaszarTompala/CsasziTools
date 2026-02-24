# CsasziCompare

A standalone file, directory, and Git commit comparison tool built with **PyQt6**.  
Inspired by [Meld](https://meldmerge.org/) — side-by-side diffs with character-level highlighting and three-way merge/rebase support.

## Features

| Feature | Description |
|---------|-------------|
| **2-way file comparison** | Side-by-side diff with faint green/red backgrounds for added/removed lines and character-level highlighting for inline changes. |
| **Directory comparison** | File explorer + changed-files list; click any file to show its diff. |
| **3-way merge** | Three-column view (ours / result / theirs) with automatic conflict resolution and manual accept-left/accept-right buttons. |
| **3-way rebase** | Same three-column view with labelling adapted for rebase operations. |
| **Git commit diff** | Compare any two commits — launched from CsasziGit or via CLI. Exports file trees to temp dirs and diffs them. |
| **Themes** | Dracula (default), Dark, and Bright colour schemes. Switch at runtime via View → Theme. |
| **Dockable panels** | File Explorer, Changed Files, Diff View, and Merge View are all QDockWidgets — drag, float, close, and restore freely. |
| **Synchronised scrolling** | Left/right (and centre in merge mode) panes scroll together. |
| **Change navigation** | ▲ Prev / ▼ Next buttons jump between changed hunks in the diff view. |

## Requirements

- Python 3.10+
- PyQt6 ≥ 6.5

Install dependencies:

```bash
pip install -r requirements.txt
```

## Usage

### Run the GUI

```bash
python main.py
```

Or use the batch file on Windows:

```bash
run.bat
```

### CLI arguments

```
python main.py [OPTIONS]

Options:
  --left PATH       Left file or directory
  --right PATH      Right file or directory
  --base PATH       Base/ancestor file (for 3-way merge)
  --mode MODE       compare | merge | rebase  (default: compare)
  --repo PATH       Git repository path (for commit comparison)
  --commit1 HASH    First commit hash
  --commit2 HASH    Second commit hash
  --theme THEME     dracula | dark | bright  (default: dracula)
```

### Examples

```bash
# Compare two files
python main.py --left old.py --right new.py

# Compare two directories
python main.py --left project_v1/ --right project_v2/

# Three-way merge
python main.py --base ancestor.py --left ours.py --right theirs.py --mode merge

# Three-way rebase
python main.py --base ancestor.py --left ours.py --right theirs.py --mode rebase

# Compare two Git commits (used by CsasziGit integration)
python main.py --repo C:\my\repo --commit1 abc1234 --commit2 def5678

# Use bright theme
python main.py --left a.txt --right b.txt --theme bright
```

## Project Structure

```
CsasziCompare/
├── main.py                          # Entry point
├── run.bat                          # Windows launcher
├── requirements.txt                 # PyQt6 dependency
├── README.md                        # This file
└── csaszicompare/
    ├── __init__.py
    ├── diff_engine.py               # Line/char diff, 3-way merge, dir comparison
    ├── main_window.py               # Main window with dockable layout
    ├── themes.py                    # Dracula / Dark / Bright themes
    └── widgets/
        ├── __init__.py
        ├── changed_files.py         # Changed-files list panel
        ├── diff_view.py             # 2-way side-by-side diff view
        ├── file_tree.py             # Filesystem explorer
        └── merge_view.py            # 3-way merge/rebase view
```

## CsasziGit Integration

When two commits are selected in CsasziGit's commit log (Ctrl+click), right-click → **Compare** launches CsasziCompare with:

```
python main.py --repo <repo_path> --commit1 <hash1> --commit2 <hash2>
```

The tool exports both commit trees to temporary directories, diffs them, and shows the results.

## Themes

All three themes include diff-specific colours:

| Element | Dracula | Dark | Bright |
|---------|---------|------|--------|
| Added line (faint) | `#1a3a25` | `#1a2e1a` | `#e6f9e6` |
| Added chars (less faint) | `#2a5a3a` | `#284a28` | `#b3e6b3` |
| Removed line (faint) | `#3a1a1a` | `#2e1a1a` | `#fce8e8` |
| Removed chars (less faint) | `#5a2a2a` | `#4a2828` | `#f0b3b3` |
| Conflict | `#4a3a1a` | `#3a3a1a` | `#FFF9C4` |
