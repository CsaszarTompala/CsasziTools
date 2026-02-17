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

echo Building executable...
echo.

REM Clean previous build artifacts to ensure icon is applied
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0dist" rmdir /s /q "%~dp0dist"
if exist "%~dp0RemoteDesktopConnector.spec" del /q "%~dp0RemoteDesktopConnector.spec"

REM Build the executable
REM --onefile: Create a single executable file
REM --windowed: No console window (GUI application)
REM --name: Name of the output executable
REM --icon: Optional icon (place rdp_icon.ico in this folder)
REM --add-data: Include the connections.json if it exists

set ICON_PARAM=
if exist "%~dp0rdp_icon.ico" (
    set ICON_PARAM=--icon "%~dp0rdp_icon.ico"
)

pyinstaller --noconfirm --clean --onefile --windowed --name "RemoteDesktopConnector" %ICON_PARAM% "%~dp0remote_desktop_gui.py"

echo.
if exist "dist\RemoteDesktopConnector.exe" (
    echo ============================================
    echo  Build successful!
    echo  Executable: dist\RemoteDesktopConnector.exe
    echo ============================================
    echo.
    echo You can copy the exe to any location.
    echo The connections.json file will be created next to the exe on first run.
) else (
    echo ============================================
    echo  Build failed! Check errors above.
    echo ============================================
)

echo.
pause
