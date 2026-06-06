# ShutdownApp

A small PyQt6 GUI that shuts down the computer after a countdown.

## Features

- Hour and minute selectors for the shutdown delay.
- **Start** begins the countdown; the remaining time is shown as `HH:MM:SS`.
- **Stop** cancels the countdown at any point.
- When the countdown reaches zero, the app runs `shutdown.exe /s /t 0` to turn the computer off.

## Setup

```powershell
pip install -r requirements.txt
```

## Run

```powershell
python main.py
```

> Note: `shutdown.exe` is Windows-only.
