@echo off
REM ---------------------------------------------------------------
REM CsasziNewComputerSetup — one-time environment setup
REM   * creates a .venv next to this script
REM   * upgrades pip
REM   * installs everything in requirements.txt
REM Run this once. After that, use run.bat to launch the tool.
REM ---------------------------------------------------------------
setlocal
set "SCRIPT_DIR=%~dp0"
set "VENV_DIR=%SCRIPT_DIR%.venv"

where python >nul 2>nul
if errorlevel 1 (
    echo [x] Python not found in PATH. Install Python 3.10+ first.
    exit /b 1
)

if exist "%VENV_DIR%\Scripts\python.exe" (
    echo [i] Reusing existing virtual environment at "%VENV_DIR%"
) else (
    echo [i] Creating virtual environment at "%VENV_DIR%"
    python -m venv "%VENV_DIR%" || (
        echo [x] Failed to create virtual environment.
        exit /b 1
    )
)

echo [i] Upgrading pip
"%VENV_DIR%\Scripts\python.exe" -m pip install --upgrade pip || exit /b 1

echo [i] Installing requirements
"%VENV_DIR%\Scripts\python.exe" -m pip install -r "%SCRIPT_DIR%requirements.txt" || exit /b 1

echo.
echo [+] Setup complete.
echo     Run the tool with:  run.bat
echo     Dry-run preview:    run.bat --dry-run
echo     Full bootstrap:     run.bat --elevate
endlocal
