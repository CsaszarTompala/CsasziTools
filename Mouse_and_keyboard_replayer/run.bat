@echo off
cd /d "%~dp0"
echo Running Mouse_and_keyboard_replayer.py...
echo.

python Mouse_and_keyboard_replayer.py

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Python script failed with exit code %errorlevel%
    echo.
    pause
) else (
    echo.
    echo Script completed successfully.
    echo Closing window...
    timeout /t 2 /nobreak >nul
)