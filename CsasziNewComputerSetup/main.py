"""
CsasziNewComputerSetup — One-shot work environment bootstrapper for Windows.

Installs the apps I always use (via winget) and drops my preferred config
files into the right place so a fresh machine ends up looking like home.

Usage:
    python main.py                  # install + configure everything
    python main.py --list           # show what's registered
    python main.py --app doublecmd  # only act on selected app(s); repeatable
    python main.py --no-install     # skip winget, only deploy configs
    python main.py --no-config      # only install, skip config deployment
    python main.py --dry-run        # print actions, change nothing
    python main.py --force-config   # overwrite existing config files

Add new apps by appending an Application(...) entry to APPS below.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

if sys.platform == "win32":
    import ctypes
    import winreg
else:
    ctypes = None  # type: ignore[assignment]
    winreg = None  # type: ignore[assignment]

SCRIPT_DIR = Path(__file__).resolve().parent
CONFIGS_DIR = SCRIPT_DIR / "configs"

# Windows consoles often default to a legacy code page (cp1250 etc.) that
# can't encode arrows or box-drawing characters. Force UTF-8 on stdout/stderr
# so output is readable everywhere.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    except Exception:
        pass


# ──────────────────────────────────────────────────────────────────────────────
# Pretty printing
# ──────────────────────────────────────────────────────────────────────────────

def _supports_color() -> bool:
    return sys.stdout.isatty() and os.environ.get("NO_COLOR") is None


_COLOR = _supports_color()


def _c(code: str, text: str) -> str:
    return f"\033[{code}m{text}\033[0m" if _COLOR else text


def info(msg: str) -> None:
    print(_c("36", "[i] ") + msg)


def ok(msg: str) -> None:
    print(_c("32", "[+] ") + msg)


def warn(msg: str) -> None:
    print(_c("33", "[!] ") + msg)


def err(msg: str) -> None:
    print(_c("31", "[x] ") + msg)


def section(title: str) -> None:
    bar = "─" * max(4, 60 - len(title))
    print()
    print(_c("1;35", f"── {title} {bar}"))


# ──────────────────────────────────────────────────────────────────────────────
# Path helpers
# ──────────────────────────────────────────────────────────────────────────────

def expand(path: str) -> Path:
    """Expand %ENV% and ~ in a path string."""
    return Path(os.path.expandvars(os.path.expanduser(path)))


# ──────────────────────────────────────────────────────────────────────────────
# winget wrapper
# ──────────────────────────────────────────────────────────────────────────────

def _run(cmd: list[str], dry_run: bool, capture: bool = False) -> subprocess.CompletedProcess | None:
    info("$ " + " ".join(cmd))
    if dry_run:
        return None
    return subprocess.run(cmd, capture_output=capture, text=True, check=False)


def winget_available() -> bool:
    return shutil.which("winget") is not None


def winget_installed(package_id: str) -> bool:
    """Best-effort: ask winget whether a package is already installed."""
    if not winget_available():
        return False
    res = subprocess.run(
        ["winget", "list", "--id", package_id, "--exact",
         "--accept-source-agreements", "--disable-interactivity"],
        capture_output=True, text=True, check=False,
        encoding="utf-8", errors="replace",
    )
    if res.returncode != 0:
        return False
    return package_id.lower() in (res.stdout or "").lower()


def winget_install(package_id: str, dry_run: bool) -> bool:
    cmd = [
        "winget", "install", "--id", package_id, "--exact",
        "--silent",
        "--accept-package-agreements", "--accept-source-agreements",
        "--disable-interactivity",
    ]
    res = _run(cmd, dry_run)
    if dry_run:
        return True
    if res is None:
        return False
    if res.returncode == 0:
        return True
    err(f"winget install failed for {package_id} (exit {res.returncode})")
    return False


# ──────────────────────────────────────────────────────────────────────────────
# Config deployment
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class ConfigCopy:
    """Copy a file or directory from configs/ into a destination on disk."""

    source: str            # relative to configs/
    destination: str       # may contain %APPDATA%, ~ etc.
    description: str = ""

    def deploy(self, dry_run: bool, force: bool) -> bool:
        src = CONFIGS_DIR / self.source
        dst = expand(self.destination)
        if not src.exists():
            err(f"config source missing: {src}")
            return False

        if src.is_dir():
            return self._deploy_dir(src, dst, dry_run, force)
        return self._deploy_file(src, dst, dry_run, force)

    def _deploy_file(self, src: Path, dst: Path, dry_run: bool, force: bool) -> bool:
        if dst.exists() and not force:
            warn(f"skip (exists, use --force-config to overwrite): {dst}")
            return True
        info(f"copy file → {dst}")
        if dry_run:
            return True
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return True

    def _deploy_dir(self, src: Path, dst: Path, dry_run: bool, force: bool) -> bool:
        info(f"copy dir  → {dst}")
        if dry_run:
            for f in src.rglob("*"):
                if f.is_file():
                    rel = f.relative_to(src)
                    target = dst / rel
                    marker = "overwrite" if target.exists() else "create"
                    if target.exists() and not force:
                        marker = "skip (exists)"
                    print(f"      {marker:<14} {target}")
            return True
        dst.mkdir(parents=True, exist_ok=True)
        for f in src.rglob("*"):
            rel = f.relative_to(src)
            target = dst / rel
            if f.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            if target.exists() and not force:
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, target)
        return True


# ──────────────────────────────────────────────────────────────────────────────
# Admin / elevation helpers
# ──────────────────────────────────────────────────────────────────────────────

def is_admin() -> bool:
    if sys.platform != "win32" or ctypes is None:
        return False
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def relaunch_as_admin(extra_args: list[str]) -> int:
    """Re-launch the current Python script with UAC elevation."""
    if sys.platform != "win32" or ctypes is None:
        err("admin elevation only available on Windows")
        return 1
    params = " ".join(f'"{a}"' for a in [str(SCRIPT_DIR / "main.py"), *extra_args])
    info("requesting admin elevation via UAC ...")
    rc = ctypes.windll.shell32.ShellExecuteW(
        None, "runas", sys.executable, params, None, 1
    )
    if rc <= 32:
        err(f"elevation failed (ShellExecuteW returned {rc})")
        return 1
    return 0


# ──────────────────────────────────────────────────────────────────────────────
# Custom-action: keyboard layout installer
# ──────────────────────────────────────────────────────────────────────────────

# Custom Y/Z swap layout assets (see configs/keyboard-yz-swap/README.md)
KBD_YZ_SOURCE = CONFIGS_DIR / "keyboard-yz-swap"
KBD_YZ_KLID = "a0000409"
KBD_YZ_SUBSTITUTE = "d0010409"
KBD_YZ_DLL_NAME = "Layout01.dll"
KBD_YZ_REG_VALUES = {
    "Layout Text": "amerikai - Custom",
    "Layout File": KBD_YZ_DLL_NAME,
    "Layout Id": "00c0",
    "Layout Product Code": "{90CA46C3-0B30-474B-AF90-9E46FFBDE32D}",
    "Layout Display Name": r"@%SystemRoot%\system32\Layout01.dll,-1000",
    "Custom Language Name": "English (United States)",
    "Custom Language Display Name": r"@%SystemRoot%\system32\Layout01.dll,-1100",
}


def install_keyboard_yz_swap(dry_run: bool) -> bool:
    """Install the custom Y/Z-swapped US layout system-wide + as the user default."""
    if sys.platform != "win32":
        err("keyboard layout install only supported on Windows")
        return False

    src_x64 = KBD_YZ_SOURCE / "Layout01.x64.dll"
    src_x86 = KBD_YZ_SOURCE / "Layout01.x86.dll"
    if not src_x64.exists() or not src_x86.exists():
        err(f"missing DLL assets in {KBD_YZ_SOURCE}")
        return False

    dst_x64 = Path(os.environ.get("WINDIR", r"C:\Windows")) / "System32" / KBD_YZ_DLL_NAME
    dst_x86 = Path(os.environ.get("WINDIR", r"C:\Windows")) / "SysWOW64" / KBD_YZ_DLL_NAME

    info(f"copy {src_x64.name} → {dst_x64}")
    info(f"copy {src_x86.name} → {dst_x86}")
    if not dry_run:
        if not is_admin():
            err("administrator rights required to write System32/SysWOW64 and HKLM. "
                "Re-run from an elevated shell or pass --elevate.")
            return False
        try:
            shutil.copy2(src_x64, dst_x64)
            shutil.copy2(src_x86, dst_x86)
        except PermissionError as exc:
            err(f"cannot write DLL: {exc}")
            return False

    # HKLM machine-wide layout registration.
    hklm_path = rf"SYSTEM\CurrentControlSet\Control\Keyboard Layouts\{KBD_YZ_KLID}"
    info(rf"reg HKLM\{hklm_path} (7 values)")
    if not dry_run:
        try:
            with winreg.CreateKey(winreg.HKEY_LOCAL_MACHINE, hklm_path) as k:
                for name, value in KBD_YZ_REG_VALUES.items():
                    typ = winreg.REG_EXPAND_SZ if "%" in value else winreg.REG_SZ
                    winreg.SetValueEx(k, name, 0, typ, value)
        except PermissionError as exc:
            err(f"cannot write HKLM key: {exc}")
            return False

    # HKCU per-user input method: add as Preload and set substitute.
    info(rf"reg HKCU\Keyboard Layout\Substitutes\{KBD_YZ_SUBSTITUTE} = {KBD_YZ_KLID}")
    info(rf"reg HKCU\Keyboard Layout\Preload (ensure {KBD_YZ_SUBSTITUTE} present)")
    if not dry_run:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, r"Keyboard Layout\Substitutes") as k:
            winreg.SetValueEx(k, KBD_YZ_SUBSTITUTE, 0, winreg.REG_SZ, KBD_YZ_KLID)
        _ensure_preload_entry(KBD_YZ_SUBSTITUTE)

    ok("custom Y/Z-swap layout installed (sign out / reboot for it to take effect)")
    return True


# ──────────────────────────────────────────────────────────────────────────────
# Custom-action: VS Code extension installer
# ──────────────────────────────────────────────────────────────────────────────

VSCODE_EXTENSIONS_FILE = CONFIGS_DIR / "vscode" / "extensions.txt"


def _find_code_cli() -> str | None:
    """Locate the `code` / `code.cmd` CLI launcher."""
    for candidate in ("code.cmd", "code"):
        path = shutil.which(candidate)
        if path:
            return path
    fallback = Path(r"C:\Program Files\Microsoft VS Code\bin\code.cmd")
    if fallback.exists():
        return str(fallback)
    return None


# Extensions that must be installed BEFORE other extensions in the same
# family — otherwise VS Code prompts to install the missing parent runtime
# (e.g. installing ms-python.debugpy before ms-python.python triggers the
# "install Python?" prompt). Order matters within each list.
VSCODE_EXTENSION_PRIORITY: list[str] = [
    "ms-python.python",            # parent for debugpy / pylance / python-envs
    "ms-vscode.cpptools",          # parent for cpptools-extension-pack / themes
    "ms-toolsai.jupyter",          # parent for jupyter-keymap / renderers / cell-tags / slideshow
]


def _order_extensions(extensions: list[str]) -> list[str]:
    """Hoist parent extensions to the front so dependents don't prompt."""
    seen: set[str] = set()
    ordered: list[str] = []
    for ext in VSCODE_EXTENSION_PRIORITY:
        if ext in extensions and ext not in seen:
            ordered.append(ext)
            seen.add(ext)
    for ext in extensions:
        if ext not in seen:
            ordered.append(ext)
            seen.add(ext)
    return ordered


def install_vscode_extensions(dry_run: bool) -> bool:
    """Install every extension listed in configs/vscode/extensions.txt."""
    if not VSCODE_EXTENSIONS_FILE.exists():
        warn(f"no extensions list at {VSCODE_EXTENSIONS_FILE}; skipping")
        return True

    extensions = [
        line.strip()
        for line in VSCODE_EXTENSIONS_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    if not extensions:
        info("extensions list is empty; nothing to install")
        return True

    extensions = _order_extensions(extensions)

    code_cli = _find_code_cli()
    if not code_cli:
        err("`code` CLI not found in PATH. Open a new shell after installing VS Code "
            "(winget adds it to PATH) and re-run with --app vscode.")
        return False

    info(f"installing {len(extensions)} VS Code extension(s) via {code_cli}")
    failed: list[str] = []
    for ext in extensions:
        info(f"  + {ext}")
        if dry_run:
            continue
        res = subprocess.run(
            [code_cli, "--install-extension", ext, "--force"],
            capture_output=True, text=True, check=False,
            encoding="utf-8", errors="replace",
        )
        if res.returncode != 0:
            err(f"    failed: {res.stderr.strip() or res.stdout.strip()}")
            failed.append(ext)
    if failed:
        err(f"{len(failed)} extension(s) failed: {', '.join(failed)}")
        return False
    ok(f"all {len(extensions)} VS Code extension(s) installed")
    return True


# ──────────────────────────────────────────────────────────────────────────────
# Custom-action: Windows language / locale
# ──────────────────────────────────────────────────────────────────────────────

WINLANG_CONFIG = CONFIGS_DIR / "windows-language" / "language-settings.json"


def _ps_quote(s: str) -> str:
    """Quote a string as a PowerShell single-quoted literal."""
    return "'" + s.replace("'", "''") + "'"


def _ps_string_array(values: list[str]) -> str:
    return "@(" + ",".join(_ps_quote(v) for v in values) + ")"


def _build_winlang_script(cfg: dict, want_admin_steps: bool) -> str:
    """Render the PowerShell script that applies the language settings."""
    lines: list[str] = ["$ErrorActionPreference = 'Stop'"]

    # User-scope: language list with input methods.
    lines.append("$list = New-WinUserLanguageList -Language 'en-US'")
    lines.append("$list.Clear()")
    for entry in cfg["user_language_list"]:
        tag = _ps_quote(entry["tag"])
        lines.append(f"$lang = New-WinUserLanguageList -Language {tag}")
        lines.append(f"$lang[0].InputMethodTips.Clear()")
        for tip in entry.get("input_method_tips", []):
            lines.append(f"$lang[0].InputMethodTips.Add({_ps_quote(tip)})")
        lines.append("$list.Add($lang[0])")
    lines.append("Set-WinUserLanguageList -LanguageList $list -Force")

    # Default input method.
    if cfg.get("default_input_method"):
        lines.append(
            f"Set-WinDefaultInputMethodOverride -InputTip {_ps_quote(cfg['default_input_method'])}"
        )

    # Format culture.
    if cfg.get("format_culture"):
        lines.append(f"Set-Culture -CultureInfo {_ps_quote(cfg['format_culture'])}")

    # Home location (GeoId).
    if cfg.get("home_location_geo_id") is not None:
        lines.append(f"Set-WinHomeLocation -GeoId {int(cfg['home_location_geo_id'])}")

    # Admin-only steps.
    if want_admin_steps:
        if cfg.get("system_locale"):
            lines.append(
                f"Set-WinSystemLocale -SystemLocale {_ps_quote(cfg['system_locale'])}"
            )
        if cfg.get("copy_to_welcome_and_default_user"):
            lines.append(
                "if (Get-Command Copy-UserInternationalSettingsToSystem -ErrorAction SilentlyContinue) "
                "{ Copy-UserInternationalSettingsToSystem -WelcomeScreen $true -NewUser $true }"
            )

    lines.append("Write-Host '[+] Windows language settings applied'")
    return "\n".join(lines)


def apply_windows_language(dry_run: bool) -> bool:
    if sys.platform != "win32":
        err("windows-language only supported on Windows")
        return False
    if not WINLANG_CONFIG.exists():
        err(f"missing config: {WINLANG_CONFIG}")
        return False

    cfg = json.loads(WINLANG_CONFIG.read_text(encoding="utf-8"))
    has_admin = is_admin()
    want_admin_steps = has_admin
    if not has_admin and (cfg.get("system_locale") or cfg.get("copy_to_welcome_and_default_user")):
        warn("not elevated — skipping system-locale + copy-to-welcome-screen steps "
             "(re-run with --elevate to apply them)")

    script = _build_winlang_script(cfg, want_admin_steps)
    info("PowerShell script to be executed:")
    for line in script.splitlines():
        print(f"      {line}")

    if dry_run:
        return True

    res = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
        capture_output=True, text=True, check=False,
        encoding="utf-8", errors="replace",
    )
    if res.stdout:
        for line in res.stdout.splitlines():
            print(f"      {line}")
    if res.returncode != 0:
        err(f"powershell exited {res.returncode}: {res.stderr.strip()}")
        return False
    if want_admin_steps and cfg.get("system_locale"):
        warn("system locale changed — reboot required for it to take effect")
    return True


# ──────────────────────────────────────────────────────────────────────────────
# Custom-action: Claude Code (corporate gateway)
# ──────────────────────────────────────────────────────────────────────────────

CLAUDE_CODE_DIR = CONFIGS_DIR / "claude-code"
CLAUDE_CODE_SCRIPT = CLAUDE_CODE_DIR / "install_claude_code.ps1"
CLAUDE_CODE_CREDS = CLAUDE_CODE_DIR / "credentials.json"


def install_claude_code(dry_run: bool) -> bool:
    if sys.platform != "win32":
        err("claude-code installer only supported on Windows")
        return False
    if not CLAUDE_CODE_SCRIPT.exists():
        err(f"missing installer script: {CLAUDE_CODE_SCRIPT}")
        return False
    if not CLAUDE_CODE_CREDS.exists():
        err(f"missing credentials: {CLAUDE_CODE_CREDS}")
        return False

    creds = json.loads(CLAUDE_CODE_CREDS.read_text(encoding="utf-8"))
    token = creds.get("auth_token", "").strip()
    uid = creds.get("uid", "").strip()
    if not token or not uid:
        err("credentials.json must contain non-empty 'auth_token' and 'uid'")
        return False

    info(f"running {CLAUDE_CODE_SCRIPT.name} for UID {uid}")
    info(f"  token: {token[:8]}... (hidden)")
    if dry_run:
        info("  [dry-run] would override Read-Host with token + UID and dot-source installer")
        return True

    # Read-Host ignores redirected stdin (always opens the console), so piping
    # values doesn't work. Instead, generate a wrapper script that defines a
    # function named Read-Host in its scope — function lookup wins over cmdlet
    # lookup — that returns queued answers in order, then dot-sources the
    # corporate installer so the override is in scope while it runs.
    wrapper = SCRIPT_DIR / ".claude_code_wrapper.ps1"
    target = str(CLAUDE_CODE_SCRIPT).replace("'", "''")
    wrapper.write_text(
        "$ErrorActionPreference = 'Stop'\n"
        f"$script:_answers = New-Object System.Collections.Queue\n"
        f"$script:_answers.Enqueue({_ps_quote(token)}) | Out-Null\n"
        f"$script:_answers.Enqueue({_ps_quote(uid)}) | Out-Null\n"
        "function Read-Host {\n"
        "    param([Parameter(ValueFromRemainingArguments=$true)] $args)\n"
        "    $asSecure = $false\n"
        "    foreach ($a in $args) { if ($a -ieq '-AsSecureString') { $asSecure = $true } }\n"
        "    if ($script:_answers.Count -eq 0) { return '' }\n"
        "    $value = [string]$script:_answers.Dequeue()\n"
        "    if ($asSecure) { return (ConvertTo-SecureString -String $value -AsPlainText -Force) }\n"
        "    return $value\n"
        "}\n"
        f". '{target}'\n",
        encoding="utf-8",
    )

    cmd = [
        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", str(wrapper),
    ]
    try:
        res = subprocess.run(
            cmd, text=True, check=False,
            encoding="utf-8", errors="replace",
        )
    finally:
        try:
            wrapper.unlink()
        except OSError:
            pass

    if res.returncode != 0:
        err(f"install_claude_code.ps1 exited {res.returncode}")
        return False
    ok("Claude Code installed and configured against the corporate gateway")
    info("open a new shell, then run: claudecode")
    return True


def _ensure_preload_entry(klid: str) -> None:
    """Make sure the given KLID appears in HKCU\\Keyboard Layout\\Preload."""
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, r"Keyboard Layout\Preload") as k:
        existing: dict[str, str] = {}
        i = 0
        while True:
            try:
                name, value, _ = winreg.EnumValue(k, i)
            except OSError:
                break
            existing[name] = value
            i += 1
        if klid in existing.values():
            return
        # Append at the next free numeric slot.
        idx = 1
        while str(idx) in existing:
            idx += 1
        winreg.SetValueEx(k, str(idx), 0, winreg.REG_SZ, klid)


# ──────────────────────────────────────────────────────────────────────────────
# Application registry
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class Application:
    key: str                              # CLI handle, e.g. "doublecmd"
    name: str                             # human-friendly name
    winget_id: str | None = None          # None → no winget step
    configs: list[ConfigCopy] = field(default_factory=list)
    post_install: Callable[[bool], bool] | None = None
    requires_admin: bool = False
    notes: str = ""

    def install(self, dry_run: bool) -> bool:
        if not self.winget_id:
            info(f"{self.name}: no installer registered, skipping install step")
            return True
        if not winget_available():
            err("winget is not available on this system — cannot install apps")
            return False
        if winget_installed(self.winget_id) and not dry_run:
            ok(f"{self.name} already installed ({self.winget_id})")
            return True
        info(f"installing {self.name} via winget ({self.winget_id})")
        return winget_install(self.winget_id, dry_run)

    def deploy_configs(self, dry_run: bool, force: bool) -> bool:
        if not self.configs:
            return True
        all_ok = True
        for c in self.configs:
            label = c.description or c.source
            info(f"{self.name}: deploying config — {label}")
            if not c.deploy(dry_run, force):
                all_ok = False
        return all_ok


APPS: list[Application] = [
    Application(
        key="doublecmd",
        name="Double Commander",
        winget_id="alexx2000.DoubleCommander",
        configs=[
            ConfigCopy(
                source="doublecmd",
                destination="%APPDATA%/doublecmd",
                description="user config (toolbars, shortcuts, tabs, colors)",
            ),
        ],
        notes="Open-source two-pane file manager (Total Commander style).",
    ),
    Application(
        key="git",
        name="Git",
        winget_id="Git.Git",
        notes="Distributed VCS — required by Git Extensions.",
    ),
    Application(
        key="git-extensions",
        name="Git Extensions",
        winget_id="GitExtensionsTeam.GitExtensions",
        notes="GUI for Git on Windows.",
    ),
    Application(
        key="vscode",
        name="Visual Studio Code",
        winget_id="Microsoft.VisualStudioCode",
        configs=[
            ConfigCopy(
                source="vscode/User",
                destination="%APPDATA%/Code/User",
                description="user settings, keybindings, snippets, prompts, mcp.json",
            ),
        ],
        post_install=install_vscode_extensions,
        notes="Editor + my user settings/keybindings/prompts + ~37 extensions "
              "(see configs/vscode/extensions.txt).",
    ),
    Application(
        key="kbd-yz-swap",
        name="Keyboard layout: US Y/Z swap",
        winget_id=None,
        post_install=install_keyboard_yz_swap,
        requires_admin=True,
        notes="Custom 'amerikai - Custom' layout (Y and Z swapped). "
              "Copies Layout01.dll to System32+SysWOW64 and writes HKLM/HKCU registry. "
              "Sign out / reboot after install for it to take effect.",
    ),
    Application(
        key="claude-code",
        name="Claude Code (corp. gateway)",
        winget_id=None,
        post_install=install_claude_code,
        notes="Runs the corporate install_claude_code.ps1 with the bundled UID + JWT "
              "token from configs/claude-code/credentials.json. Installs Claude Code "
              "via winget, creates claudecode.bat with all env vars, sets up the "
              "extended status line. Open a new shell and run `claudecode` after.",
    ),
    Application(
        key="windows-language",
        name="Windows language / locale",
        winget_id=None,
        post_install=apply_windows_language,
        requires_admin=True,
        notes="Applies user language list (en-US + hu), default input method "
              "(custom Y/Z layout), format culture, home location and "
              "system locale from configs/windows-language/language-settings.json. "
              "Run kbd-yz-swap first. Reboot after — system locale change needs it.",
    ),
]


# ──────────────────────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Bootstrap a fresh Windows machine with my favorite tools and configs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("--app", action="append", metavar="KEY",
                   help="Limit to one or more app keys (repeatable). Default: all.")
    p.add_argument("--list", action="store_true", help="List registered apps and exit.")
    p.add_argument("--no-install", action="store_true", help="Skip winget install steps.")
    p.add_argument("--no-config", action="store_true", help="Skip config deployment.")
    p.add_argument("--force-config", action="store_true",
                   help="Overwrite existing config files instead of skipping them.")
    p.add_argument("--dry-run", action="store_true", help="Print actions without executing.")
    p.add_argument("--elevate", action="store_true",
                   help="Re-launch with UAC elevation if any selected app needs admin rights.")
    p.add_argument("--no-elevate", action="store_true",
                   help="Refuse auto-elevation; fail apps that require admin instead.")
    return p.parse_args()


def list_apps() -> None:
    print("Registered applications:\n")
    for a in APPS:
        wg = a.winget_id or "—"
        admin = "  [admin]" if a.requires_admin else ""
        print(f"  {a.key:<16} {a.name:<32} winget: {wg}{admin}")
        if a.configs:
            for c in a.configs:
                print(f"      config: {c.source!r:<24} → {c.destination}")
        if a.post_install:
            print(f"      custom action: {a.post_install.__name__}()")
        if a.notes:
            print(f"      {a.notes}")
        print()


def select_apps(keys: list[str] | None) -> list[Application]:
    if not keys:
        return APPS
    by_key = {a.key: a for a in APPS}
    chosen: list[Application] = []
    unknown: list[str] = []
    for k in keys:
        if k in by_key:
            chosen.append(by_key[k])
        else:
            unknown.append(k)
    if unknown:
        err(f"unknown app key(s): {', '.join(unknown)}")
        info("known: " + ", ".join(by_key))
        sys.exit(2)
    return chosen


def main() -> int:
    args = parse_args()

    if args.list:
        list_apps()
        return 0

    if sys.platform != "win32":
        warn(f"this tool targets Windows; current platform: {sys.platform}")

    if not args.no_install and not winget_available():
        err("winget not found in PATH. Install App Installer from the Microsoft Store, "
            "or rerun with --no-install to only deploy configs.")
        return 1

    apps = select_apps(args.app)

    needs_admin = any(a.requires_admin for a in apps)
    if needs_admin and not is_admin() and not args.dry_run:
        warn("one or more selected apps require administrator rights:")
        for a in apps:
            if a.requires_admin:
                print(f"      - {a.name} ({a.key})")
        if args.elevate and not args.no_elevate:
            return relaunch_as_admin(sys.argv[1:])
        if not args.no_elevate:
            info("re-run with --elevate to auto-prompt UAC, or start an elevated shell.")
        # Continue anyway; admin-required steps will fail and be reported.

    section(f"Plan: {len(apps)} app(s)" + (" [DRY RUN]" if args.dry_run else ""))
    for a in apps:
        tag = "  [admin]" if a.requires_admin else ""
        print(f"  • {a.name} ({a.key}){tag}")

    failures: list[str] = []
    for a in apps:
        section(a.name)
        if not args.no_install:
            if not a.install(args.dry_run):
                failures.append(f"install:{a.key}")
                continue
        if not args.no_config:
            if not a.deploy_configs(args.dry_run, args.force_config):
                failures.append(f"config:{a.key}")
        if a.post_install:
            try:
                if a.post_install(args.dry_run) is False:
                    failures.append(f"post:{a.key}")
            except Exception as exc:  # noqa: BLE001
                err(f"post-install hook failed for {a.key}: {exc}")
                failures.append(f"post:{a.key}")

    section("Done")
    if failures:
        err("failures: " + ", ".join(failures))
        return 1
    ok("all steps completed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
