"""
CsasziFlash — Command-line Lauterbach flash tool for ARS620DP28 variants.

Reads config.json to locate flash binaries and the Lauterbach environment,
lets the user pick a variant and which entities to flash, writes the
Trace32 configuration file, and runs the Lauterbach autoflashing batch.

Usage:
    python main.py              Interactive menu
    python main.py flash        Jump straight to variant selection & flash
"""

import glob
import json
import os
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(SCRIPT_DIR, "config.json")

# ─── Colour helpers (ANSI) ──────────────────────────────────────────────────

RESET = "\033[0m"
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"


def _ok(msg: str) -> None:
    print(f"{GREEN}[OK]{RESET} {msg}")


def _warn(msg: str) -> None:
    print(f"{YELLOW}[WARN]{RESET} {msg}")


def _err(msg: str) -> None:
    print(f"{RED}[ERROR]{RESET} {msg}")


def _info(msg: str) -> None:
    print(f"{CYAN}[INFO]{RESET} {msg}")


def _header(msg: str) -> None:
    width = 60
    print(f"\n{BOLD}{CYAN}{'═' * width}{RESET}")
    print(f"{BOLD}{CYAN}  {msg}{RESET}")
    print(f"{BOLD}{CYAN}{'═' * width}{RESET}\n")


# ─── Config ─────────────────────────────────────────────────────────────────

def load_config() -> dict:
    """Load and validate config.json."""
    if not os.path.isfile(CONFIG_PATH):
        _err(f"Config file not found: {CONFIG_PATH}")
        _info("Copy config.json.example and fill in your paths.")
        sys.exit(1)

    with open(CONFIG_PATH, "r", encoding="utf-8") as fh:
        cfg = json.load(fh)

    required = ["input_base_path", "debugger_config_path", "debugger_start_cmd", "flash_files"]
    for key in required:
        if key not in cfg:
            _err(f"Missing required config key: '{key}'")
            sys.exit(1)

    return cfg


# ─── Variant discovery ──────────────────────────────────────────────────────

KNOWN_VARIANTS = [
    "MRR581",
    "SR_R581", "SR_L581",
    "CR_FR581", "CR_FL581", "CR_BR581", "CR_BL581",
    "BR_R581", "BR_L581",
]


def _resolve_appl_glob(base_path: str, appl_pattern: str) -> list[str]:
    """Return list of supermot files matching the glob pattern."""
    full_pattern = os.path.join(base_path, appl_pattern)
    return sorted(glob.glob(full_pattern))


def detect_variants(base_path: str, appl_pattern: str) -> list[dict]:
    """Detect available variants by scanning for supermot files."""
    matches = _resolve_appl_glob(base_path, appl_pattern)
    variants: list[dict] = []
    for path in matches:
        fname = os.path.basename(path).lower()
        # Try to match known variant by checking the file name pattern
        # e.g. ars620dp28f_supermot.mot -> MRR581 (default / f-variant)
        identified = None
        for v in KNOWN_VARIANTS:
            tag = v.lower().replace("581", "").replace("_", "")
            if tag in fname:
                identified = v
                break
        if identified is None:
            # Default: if the filename contains the letter before _supermot
            # e.g. ars620dp28f -> variant letter 'f' (typically MRR581)
            identified = "MRR581 (default)"
        variants.append({"name": identified, "supermot_path": path})
    return variants


# ─── Flash config writer (replicates DP_support logic) ──────────────────────

TRACE32_SLOTS = 12


def write_flash_config(file_paths: list[str], config_path: str) -> None:
    """Write the Trace32 auto-programming config file.

    Fills up to 12 downloadable-entity slots.  Enabled slots get the real
    file path; unused slots are written as disabled with "not specified".
    """
    padded = file_paths + ["not specified"] * (TRACE32_SLOTS - len(file_paths))

    with open(config_path, "w", encoding="utf-8") as fh:
        fh.write("CLEARSYMBOLS=y\n")
        for idx, path in enumerate(padded):
            slot = idx + 1
            flag = "n" if "not specified" in path else "y"
            fh.writelines([
                f"// Downloadable entity no {slot}\n",
                f"EXE_NO_{slot}_EN={flag}\n",
                "not specified\n",
                f"EXE_NO_{slot}_DOWNLOAD_EN={flag}\n",
                f"EXE_NO_{slot}_DOWNLOAD_OTHER_EN={flag}\n",
                f"{path}\n",
                "0x00000000\n",
                "not specified\n",
            ])
        fh.write("INHIBIT_USER_OK_QUERY=y\n")


# ─── Flash execution ────────────────────────────────────────────────────────

def run_lauterbach(cmd_path: str, pass_verdict: str, max_attempts: int = 2) -> bool:
    """Execute the Lauterbach autoflashing batch, retrying on failure."""
    for attempt in range(1, max_attempts + 1):
        _info(f"Lauterbach attempt {attempt}/{max_attempts} …")
        result = subprocess.run(
            cmd_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if pass_verdict in result.stdout:
            return True
        if attempt < max_attempts:
            _warn(f"Attempt {attempt} did not pass – retrying in 5 s …")
            time.sleep(5)
    return False


def flash_files(cfg: dict, files_to_flash: list[str]) -> bool:
    """Flash a list of .mot files one-by-one through Lauterbach."""
    debugger_cfg = cfg["debugger_config_path"]
    debugger_cmd = cfg["debugger_start_cmd"]
    max_attempts = cfg.get("max_flash_attempts", 2)
    pass_verdict = cfg.get("pass_verdict", '<Verdict:> "PASS"')

    all_ok = True
    for file_path in files_to_flash:
        fname = os.path.basename(file_path)
        _header(f"Flashing: {fname}")

        if not os.path.isfile(file_path):
            _err(f"File not found: {file_path}")
            all_ok = False
            continue

        # Write Trace32 config for this single entity
        write_flash_config([file_path], debugger_cfg)

        success = run_lauterbach(debugger_cmd, pass_verdict, max_attempts)
        if success:
            _ok(f"{fname} flashed successfully.")
        else:
            _err(f"{fname} flash FAILED after {max_attempts} attempts!")
            all_ok = False

    return all_ok


# ─── Resolve file list ──────────────────────────────────────────────────────

def resolve_flash_file(base_path: str, rel_path: str) -> str | None:
    """Resolve a single flash file path (supports glob wildcards)."""
    full = os.path.join(base_path, rel_path)
    if "*" in rel_path or "?" in rel_path:
        matches = glob.glob(full)
        return matches[0] if matches else None
    return full if os.path.isfile(full) else None


def build_flash_list(cfg: dict, variant_supermot: str | None = None, include_nvm_clear: bool = False) -> list[str]:
    """Build the ordered list of absolute file paths to flash."""
    base = cfg["input_base_path"]
    flash_cfg = cfg["flash_files"]
    files: list[str] = []

    for key in ("fbl", "hsm", "sbl", "seckeys"):
        rel = flash_cfg.get(key)
        if not rel:
            continue
        resolved = resolve_flash_file(base, rel)
        if resolved:
            files.append(resolved)
        else:
            _warn(f"Flash file not found for '{key}': {os.path.join(base, rel)}")

    # Application (supermot) — use variant-specific if given
    if variant_supermot:
        files.append(variant_supermot)
    else:
        appl_rel = flash_cfg.get("appl", "")
        resolved = resolve_flash_file(base, appl_rel)
        if resolved:
            files.append(resolved)
        else:
            _warn(f"Application supermot not found: {os.path.join(base, appl_rel)}")

    if include_nvm_clear:
        nvm_rel = cfg.get("nvm_clear", "")
        if nvm_rel:
            nvm_path = os.path.join(base, nvm_rel)
            if os.path.isfile(nvm_path):
                files.append(nvm_path)
            else:
                _warn(f"NVM clear file not found: {nvm_path}")

    return files


# ─── Interactive menu ────────────────────────────────────────────────────────

def choose_variant(cfg: dict) -> str | None:
    """Let the user pick a variant. Returns the supermot path."""
    base = cfg["input_base_path"]
    appl_pattern = cfg["flash_files"].get("appl", "")

    variants = detect_variants(base, appl_pattern)

    if not variants:
        # Fallback: try to resolve the single glob
        resolved = resolve_flash_file(base, appl_pattern)
        if resolved:
            _info(f"Single application file found: {os.path.basename(resolved)}")
            return resolved
        _err("No application (supermot) files found in input directory.")
        _info(f"Searched: {os.path.join(base, appl_pattern)}")
        return None

    if len(variants) == 1:
        v = variants[0]
        _info(f"Single variant detected: {v['name']}")
        _info(f"  File: {os.path.basename(v['supermot_path'])}")
        return v["supermot_path"]

    print(f"\n{BOLD}Available variants:{RESET}\n")
    for idx, v in enumerate(variants, 1):
        print(f"  {CYAN}{idx}{RESET}) {v['name']}  —  {os.path.basename(v['supermot_path'])}")
    print(f"  {CYAN}0{RESET}) Cancel\n")

    while True:
        choice = input(f"Select variant [1-{len(variants)}, 0 to cancel]: ").strip()
        if choice == "0":
            return None
        if choice.isdigit() and 1 <= int(choice) <= len(variants):
            selected = variants[int(choice) - 1]
            _ok(f"Selected: {selected['name']}")
            return selected["supermot_path"]
        _warn("Invalid selection, try again.")


def confirm_flash(files: list[str]) -> bool:
    """Show the user what will be flashed and ask for confirmation."""
    print(f"\n{BOLD}The following files will be flashed (in order):{RESET}\n")
    for idx, f in enumerate(files, 1):
        print(f"  {idx}. {os.path.basename(f)}")
        print(f"     {f}")
    print()
    answer = input("Proceed with flashing? [y/N]: ").strip().lower()
    return answer in ("y", "yes")


def ask_nvm_clear() -> bool:
    """Ask the user whether to include NVM clear."""
    answer = input("Include NVM/HSM clear? [y/N]: ").strip().lower()
    return answer in ("y", "yes")


def cmd_flash(cfg: dict) -> int:
    """Full interactive flash workflow."""
    _header("CsasziFlash — Lauterbach Autoflash")

    # 1. Validate paths
    base = cfg["input_base_path"]
    if not os.path.isdir(base):
        _err(f"Input base path does not exist: {base}")
        return 1

    debugger_cmd = cfg.get("debugger_start_cmd", "")
    if not os.path.isfile(debugger_cmd):
        _err(f"Lauterbach CMD not found: {debugger_cmd}")
        return 1

    debugger_cfg = cfg.get("debugger_config_path", "")
    debugger_cfg_dir = os.path.dirname(debugger_cfg)
    if not os.path.isdir(debugger_cfg_dir):
        _err(f"Debugger config directory does not exist: {debugger_cfg_dir}")
        return 1

    # 2. Pick variant
    supermot = choose_variant(cfg)
    if supermot is None:
        _info("Flash cancelled.")
        return 0

    # 3. NVM clear?
    nvm = ask_nvm_clear()

    # 4. Build file list
    files = build_flash_list(cfg, variant_supermot=supermot, include_nvm_clear=nvm)
    if not files:
        _err("No files to flash.")
        return 1

    # 5. Confirm
    if not confirm_flash(files):
        _info("Flash cancelled by user.")
        return 0

    # 6. Flash
    success = flash_files(cfg, files)

    if success:
        _header("ALL FLASH OPERATIONS PASSED")
    else:
        _header("SOME FLASH OPERATIONS FAILED")
        return 1

    return 0


def cmd_status(cfg: dict) -> int:
    """Show current config and detected files."""
    _header("CsasziFlash — Status")

    base = cfg["input_base_path"]
    print(f"  Input path     : {base}")
    print(f"  Debugger config: {cfg.get('debugger_config_path', 'N/A')}")
    print(f"  Debugger CMD   : {cfg.get('debugger_start_cmd', 'N/A')}")
    print()

    flash_cfg = cfg.get("flash_files", {})
    for key in ("fbl", "hsm", "sbl", "seckeys", "appl"):
        rel = flash_cfg.get(key, "")
        resolved = resolve_flash_file(base, rel)
        status = f"{GREEN}FOUND{RESET}" if resolved else f"{RED}MISSING{RESET}"
        display = resolved if resolved else os.path.join(base, rel)
        print(f"  {key:8s}: [{status}] {display}")

    nvm_rel = cfg.get("nvm_clear", "")
    if nvm_rel:
        nvm_full = os.path.join(base, nvm_rel)
        status = f"{GREEN}FOUND{RESET}" if os.path.isfile(nvm_full) else f"{RED}MISSING{RESET}"
        print(f"  {'nvm':8s}: [{status}] {nvm_full}")

    print()

    # Variants
    appl_pattern = flash_cfg.get("appl", "")
    variants = detect_variants(base, appl_pattern)
    if variants:
        print(f"{BOLD}Detected variants:{RESET}")
        for v in variants:
            print(f"  • {v['name']}  —  {os.path.basename(v['supermot_path'])}")
    else:
        _warn("No application (supermot) files found.")

    return 0


# ─── Main ────────────────────────────────────────────────────────────────────

USAGE = f"""{BOLD}CsasziFlash{RESET} — Lauterbach ECU flash tool

{BOLD}Commands:{RESET}
  flash     Select variant and flash via Lauterbach
  status    Show config and detected flash files
  help      Show this message
"""


def main() -> int:
    cfg = load_config()

    args = sys.argv[1:]
    cmd = args[0].lower() if args else ""

    if cmd == "flash":
        return cmd_flash(cfg)
    elif cmd == "status":
        return cmd_status(cfg)
    elif cmd in ("help", "-h", "--help"):
        print(USAGE)
        return 0
    elif cmd == "":
        # Interactive: show menu
        print(USAGE)
        while True:
            choice = input(f"{BOLD}>{RESET} ").strip().lower()
            if choice == "flash":
                return cmd_flash(cfg)
            elif choice == "status":
                return cmd_status(cfg)
            elif choice in ("help", "h", "?"):
                print(USAGE)
            elif choice in ("exit", "quit", "q"):
                return 0
            else:
                _warn(f"Unknown command: '{choice}'. Type 'help' for options.")
    else:
        _warn(f"Unknown command: '{cmd}'")
        print(USAGE)
        return 1


if __name__ == "__main__":
    sys.exit(main())
