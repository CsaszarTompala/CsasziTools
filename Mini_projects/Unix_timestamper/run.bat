@echo off
cd /d "%~dp0"
echo Running unix_timestamper.py...
echo.

python unix_timestamper.py

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