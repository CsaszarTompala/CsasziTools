# Mouse and Keyboard Replayer

Records and replays mouse + keyboard interactions, including cursor movement and drag operations.

## Features

- Records mouse movement paths for realistic playback
- Records mouse button press/release (left, right, middle)
- Records mouse wheel scrolling
- Records keyboard key press/release events
- Supports drag-and-drop replay (press + move + release)
- Saves and loads recordings as JSON files
- Replays in a loop until you stop it
- Uses `End` key to stop recording or replay

## Requirements

- Python 3.6+
- `pynput==1.7.6`

Install dependencies:

```bash
pip install -r requirements.txt
```

## Run

```bash
python Mouse_and_keyboard_replayer.py
```

Or on Windows:

```bash
run.bat
```

## Usage

1. Start the app.
2. Choose `0` to record.
3. Perform your actions (including click-drag movement).
4. Press `End` to stop recording.
5. Save recording to a `.json` file.
6. Choose `1` to load and replay.
7. Press `End` during replay to stop.

## Recording format (high-level)

Saved events include:

- Mouse `move`
- Mouse `press` / `release`
- Mouse `scroll`
- Keyboard `press` / `release`

Each event stores a relative `timestamp` for timing-accurate replay.

## Notes

- Movement is lightly throttled to keep files manageable while preserving drag paths.
- Existing old recordings (without move events) still replay normally.

## Files

- `Mouse_and_keyboard_replayer.py` - main application
- `requirements.txt` - Python dependency list
- `run.bat` - Windows launcher
- `recordings/` - saved recordings
