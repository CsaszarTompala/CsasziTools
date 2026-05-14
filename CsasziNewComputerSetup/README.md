# CsasziNewComputerSetup

One-shot bootstrapper that turns a freshly imaged Windows machine into "my"
work environment: installs the apps I always use (via `winget`) and drops my
preferred config files into the locations they belong.

## First-time setup

```
setup.bat        # creates .venv, upgrades pip, installs requirements.txt
```

Then use `run.bat` to launch the tool — it will use the venv automatically.

## Usage

```
run.bat                         # install + configure everything (system Python or venv)
run.bat --elevate               # auto-prompt UAC for admin steps (keyboard layout)
run.bat --dry-run               # preview without changing anything

python main.py                  # same, if you'd rather invoke directly
python main.py --list           # show registered apps
python main.py --app doublecmd  # only act on selected apps (repeatable)
python main.py --no-install     # skip winget, only deploy configs
python main.py --no-config      # only install apps
python main.py --dry-run        # print actions, change nothing
python main.py --force-config   # overwrite existing config files
```

Or just double-click `run.bat`.

## Currently installs

| Key             | App                              | winget ID                         | Admin |
|-----------------|----------------------------------|-----------------------------------|-------|
| `doublecmd`     | Double Commander                 | `Alexx2000.DoubleCommander`       |       |
| `git`           | Git                              | `Git.Git`                         |       |
| `git-extensions`| Git Extensions                   | `GitExtensionsTeam.GitExtensions` |       |
| `vscode`        | Visual Studio Code + ext + cfg   | `Microsoft.VisualStudioCode`      |       |
| `claude-code`   | Claude Code (corp. gateway)      | — (custom action)                 |       |
| `kbd-yz-swap`   | Custom US layout (Y/Z swapped)   | — (custom action)                 | ✓     |
| `windows-language` | Windows language / locale     | — (custom action)                 | ✓     |

After install, `doublecmd` deploys the snapshot in `configs/doublecmd/` to
`%APPDATA%\doublecmd\`. Existing files are skipped by default — pass
`--force-config` to overwrite.

`vscode` installs VS Code via winget, then deploys `configs/vscode/User/`
(settings, keybindings, snippets, prompts, `mcp.json`,
`chatLanguageModels.json`) into `%APPDATA%\Code\User\`, then runs
`code --install-extension <id>` for every line in
`configs/vscode/extensions.txt`. To refresh the extensions list from your
current machine: `code --list-extensions > configs\vscode\extensions.txt`.

`claude-code` runs the corporate `install_claude_code.ps1` (bundled in
`configs/claude-code/`) and feeds it the UID + JWT token from
`configs/claude-code/credentials.json` over stdin so it runs unattended.
The script installs Node.js + Git + Claude Code via winget, writes
`C:\tools\claudecode\claudecode.bat` with all corporate env vars
(`ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, `NO_PROXY`, ...), and sets
up the extended status line. Open a fresh shell afterwards and run
`claudecode`. Refresh `credentials.json` whenever the JWT rotates.

`windows-language` reads `configs/windows-language/language-settings.json`
and applies it through PowerShell: user language list (with input methods),
default input method, format culture, home location, and — when elevated —
the system locale and copy-to-welcome-screen / new-user-defaults. Reboot
after applying for the system locale change to take effect. Edit the JSON
to adjust without touching code.

`kbd-yz-swap` copies `Layout01.dll` to both `System32` and `SysWOW64`,
writes the machine-wide layout registration under
`HKLM\SYSTEM\CurrentControlSet\Control\Keyboard Layouts\a0000409`, and
sets the layout as the user's first preload via
`HKCU\Keyboard Layout\Substitutes` + `Preload`. Requires admin —
either start an elevated shell, or pass `--elevate` to trigger UAC.
Sign out / reboot after install for Windows to pick up the new layout.

## Adding a new app

Append an entry to `APPS` in `main.py`. Each app has:

- `key` — short CLI handle (`--app KEY`)
- `name` — pretty name shown in output
- `winget_id` — package ID (`winget search <name>` to find it); set to `None`
  if no install step is needed
- `configs` — list of `ConfigCopy(source=<rel path under configs/>,
  destination=<path with %APPDATA% etc.>)`
- optional `post_install` hook for anything winget+copy can't express

Then drop the matching files into `configs/<your-folder>/` and commit.

## Refreshing the bundled config

Snapshots in `configs/` are point-in-time copies. To re-sync from your
current machine, just copy the files back over them, e.g.:

```
xcopy /E /Y "%APPDATA%\doublecmd" configs\doublecmd
```

and commit the diff.

## Requirements

- Windows 10/11 with `winget` (App Installer) available in PATH
- Python 3.10+ (no third-party packages)
