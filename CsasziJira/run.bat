@echo off
echo Installing required packages...
pip install -r "%~dp0requirements.txt"
echo.
echo Starting CsasziJira...
python "%~dp0main.py"
pause
