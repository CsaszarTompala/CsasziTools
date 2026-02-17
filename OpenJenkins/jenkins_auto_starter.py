"""
Jenkins Node Auto-Starter Script.

This script monitors and automatically starts the Jenkins agent if it's not running.
It runs a daily check at 2 AM local time and provides a 60-second countdown window
before starting the Jenkins node, allowing the user to cancel if needed.

Usage:
    python jenkins_auto_starter.py
"""

import subprocess
import time
import threading
import tkinter as tk
from tkinter import ttk
from datetime import datetime, timedelta
import schedule
import sys
import json
import os


def load_config():
    """
    Load configuration from config.json file.

    Returns:
        dict: Configuration dictionary with required settings.

    Raises:
        FileNotFoundError: If config.json is not found.
        json.JSONDecodeError: If config.json is not valid JSON.
    """
    config_path = os.path.join(os.path.dirname(__file__), "config.json")
    try:
        with open(config_path, "r") as config_file:
            return json.load(config_file)
    except FileNotFoundError:
        print(f"Error: config.json not found at {config_path}")
        raise
    except json.JSONDecodeError:
        print(f"Error: config.json is not valid JSON")
        raise


# Load configuration
try:
    _config = load_config()
    BATCH_FILE_PATH = _config.get("batch_file_path")
    JAVA_PROCESS_NAME = _config.get("java_process_name", "java.exe")
    JENKINS_AGENT_IDENTIFIER = _config.get("jenkins_agent_identifier", "agent.jar")
    COUNTDOWN_SECONDS = _config.get("countdown_seconds", 60)
except Exception as e:
    print(f"Failed to load configuration: {e}")
    sys.exit(1)


def is_jenkins_agent_running() -> bool:
    """
    Check if the Jenkins agent is currently running.

    Arguments:
        None

    Returns:
        bool: True if Jenkins agent process is running, False otherwise.
    """
    try:
        # Use WMIC to get detailed command line info for java processes
        cmd = f'wmic process where "name=\'{JAVA_PROCESS_NAME}\'" get commandline'
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        # Check if agent.jar is in any of the running java processes
        return JENKINS_AGENT_IDENTIFIER in result.stdout
    except Exception as e:
        print(f"Error checking process: {e}")
        return False


def start_jenkins_agent() -> bool:
    """
    Start the Jenkins agent by running the batch file.

    Arguments:
        None

    Returns:
        bool: True if the batch file was started successfully, False otherwise.
    """
    try:
        # Start the batch file in a new window
        subprocess.Popen(
            f'start cmd /k "{BATCH_FILE_PATH}"',
            shell=True
        )
        print(f"[{datetime.now()}] Jenkins agent started successfully.")
        return True
    except Exception as e:
        print(f"[{datetime.now()}] Error starting Jenkins agent: {e}")
        return False


class CountdownWindow:
    """Countdown window with cancel option for Jenkins auto-start."""

    def __init__(self, seconds: int) -> None:
        """
        Initialize the countdown window.

        Arguments:
            seconds (int): Number of seconds to countdown from.

        Returns:
            None
        """
        self.seconds = seconds
        self.cancelled = False
        self.root = None

    def show(self) -> bool:
        """
        Display the countdown window and wait for completion or cancellation.

        Arguments:
            None

        Returns:
            bool: True if countdown completed (start Jenkins), False if cancelled.
        """
        self.root = tk.Tk()
        self.root.title("Jenkins Node Auto-Start")
        self.root.geometry("400x200")
        self.root.attributes("-topmost", True)
        self.root.resizable(False, False)
        # Center the window on screen
        self.root.update_idletasks()
        x = (self.root.winfo_screenwidth() // 2) - (400 // 2)
        y = (self.root.winfo_screenheight() // 2) - (200 // 2)
        self.root.geometry(f"400x200+{x}+{y}")
        # Main frame
        frame = ttk.Frame(self.root, padding="20")
        frame.pack(fill=tk.BOTH, expand=True)
        # Message label
        message_label = ttk.Label(
            frame,
            text="Jenkins node is not running!\nStarting automatically in:",
            font=("Segoe UI", 11),
            justify=tk.CENTER
        )
        message_label.pack(pady=(0, 10))
        # Countdown label
        self.countdown_label = ttk.Label(
            frame,
            text=str(self.seconds),
            font=("Segoe UI", 36, "bold"),
            foreground="red"
        )
        self.countdown_label.pack(pady=(0, 10))
        # Progress bar
        self.progress = ttk.Progressbar(
            frame,
            length=300,
            mode="determinate",
            maximum=self.seconds
        )
        self.progress["value"] = self.seconds
        self.progress.pack(pady=(0, 15))
        # Cancel button
        cancel_btn = ttk.Button(
            frame,
            text="Cancel",
            command=self._cancel
        )
        cancel_btn.pack()
        # Handle window close
        self.root.protocol("WM_DELETE_WINDOW", self._cancel)
        # Start countdown
        self._update_countdown()
        self.root.mainloop()
        return not self.cancelled

    def _update_countdown(self) -> None:
        """
        Update the countdown display.

        Arguments:
            None

        Returns:
            None
        """
        if self.cancelled:
            return
        if self.seconds <= 0:
            self.root.destroy()
            return
        self.countdown_label.config(text=str(self.seconds))
        self.progress["value"] = self.seconds
        self.seconds -= 1
        self.root.after(1000, self._update_countdown)

    def _cancel(self) -> None:
        """
        Handle cancel button click or window close.

        Arguments:
            None

        Returns:
            None
        """
        self.cancelled = True
        self.root.destroy()


def check_and_start_jenkins() -> None:
    """
    Check if Jenkins agent is running and start it if not.

    Arguments:
        None

    Returns:
        None
    """
    print(f"[{datetime.now()}] Checking if Jenkins agent is running...")
    if is_jenkins_agent_running():
        print(f"[{datetime.now()}] Jenkins agent is already running.")
        return
    print(f"[{datetime.now()}] Jenkins agent is NOT running. Showing countdown window...")
    # Show countdown window
    countdown = CountdownWindow(COUNTDOWN_SECONDS)
    should_start = countdown.show()
    if should_start:
        print(f"[{datetime.now()}] Countdown completed. Starting Jenkins agent...")
        start_jenkins_agent()
    else:
        print(f"[{datetime.now()}] User cancelled Jenkins auto-start.")


def run_scheduler() -> None:
    """
    Run the scheduler loop to check at 2 AM daily.

    Arguments:
        None

    Returns:
        None
    """
    # Schedule the job for 2 AM every day
    schedule.every().day.at("02:00").do(check_and_start_jenkins)
    print(f"[{datetime.now()}] Jenkins Auto-Starter initialized.")
    print(f"Scheduled to check daily at 02:00 AM.")
    print(f"Batch file: {BATCH_FILE_PATH}")
    print("-" * 50)
    while True:
        schedule.run_pending()
        time.sleep(30)  # Check every 30 seconds


def main() -> int:
    """
    Main entry point for the Jenkins auto-starter script.

    Arguments:
        None

    Returns:
        int: Exit code (0 for success).
    """
    print("=" * 50)
    print("Jenkins Node Auto-Starter")
    print("=" * 50)
    # Option to run check immediately for testing
    if len(sys.argv) > 1 and sys.argv[1] == "--now":
        print("Running immediate check (--now flag detected)...")
        check_and_start_jenkins()
        return 0
    # Run the scheduler
    try:
        run_scheduler()
    except KeyboardInterrupt:
        print(f"\n[{datetime.now()}] Shutting down Jenkins Auto-Starter...")
    return 0


if __name__ == "__main__":
    sys.exit(main())
