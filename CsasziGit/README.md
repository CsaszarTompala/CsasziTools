# CsasziGit

**Git GUI tool** inspired by Git Extensions — built with Python & PyQt6.

| Info | Value |
|---|---|
| Language | Python 3.11+ |
| UI Framework | PyQt6 (Fusion style) |
| Default Theme | Dracula |
| Themes | Dracula, Dark, Bright |

## Features

- **Git-Extensions-style layout** — branch panel on the left, commit history log with graph, staged/unstaged/untracked file lists, and syntax-highlighted diff viewer.
- **Commit graph** — visual branch/merge graph rendered with coloured dots and connecting lines via a custom `QStyledItemDelegate`, similar to Git Extensions / gitk.
- **File staging** — stage / unstage individual files or all at once. Context-menu support for quick actions. Untracked files shown separately with "Stage (track)" option.
- **Commit dialog** — write a commit message and commit staged files. Accessible via toolbar, menu, or `Ctrl+Enter`.
- **Push / Pull / Fetch** — toolbar and menu actions with error reporting. Fetch includes `--prune`.
- **Branch management** — tree view of local and remote branches with double-click checkout, right-click merge, create new branch, and delete (with force-delete prompt on unmerged branches). Tags are shown separately.
- **Diff viewer** — syntax-highlighted unified diff display. Supports `+/-` colouring, `@@` hunk headers, file headers, and commit metadata.
- **Stash** — stash save (with optional message) and stash pop from toolbar and menu.
- **Three themes** — Dracula (default), Dark, and Bright. Switchable from Tools → Settings with instant preview. Comprehensive QSS covers every Qt widget type.
- **AI-assisted Git commands** — when an OpenAI API key is configured (Tools → Settings), a dockable AI Assistant panel appears on the right. Type what you want in natural language (e.g. "undo the last commit but keep changes"), GPT suggests the exact `git` commands, and you can **Approve & Run** or **Rephrase**. Approved commands execute in a log window showing output. Repository context (current branch, status) is automatically sent to GPT for accurate suggestions.
- **Folder picker** — open any Git repository on your machine. Last opened repo is remembered between sessions.
- **Keyboard shortcuts** — `Ctrl+O` (open), `Ctrl+Enter` (commit), `Ctrl+Shift+P` (push), `Ctrl+Shift+L` (pull), `Ctrl+Shift+F` (fetch), `Ctrl+Shift+A` (toggle AI panel), `F5` (refresh), `Ctrl+Q` (quit).

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
        ├── file_status.py          # Staged / unstaged / untracked files
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
