@echo off
REM Build Unix Timestamper Executable
REM This script creates a standalone .exe file using PyInstaller

echo ================================================================================
echo Building Unix Timestamper Executable...
echo ================================================================================
echo.

REM Check if PyInstaller is installed
C:\LegacyApp\Python311\python.exe -m pip show pyinstaller >nul 2>&1
if %errorlevel% neq 0 (
    echo PyInstaller is not installed. Installing now...
    echo Trying public PyPI repository...
    C:\LegacyApp\Python311\python.exe -m pip install --index-url https://pypi.org/simple pyinstaller
    if %errorlevel% neq 0 (
        echo.
        echo Failed to install PyInstaller!
        echo.
        echo Please install PyInstaller manually using:
        echo   pip install pyinstaller
        echo.
        echo Or download from: https://pypi.org/project/pyinstaller/
        pause
        exit /b 1
    )
)

REM Clean previous builds
if exist "build" rmdir /s /q "build"
if exist "dist" rmdir /s /q "dist"
if exist "Unix_timestamper.spec" del "Unix_timestamper.spec"

REM Build the executable
echo.
echo Building executable...
C:\LegacyApp\Python311\python.exe -m PyInstaller --onefile --console --name "Unix_Timestamper" --paths="..\.." --hidden-import=Common.Menu.terminal_menu Unix_timestamper.py

if %errorlevel% neq 0 (
    echo.
    echo ================================================================================
    echo Build FAILED!
    echo ================================================================================
    pause
    exit /b 1
)

echo.
echo ================================================================================
echo Build SUCCESSFUL!
echo ================================================================================
echo.
echo Executable location: %cd%\dist\Unix_Timestamper.exe
echo.
echo You can now run the standalone executable from the dist folder.
echo.
pause
