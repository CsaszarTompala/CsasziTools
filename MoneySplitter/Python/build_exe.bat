@echo off
echo ============================================
echo  Money Splitter — Build Executable
echo ============================================
echo.

echo Installing required packages...
pip install -r "%~dp0requirements.txt"
pip install pyinstaller
echo.

echo Building executable...
cd /d "%~dp0"
pyinstaller --onefile --windowed --name MoneySplitter --clean main.py
echo.

echo ============================================
echo  Build complete!
echo  Executable: dist\MoneySplitter.exe
echo ============================================
pause
