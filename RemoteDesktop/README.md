# Remote Desktop Connector

A Python utility for managing and connecting to remote desktop sessions via Windows Remote Desktop Protocol (RDP).

## Overview

Remote Desktop Connector provides a convenient command-line and GUI interface for storing credentials and connecting to remote computers using Windows RDP. It includes both a CLI script and a visual GUI application for easy access to frequently used connections.

## Features

- 🖥️ Connect to remote computers via RDP
- 🔐 Secure credential storage using Windows Credential Manager
- 💾 Pre-configured connection shortcuts
- 🎨 Visual GUI for easy connection management
- ⌨️ Command-line interface for automation
- 🔌 Multiple connection profiles stored
- 🚀 Quick launch shortcuts for frequently used connections

## Installation

### Requirements

- Windows OS (uses `mstsc` and `cmdkey`)
- Python 3.6+
- pynput (optional, for GUI features)

### Setup

1. Clone or download the project

2. Install dependencies (optional, for GUI):
```bash
pip install -r requirements.txt
```

3. No additional installation needed - uses Windows built-in RDP tools

## Usage

### Command-Line Interface

#### Connect to Remote Computer

```bash
python remote_desktop.py <computer_name> <username> <password>
```

**Examples:**

```bash
# Connect via IP address
python remote_desktop.py 192.168.1.100 admin MyPassword123

# Connect via hostname
python remote_desktop.py RemotePC uig40210 MyPassword123

# Connect via FQDN
python remote_desktop.py computer.domain.com user MyPassword123
```

#### Pre-configured Batch Files

Quick connection shortcuts (already configured):

```bash
# Connect to specific computers
connect_to_Luke.bat
connect_to_Chewie.bat
connect_to_aqlde70w.bat
connect_to_uuddcrzw.bat
```

These batch files contain pre-configured credentials and connection details.

### Graphical User Interface

Start the GUI application:

```bash
python main.py
```

Or use the batch file:

```bash
run_gui.bat
```

**GUI Features:**
- **Branded header** with RemoteDesktopConnector logo
- Visual connection manager with scrollable list
- Quick-launch buttons for saved connections
- Add / edit / delete connection profiles
- **4 colour themes** — Dracula (default), Monokai, Nord, Solarized Light — selectable from *View → Theme* (remembered across sessions)

## How It Works

The application handles remote desktop connections in these steps:

1. **Credential Storage**: Credentials are stored securely using Windows Credential Manager via `cmdkey`
2. **Connection Launch**: Launches `mstsc` (Remote Desktop Connection) with target computer
3. **Authentication**: Windows uses stored credentials for automatic login
4. **Session Management**: Session runs in standard Windows RDP window

### Behind the Scenes

```
User Input
    ↓
Parse arguments (computer, username, password)
    ↓
Store credentials using cmdkey
    ↓
Launch mstsc with remote computer name
    ↓
Windows RDP connects automatically
```

## Pre-configured Connections

The following batch files are included for quick access:

| Batch File | Target | Description |
|------------|--------|-------------|
| connect_to_Luke.bat | Luke (hostname) | Quick connection to Luke |
| connect_to_Chewie.bat | Chewie (hostname) | Quick connection to Chewie |
| connect_to_aqlde70w.bat | aqlde70w | Quick connection to aqlde70w |
| connect_to_uuddcrzw.bat | uuddcrzw | Quick connection to uuddcrzw |

To use any of these, simply double-click or run from command line.

## Configuration

### Adding New Connection

1. Create a new .bat file (or edit existing):
```batch
@echo off
python remote_desktop.py <TARGET_COMPUTER> <USERNAME> <PASSWORD>
pause
```

2. Save with descriptive name like `connect_to_MyComputer.bat`

3. Double-click to connect

### Modifying Existing Connections

Edit the corresponding .bat file and update:
- Computer name/IP
- Username
- Password (or leave empty to prompt)

## Project Structure

```
RemoteDesktop/
├── main.py                          # GUI entry point
├── remote_desktop.py                # CLI interface (standalone)
├── logo_RD.png                      # Window icon
├── RemoteDesktopConnector_logo.png   # Header logo (full text)
├── settings.json                    # Auto-generated user preferences
├── ui/                              # Frontend — GUI layer
│   ├── __init__.py
│   ├── main_window.py               # Main window + View menu
│   └── dialogs.py                   # Add/Edit connection dialog
├── logic/                           # Middle layer — business logic
│   ├── __init__.py
│   ├── rdp.py                       # Credential storage & mstsc launch
│   └── themes.py                    # Colour theme definitions & persistence
├── data/                            # Backend — data persistence
│   ├── __init__.py
│   └── connections.py               # Connection load/save + defaults
├── requirements.txt                 # Python dependencies (Pillow)
├── run_gui.bat                      # GUI launcher
├── build_exe.bat                    # PyInstaller build script
├── RemoteDesktopConnector.spec      # PyInstaller spec file
└── Connection Shortcuts:
    ├── connect_to_Luke.bat
    ├── connect_to_Chewie.bat
    ├── connect_to_aqlde70w.bat
    └── connect_to_uuddcrzw.bat
```

## Windows Credential Manager

Credentials are stored in Windows Credential Manager:

1. **View stored credentials:**
   - Control Panel → Credential Manager
   - Look for RDP entries

2. **Clear stored credentials:**
   ```bash
   cmdkey /delete:<computer_name>
   ```

3. **List all stored credentials:**
   ```bash
   cmdkey /list
   ```

## Security Considerations

⚠️ **Important Security Notes:**

- **Don't hardcode passwords in batch files** if the system is shared
- **Use alternative authentication** when possible:
  - Windows domain authentication (preferred)
  - Windows Smart Cards
  - Multi-factor authentication
- **Credential Manager** stores credentials securely using Windows encryption
- Consider using **network credentials** stored in Credential Manager instead
- Only grant RDP permissions to trusted users
- Disable unnecessary RDP sessions
- Keep Windows and RDP updated

## Building Executable

To create a standalone .exe file:

```bash
build_exe.bat
```

This generates executables in the `dist/` directory:
- `RemoteDesktop.exe` - CLI version
- `RemoteDesktopConnector.exe` - GUI version (if built)

## Troubleshooting

### Connection fails

1. **Verify computer is reachable:**
   ```bash
   ping <computer_name_or_ip>
   ```

2. **Check RDP is enabled:**
   - Remote settings on target computer
   - Windows Firewall allow RDP (port 3389)

3. **Verify credentials:**
   - Correct username format (DOMAIN\username or local\username)
   - Password has no special shell characters (escape if needed)

### Credential issues

1. **Clear old credentials:**
   ```bash
   cmdkey /delete:<computer_name>
   ```

2. **Re-add credentials:**
   ```bash
   python remote_desktop.py <computer> <user> <password>
   ```

3. **Manual credential entry:**
   Run mstsc directly and enter credentials manually

### mstsc not found

- Ensure Windows Remote Desktop is installed (Home/Pro/Enterprise editions)
- Windows Home Edition has limited RDP support
- Consider using Remote Desktop app from Microsoft Store

## Tips & Tricks

### Connect to localhost (for testing)
```bash
python remote_desktop.py localhost username password
```

### Use IP addresses instead of hostnames
```bash
python remote_desktop.py 192.168.1.100 user pass
```

### Domain authentication
```bash
python remote_desktop.py computer.domain.com DOMAIN\username password
```

### Batch multiple connections
Create a script that launches multiple connections sequentially

## Performance Notes

- First connection may take 5-10 seconds (credential processing)
- Subsequent connections are faster (cached credentials)
- GUI version requires GUI environment (Windows only)
- CLI version can run from Server edition

## License

Personal utility project - use freely in your projects.

## Author Notes

Remote Desktop Connector is useful for:
- System administrators managing multiple servers
- Remote work connecting to office computers
- Development team accessing shared development machines
- Quick access to frequently used remote systems
- Lab environments with multiple test machines
- Automated administrative tasks via RDP

## Related Tools

- Remote Desktop Connection (mstsc) - Windows built-in
- Credential Manager - Windows credential storage
- Windows Remote Assistance
- Third-party RDP clients (Citrix, TeamViewer, etc.)
