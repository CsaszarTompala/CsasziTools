@echo off
echo ============================================
echo  Building Remote Desktop Connector EXE
echo ============================================
echo.

REM Check if PyInstaller is installed
python -c "import PyInstaller" 2>nul
if errorlevel 1 (
    echo PyInstaller not found. Installing...
    pip install pyinstaller
    echo.
)

REM Install project dependencies
pip install -r "%~dp0requirements.txt"
echo.

echo Building executable...
echo.

REM Clean previous build artifacts
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0dist" rmdir /s /q "%~dp0dist"
if exist "%~dp0RemoteDesktopConnector.spec" del /q "%~dp0RemoteDesktopConnector.spec"

REM Build the executable
cd /d "%~dp0"
pyinstaller --noconfirm --clean --onefile --windowed ^
    --name "RemoteDesktopConnector" ^
    --add-data "logo_RD.png;." ^
    --add-data "RemoteDesktopConnector_logo.png;." ^
    --paths "%~dp0" ^
    --hidden-import ui --hidden-import logic --hidden-import data ^
    main.py

echo.
if exist "%~dp0dist\RemoteDesktopConnector.exe" (
    echo ============================================
    echo  Build successful!
    echo  Executable: dist\RemoteDesktopConnector.exe
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
if exist "%~dp0RemoteDesktopConnector.spec" del /q "%~dp0RemoteDesktopConnector.spec"
echo Done.
echo.
pause
