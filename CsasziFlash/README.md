# CsasziFlash

Command-line tool for flashing ARS620DP28 ECU variants via Lauterbach (Trace32).

## Overview

CsasziFlash automates the Lauterbach auto-programming workflow:

1. Discovers available application variants (supermot files) in the input directory
2. Lets you choose which variant to flash
3. Writes the Trace32 configuration file for each flash entity
4. Runs the Lauterbach autoflashing batch for each file (FBL → HSM → SBL → SECKEYS → APPL)
5. Reports pass/fail per entity

## Prerequisites

- **Lauterbach / Trace32** environment installed (DevEnv path)
- Unzipped flash artifacts in the input directory (same structure as Jenkins pipeline output)
- Python 3.10+

## Setup

1. Open `config.json` and fill in the paths for your machine:

| Key | Description |
|-----|-------------|
| `input_base_path` | Root of the unzipped artifacts (contains `fbl/`, `hsm/`, `sbl/`, `seckeys/`, `appl/` folders) |
| `debugger_config_path` | Path to the Trace32 auto-programming config `.txt` file |
| `debugger_start_cmd` | Path to the Lauterbach autoflashing `.cmd` batch |
| `flash_files.fbl` | Relative path to FBL `.mot` inside `input_base_path` |
| `flash_files.hsm` | Relative path to HSM `.mot` |
| `flash_files.sbl` | Relative path to SBL `.mot` |
| `flash_files.seckeys` | Relative path to SECKEYS `.mot` |
| `flash_files.appl` | Relative glob pattern for application supermot (e.g. `appl\out\*_supermot.mot`) |
| `nvm_clear` | Relative path to NvMHsmClear `.mot` (optional) |
| `max_flash_attempts` | Retries per file (default `2`) |
| `pass_verdict` | String to look for in Lauterbach stdout to confirm success |

2. Run `run.bat` or:

```
python main.py flash
```

## Commands

| Command | Description |
|---------|-------------|
| `flash` | Interactive variant selection and flash |
| `status` | Show current config, detected files and variants |
| `help` | Show usage |

## Input directory structure

```
input_base_path/
├── appl/out/
│   ├── ars620dp28f_supermot.mot   (variant-specific)
│   └── NvMHsmClear.mot
├── fbl/out/
│   └── ARS620DP28_FBL.mot
├── hsm/out/
│   └── ARS620DP28_HSM.mot
├── sbl/out/
│   └── RAD6XX_SBL_B2.mot
└── seckeys/out/SECKEYS/B2/
    └── SECKEYS.mot
```

## Supported variants

MRR581, SR_R581, SR_L581, CR_FR581, CR_FL581, CR_BR581, CR_BL581, BR_R581, BR_L581

The tool auto-detects available variants by scanning for `*_supermot.mot` files in `appl/out/`.

## Flashing flow

For each `.mot` file (in order: FBL, HSM, SBL, SECKEYS, APPL, optionally NvMHsmClear):

1. Write the file path into the Trace32 auto-programming config
2. Execute the Lauterbach autoflashing CMD
3. Check stdout for `<Verdict:> "PASS"`
4. Retry once on failure (configurable)
