"""
Mouse and Keyboard Replayer Application

This application allows users to record mouse clicks and keyboard keystrokes,
save them to a JSON file, and replay them in a loop until stopped.

Features:
- Records left, right, and middle mouse button clicks with coordinates
- Records mouse movement for realistic pointer paths and drag operations
- Records mouse wheel scroll events with direction and intensity
- Records all keyboard keystrokes
- Saves recordings to JSON files
- Replays recordings in a continuous loop
- Uses 'End' key to stop recording/replaying

Requirements:
- pynput library for input handling
- json library for data storage
- time library for timing operations
"""

import json
import time
import os
import sys
from typing import List, Dict, Any, Optional, Tuple
from threading import Event
from pynput import mouse, keyboard
from pynput.mouse import Button
from pynput.keyboard import Key

# Add parent directory to path to import Common modules
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from Common.Menu import TerminalMenu


class MouseKeyboardRecorder:
    """
    A class to handle recording and replaying of mouse and keyboard events.
    """
    
    def __init__(self) -> None:
        """Initialize the recorder with empty event list and control flags."""
        self.events: List[Dict[str, Any]] = []
        self.recording: bool = False
        self.replaying: bool = False
        self.stop_event: Event = Event()
        self.start_time: float = 0.0
        self.last_move_time: float = 0.0
        self.last_move_position: Optional[Tuple[int, int]] = None
        self.move_record_interval: float = 0.01
        self.move_min_distance: int = 1
    
    def reset_recording(self) -> None:
        """Reset the recording state and clear events."""
        self.events.clear()
        self.recording = False
        self.stop_event.clear()
        self.start_time = 0.0
        self.last_move_time = 0.0
        self.last_move_position = None

    def on_mouse_move(self, x: int, y: int) -> None:
        """
        Handle mouse movement events during recording.

        Arguments:
            x: Current mouse X coordinate
            y: Current mouse Y coordinate
        """
        if not self.recording:
            return

        current_time = time.time()
        elapsed = current_time - self.start_time

        if self.last_move_position is not None:
            last_x, last_y = self.last_move_position
            distance = abs(x - last_x) + abs(y - last_y)
            if distance < self.move_min_distance and (current_time - self.last_move_time) < self.move_record_interval:
                return

        event = {
            'type': 'mouse',
            'action': 'move',
            'x': x,
            'y': y,
            'timestamp': elapsed
        }
        self.events.append(event)
        self.last_move_time = current_time
        self.last_move_position = (x, y)
    
    def on_mouse_click(self, x: int, y: int, button: Button, pressed: bool) -> None:
        """
        Handle mouse click events during recording.
        
        Arguments:
            x: X coordinate of the mouse click
            y: Y coordinate of the mouse click
            button: Mouse button that was clicked
            pressed: Whether the button was pressed (True) or released (False)
        """
        if not self.recording:
            return
        
        current_time = time.time()
        event = {
            'type': 'mouse',
            'action': 'press' if pressed else 'release',
            'button': button.name,
            'x': x,
            'y': y,
            'timestamp': current_time - self.start_time
        }
        self.events.append(event)
        print(f"Mouse {event['action']}: {button.name} at ({x}, {y})")

    def on_mouse_scroll(self, x: int, y: int, dx: int, dy: int) -> None:
        """
        Handle mouse scroll events during recording.

        Arguments:
            x: X coordinate of the mouse when scrolling
            y: Y coordinate of the mouse when scrolling
            dx: Horizontal scroll delta
            dy: Vertical scroll delta
        """
        if not self.recording:
            return
        current_time = time.time()
        event = {
            'type': 'mouse',
            'action': 'scroll',
            'x': x,
            'y': y,
            'dx': dx,
            'dy': dy,
            'timestamp': current_time - self.start_time
        }
        self.events.append(event)
        print(f"Mouse scroll: dx={dx}, dy={dy} at ({x}, {y})")
    
    def on_key_press(self, key) -> Optional[bool]:
        """
        Handle key press events during recording.
        
        Arguments:
            key: The key that was pressed
            
        Returns:
            False if recording should stop (End key pressed), None otherwise
        """
        if not self.recording:
            return None
        
        # Check for End key to stop recording
        if key == Key.end:
            print("\nEnd key pressed. Stopping recording...")
            self.recording = False
            self.stop_event.set()
            return False
        
        current_time = time.time()
        key_name = self._get_key_name(key)
        
        event = {
            'type': 'keyboard',
            'action': 'press',
            'key': key_name,
            'timestamp': current_time - self.start_time
        }
        self.events.append(event)
        print(f"Key press: {key_name}")
        return None
    
    def on_key_release(self, key) -> Optional[bool]:
        """
        Handle key release events during recording.
        
        Arguments:
            key: The key that was released
            
        Returns:
            False if recording should stop (End key pressed), None otherwise
        """
        if not self.recording:
            return None
        
        # Check for End key to stop recording
        if key == Key.end:
            return None  # Already handled in on_key_press
        
        current_time = time.time()
        key_name = self._get_key_name(key)
        
        event = {
            'type': 'keyboard',
            'action': 'release',
            'key': key_name,
            'timestamp': current_time - self.start_time
        }
        self.events.append(event)
        print(f"Key release: {key_name}")
        return None
    
    def _get_key_name(self, key) -> str:
        """
        Get a string representation of the key.
        
        Arguments:
            key: The key object
            
        Returns:
            String representation of the key
        """
        try:
            return key.char if hasattr(key, 'char') and key.char is not None else key.name
        except AttributeError:
            return str(key)
    
    def start_recording(self) -> None:
        """Start recording mouse and keyboard events."""
        print("Starting recording in:")
        for i in range(3, 0, -1):
            print(f"{i}...")
            time.sleep(1)
        print("Recording started! Press 'End' key to stop recording.")
        
        self.reset_recording()
        self.recording = True
        self.start_time = time.time()
        
        # Start listeners
        mouse_listener = mouse.Listener(
            on_move=self.on_mouse_move,
            on_click=self.on_mouse_click,
            on_scroll=self.on_mouse_scroll
        )
        keyboard_listener = keyboard.Listener(
            on_press=self.on_key_press,
            on_release=self.on_key_release
        )
        
        mouse_listener.start()
        keyboard_listener.start()
        
        # Wait for stop signal
        self.stop_event.wait()
        
        # Stop listeners
        mouse_listener.stop()
        keyboard_listener.stop()
        
        print(f"Recording stopped. Recorded {len(self.events)} events.")
    
    def save_recording(self, filename: str) -> bool:
        """
        Save the recorded events to a JSON file in the recordings folder.
        
        Arguments:
            filename: Name of the JSON file to save (with or without .json extension)
            
        Returns:
            True if save was successful, False otherwise
        """
        try:
            # Ensure filename has .json extension
            if not filename.endswith('.json'):
                filename += '.json'
            
            # Get the directory of the current script
            script_dir = os.path.dirname(os.path.abspath(__file__))
            # Create recordings folder path relative to script directory
            recordings_dir = os.path.join(script_dir, 'recordings')
            
            # Create recordings directory if it doesn't exist
            if not os.path.exists(recordings_dir):
                os.makedirs(recordings_dir)
            
            # Create full path for the recording file
            full_path = os.path.join(recordings_dir, filename)
            
            with open(full_path, 'w') as f:
                json.dump(self.events, f, indent=2)
            print(f"Recording saved to {full_path}")
            return True
        except Exception as e:
            print(f"Error saving recording: {e}")
            return False
    
    def load_recording(self, filename: str) -> bool:
        """
        Load recorded events from a JSON file in the recordings folder.
        
        Arguments:
            filename: Name of the JSON file to load (with or without .json extension, can include path)
            
        Returns:
            True if load was successful, False otherwise
        """
        try:
            # If filename contains path separators, use it as is (but still check for .json extension)
            if os.path.sep in filename or '/' in filename:
                # For full paths, try with and without .json extension
                if not filename.endswith('.json'):
                    # Try with .json extension first
                    json_filename = filename + '.json'
                    if os.path.exists(json_filename):
                        full_path = json_filename
                    elif os.path.exists(filename):
                        full_path = filename
                    else:
                        print(f"File {filename} or {json_filename} does not exist.")
                        return False
                else:
                    full_path = filename
            else:
                # Otherwise, look in the recordings folder
                script_dir = os.path.dirname(os.path.abspath(__file__))
                recordings_dir = os.path.join(script_dir, 'recordings')
                
                # Try with .json extension first if not already present
                if not filename.endswith('.json'):
                    json_filename = filename + '.json'
                    json_full_path = os.path.join(recordings_dir, json_filename)
                    original_full_path = os.path.join(recordings_dir, filename)
                    
                    if os.path.exists(json_full_path):
                        full_path = json_full_path
                    elif os.path.exists(original_full_path):
                        full_path = original_full_path
                    else:
                        print(f"File {json_filename} or {filename} does not exist in recordings folder.")
                        return False
                else:
                    full_path = os.path.join(recordings_dir, filename)
            
            if not os.path.exists(full_path):
                print(f"File {full_path} does not exist.")
                return False
            
            with open(full_path, 'r') as f:
                self.events = json.load(f)
            print(f"Recording loaded from {full_path}. {len(self.events)} events loaded.")
            return True
        except Exception as e:
            print(f"Error loading recording: {e}")
            return False
    
    def list_available_recordings(self) -> List[str]:
        """
        List all available recording files in the recordings folder.
        
        Returns:
            List of recording filenames
        """
        try:
            script_dir = os.path.dirname(os.path.abspath(__file__))
            recordings_dir = os.path.join(script_dir, 'recordings')
            
            if not os.path.exists(recordings_dir):
                return []
            
            recordings = [f for f in os.listdir(recordings_dir) if f.endswith('.json')]
            return recordings
        except Exception as e:
            print(f"Error listing recordings: {e}")
            return []
    
    def replay_events(self) -> None:
        """Replay the loaded events in a continuous loop until End key is pressed."""
        if not self.events:
            print("No events to replay!")
            return
        
        print("Starting replay... Press 'End' key to stop.")
        print("Replay will start in 3 seconds...")
        time.sleep(3)
        
        self.replaying = True
        self.stop_event.clear()
        
        # Create controllers for playback
        mouse_controller = mouse.Controller()
        keyboard_controller = keyboard.Controller()
        
        # Set up listener for End key during replay
        def on_replay_key_press(key):
            if key == Key.end:
                print("\nEnd key pressed. Stopping replay...")
                self.replaying = False
                self.stop_event.set()
                return False
        
        replay_listener = keyboard.Listener(on_press=on_replay_key_press)
        replay_listener.start()
        
        loop_count = 0
        try:
            while self.replaying and not self.stop_event.is_set():
                loop_count += 1
                print(f"Starting replay loop {loop_count}...")
                
                start_time = time.time()
                last_timestamp = 0.0
                
                for event in self.events:
                    if not self.replaying or self.stop_event.is_set():
                        break
                    
                    # Wait for the appropriate time
                    time_to_wait = event['timestamp'] - last_timestamp
                    if time_to_wait > 0:
                        time.sleep(time_to_wait)
                    
                    # Execute the event
                    if event['type'] == 'mouse':
                        if event['action'] == 'move':
                            mouse_controller.position = (event['x'], event['y'])
                        elif event['action'] == 'press':
                            mouse_controller.position = (event['x'], event['y'])
                            button = Button.left if event['button'] == 'left' else \
                                   Button.right if event['button'] == 'right' else Button.middle
                            mouse_controller.press(button)
                        elif event['action'] == 'release':
                            button = Button.left if event['button'] == 'left' else \
                                   Button.right if event['button'] == 'right' else Button.middle
                            mouse_controller.release(button)
                        elif event['action'] == 'scroll':
                            dx = int(event.get('dx', 0))
                            dy = int(event.get('dy', 0))
                            mouse_controller.position = (event['x'], event['y'])
                            mouse_controller.scroll(dx, dy)
                    
                    elif event['type'] == 'keyboard':
                        try:
                            # Handle special keys
                            if len(event['key']) > 1:
                                key_obj = getattr(Key, event['key'], None)
                                if key_obj is None:
                                    continue
                            else:
                                key_obj = event['key']
                            
                            if event['action'] == 'press':
                                keyboard_controller.press(key_obj)
                            elif event['action'] == 'release':
                                keyboard_controller.release(key_obj)
                        except Exception as e:
                            print(f"Error replaying key {event['key']}: {e}")
                            continue
                    
                    last_timestamp = event['timestamp']
                
                if self.replaying:
                    print(f"Completed replay loop {loop_count}")
                    time.sleep(0.5)  # Small delay between loops
        
        except KeyboardInterrupt:
            print("\nReplay interrupted by user.")
        finally:
            self.replaying = False
            replay_listener.stop()
            print("Replay stopped.")


def main() -> None:
    """Main application loop with improved menu interface."""
    recorder = MouseKeyboardRecorder()
    menu = TerminalMenu(80)
    
    while True:
        try:
            # Display main menu
            menu.create_simple_menu([
                "CENTER:MOUSE AND KEYBOARD REPLAYER",
                "CENTER:Record and replay mouse movement, clicks, wheel scrolls, and keyboard events",
                [
                    "LEFT:OPTIONS:,"
                    "LEFT:[0] + ENTER --> Start Recording,"
                    "LEFT:[1] + ENTER --> Load and Replay Recording,"
                    "LEFT:[2] + ENTER --> Exit Application"
                ]
            ])
            
            choice = input("Enter your choice: ").strip()
            
            if choice == '0':
                # Recording mode
                menu.create_info_window("RECORDING MODE", "Preparing to record...", 40)
                
                recorder.start_recording()
                
                if recorder.events:
                    menu.create_info_window("SAVE RECORDING", "Recording completed successfully!", 50)
                    
                    # Ask for filename to save
                    while True:
                        filename = input("Enter filename to save recording (.json extension optional): ").strip()
                        
                        if recorder.save_recording(filename):
                            print("\nRecording saved successfully!")
                            input("\nPress ENTER to return to main menu...")
                            break
                        else:
                            retry = input("Failed to save. Try again? (y/n): ").lower().strip()
                            if retry not in ['y', 'yes']:
                                break
            
            elif choice == '1':
                # Replay mode
                menu.create_info_window("LOAD AND REPLAY RECORDING", "Select a recording to replay", 50)
                
                while True:
                    # Show available recordings
                    available_recordings = recorder.list_available_recordings()
                    if available_recordings:
                        print("Available recordings in the recordings folder:")
                        print("─" * 45)
                        for i, recording in enumerate(available_recordings, 1):
                            print(f"  {i}. {recording}")
                        print("─" * 45)
                        print()
                    else:
                        print("⚠️  No recordings found in the recordings folder.")
                        print()
                    
                    json_path = input("Enter recording filename (.json extension optional): ").strip()
                    
                    if recorder.load_recording(json_path):
                        recorder.replay_events()
                        print("\nReplay completed!")
                        input("\nPress ENTER to return to main menu...")
                        break
                    else:
                        retry = input("Failed to load file. Try again? (y/n): ").lower().strip()
                        if retry not in ['y', 'yes']:
                            input("\nPress ENTER to return to main menu...")
                            break
            
            elif choice == '2':
                # Exit application
                menu.create_goodbye_window()
                break
            
            else:
                menu.create_error_window("Invalid choice! Please enter 0, 1, or 2")
                input("Press ENTER to continue...")
        
        except KeyboardInterrupt:
            menu.create_info_window("APPLICATION INTERRUPTED", "Program was interrupted by user", 40)
            break
        except Exception as e:
            menu.create_error_window(str(e))
            input("Press ENTER to continue...")


if __name__ == "__main__":
    main()