# CsasziGit

**Git GUI tool** inspired by Git Extensions — built with Python & PyQt6.

| Info | Value |
|---|---|
| Language | Python 3.11+ |
| UI Framework | PyQt6 (Fusion style) |
| Default Theme | Dracula |
| Themes | Dracula, Dark, Bright |

## Features

- **Fully dockable layout** — every panel (Branches, Folder Browser, Commit Log, Diff Viewer, Diff Only, AI Assistant, Git Terminal) is a movable, closable, floatable dock widget. Drag and drop panels next to, above, or below any other panel. Close with the ✕ button and restore from the *Window* menu. Nested docking is enabled for maximum flexibility.
- **Window menu** — toggle visibility of each panel individually, with a *Restore Default Layout* action to reset everything back.
- **Commit graph** — visual branch/merge graph rendered with coloured dots and connecting lines via a custom `QStyledItemDelegate`, similar to Git Extensions / gitk.
- **Add/Commit window** — staged and working-directory files are managed in a dedicated dialog (opened from Add/Commit), with built-in diff preview, stage/unstage actions (right-click or double-click), and commit message entry. Untracked (new) files appear alongside modified files for easy discovery.
- **Folder browser** — a dockable Explorer-style folder tree that shows the filesystem as a collapsible tree. Single-click a folder to check if it’s a Git repo; double-click to open it. The root folder is configurable.
- **Diff Viewer** — syntax-highlighted unified diff display. Supports `+/-` colouring, `@@` hunk headers, file headers, and commit metadata.
- **Diff Only** — a companion tab (tabified with the Diff Viewer) that strips all context lines and shows only the changed `+`/`-` lines plus file and hunk headers, for a focused differences-only view.
- **Push / Pull / Fetch / Fetch All** — toolbar and menu actions with error reporting. `Fetch` uses the default remote and `Fetch All` runs `git fetch --all --prune`.
- **Branch management** — tree view of local and remote branches with double-click checkout, right-click merge, create new branch, and delete (with force-delete prompt on unmerged branches). Tags are shown separately.
- **Stash** — stash save (with optional message) and stash pop from toolbar and menu.
- **Three themes** — Dracula (default), Dark, and Bright. Switchable from Tools → Settings with instant preview. Comprehensive QSS covers every Qt widget type.
- **AI-assisted Git commands** — when an OpenAI API key is configured (Tools → Settings), a dockable AI Assistant panel appears. Type what you want in natural language (e.g. "undo the last commit but keep changes"), GPT suggests the exact `git` commands, and you can **Approve & Run** or **Rephrase**. Approved commands execute in a log window showing output. Repository context (current branch, status) is automatically sent to GPT for accurate suggestions.
- **Git Terminal** — a dockable interactive command-line panel where you can type any `git` command and see its output inline, just like a Git Bash shell. Supports command history via Up/Down arrow keys. Toggle from *Window → Git Terminal* or `Ctrl+Shift+T`.
- **Git Command Reference** — a searchable, scrollable list of 340+ git commands with concise explanations, built into the terminal panel. The Command column auto-resizes to fit visible content so descriptions always start right after the command text. Type in the search bar to filter instantly. Double-click any command to paste it into the terminal input.
- **Commit comparison** — select two commits with Ctrl+click in the commit log, right-click → **Compare** to launch CsasziCompare with a side-by-side view of all changed files between those commits.
- **Folder picker** — open any Git repository on your machine. Last opened repo is remembered between sessions.
- **Keyboard shortcuts** — `Ctrl+O` (open), `Ctrl+Enter` (Add/Commit), `Ctrl+Shift+P` (push), `Ctrl+Shift+L` (pull), `Ctrl+Shift+F` (fetch), `Ctrl+Alt+F` (fetch all), `Ctrl+Shift+A` (toggle AI panel), `Ctrl+Shift+T` (toggle Git Terminal), `F5` (refresh), `Ctrl+Q` (quit).

## Project structure

```
CsasziGit/
├── main.py                         # Entry point
├── requirements.txt                # PyQt6, requests
├── run.bat                         # Windows launcher
├── README.md
└── csaszigit/
    ├── __init__.py
    ├── git_ops.py                  # All Git CLI operations (subprocess)
    ├── gpt_helper.py               # OpenAI Chat Completions integration
    ├── settings.py                 # QSettings persistence
    ├── themes.py                   # Dracula / Dark / Bright QSS + QPalette
    ├── main_window.py              # Main window layout, menus, toolbar
    └── widgets/
        ├── __init__.py
        ├── ai_assistant.py         # GPT-powered command assistant panel
        ├── branch_panel.py         # Branch tree with management actions
        ├── commit_log.py           # Commit history with graph delegate
        ├── diff_viewer.py          # Syntax-highlighted diff display
        ├── file_status.py          # Staged / working-directory file panel
        ├── folder_browser.py       # Explorer-style folder tree browser
        ├── git_terminal.py         # Interactive git terminal + command reference
        └── settings_dialog.py      # Theme + GPT key settings
```

## Running

```bash
pip install -r requirements.txt
python main.py
```

Or on Windows:
```cmd
run.bat
```

## Dependencies

| Package | Purpose |
|---|---|
| PyQt6 | GUI framework |
| requests | OpenAI API calls (AI assistant) |
| git (CLI) | Must be available on PATH |
