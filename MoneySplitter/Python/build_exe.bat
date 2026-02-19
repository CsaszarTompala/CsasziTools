@echo off
echo ============================================
echo  Money Splitter — Build Executable
echo ============================================
echo.

echo Installing required packages...
pip install -r "%~dp0requirements.txt"
pip install pyinstaller
echo.

REM Clean previous build artifacts
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0dist" rmdir /s /q "%~dp0dist"
if exist "%~dp0MoneySplitter.spec" del /q "%~dp0MoneySplitter.spec"

echo Building executable...
cd /d "%~dp0"
pyinstaller --noconfirm --clean --onefile --windowed ^
    --name MoneySplitter ^
    --icon "%~dp0logo_MS.png" ^
    --add-data "%~dp0logo_MS.png;." ^
    --add-data "%~dp0MoneySplitter_logo.png;." ^
    --paths "%~dp0" ^
    --hidden-import ui --hidden-import logic --hidden-import data ^
    main.py

echo.
if exist "%~dp0dist\MoneySplitter.exe" (
    echo ============================================
    echo  Build successful!
    echo  Executable: dist\MoneySplitter.exe
    echo ============================================
) else (
    echo ============================================
    echo  Build FAILED! Check errors above.
    echo ============================================
    pause
    exit /b 1
)

REM --- Cleanup: keep only dist\ with the exe inside ---
echo.
echo Cleaning up build artifacts...
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0MoneySplitter.spec" del /q "%~dp0MoneySplitter.spec"
echo Done.
echo.
pause
