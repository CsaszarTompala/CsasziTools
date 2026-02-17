@echo off
echo Installing required packages...
pip install -r "%~dp0requirements.txt"
echo.
echo Starting Jenkins Auto-Starter...
python "%~dp0jenkins_auto_starter.py"
pause