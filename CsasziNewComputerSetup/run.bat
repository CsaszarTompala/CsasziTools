@echo off
REM Launch CsasziNewComputerSetup. Uses .venv if present (run setup.bat first),
REM otherwise falls back to the system Python.
setlocal
set "SCRIPT_DIR=%~dp0"
set "VENV_PY=%SCRIPT_DIR%.venv\Scripts\python.exe"

if exist "%VENV_PY%" (
    "%VENV_PY%" "%SCRIPT_DIR%main.py" %*
) else (
    echo [!] No virtual environment found. Run setup.bat first for the recommended setup.
    echo [i] Falling back to system Python ...
    python "%SCRIPT_DIR%main.py" %*
)
endlocal
