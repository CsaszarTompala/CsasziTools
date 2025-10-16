"""
Mouse and Keyboard Replayer Application

This application allows users to record mouse clicks and keyboard keystrokes,
save them to a JSON file, and replay them in a loop until stopped.

Features:
- Records left, right, and middle mouse button clicks with coordinates
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
import sys
import os
from typing import List, Dict, Any, Optional
from threading import Event
from pynput import mouse, keyboard
from pynput.mouse import Button
from pynput.keyboard import Key


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
    
    def reset_recording(self) -> None:
        """Reset the recording state and clear events."""
        self.events.clear()
        self.recording = False
        self.stop_event.clear()
        self.start_time = 0.0
    
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
        mouse_listener = mouse.Listener(on_click=self.on_mouse_click)
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
        Save the recorded events to a JSON file.
        
        Arguments:
            filename: Path to the JSON file to save
            
        Returns:
            True if save was successful, False otherwise
        """
        try:
            with open(filename, 'w') as f:
                json.dump(self.events, f, indent=2)
            print(f"Recording saved to {filename}")
            return True
        except Exception as e:
            print(f"Error saving recording: {e}")
            return False
    
    def load_recording(self, filename: str) -> bool:
        """
        Load recorded events from a JSON file.
        
        Arguments:
            filename: Path to the JSON file to load
            
        Returns:
            True if load was successful, False otherwise
        """
        try:
            if not os.path.exists(filename):
                print(f"File {filename} does not exist.")
                return False
            
            with open(filename, 'r') as f:
                self.events = json.load(f)
            print(f"Recording loaded from {filename}. {len(self.events)} events loaded.")
            return True
        except Exception as e:
            print(f"Error loading recording: {e}")
            return False
    
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
                        if event['action'] == 'press':
                            mouse_controller.position = (event['x'], event['y'])
                            button = Button.left if event['button'] == 'left' else \
                                   Button.right if event['button'] == 'right' else Button.middle
                            mouse_controller.press(button)
                        elif event['action'] == 'release':
                            button = Button.left if event['button'] == 'left' else \
                                   Button.right if event['button'] == 'right' else Button.middle
                            mouse_controller.release(button)
                    
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
    """Main application loop."""
    recorder = MouseKeyboardRecorder()
    
    print("=== Mouse and Keyboard Replayer ===")
    print("This application can record and replay mouse clicks and keyboard keystrokes.")
    print()
    
    while True:
        try:
            choice = input("Do you want to record? (yes/no): ").lower().strip()
            
            if choice in ['yes', 'y']:
                # Recording mode
                recorder.start_recording()
                
                if recorder.events:
                    # Ask for filename to save
                    while True:
                        filename = input("Enter filename to save recording (with .json extension): ").strip()
                        if not filename.endswith('.json'):
                            filename += '.json'
                        
                        if recorder.save_recording(filename):
                            break
                        else:
                            retry = input("Failed to save. Try again? (yes/no): ").lower().strip()
                            if retry not in ['yes', 'y']:
                                break
            
            elif choice in ['no', 'n']:
                # Replay mode
                while True:
                    json_path = input("Enter path to JSON recording file: ").strip()
                    
                    if recorder.load_recording(json_path):
                        recorder.replay_events()
                        break
                    else:
                        retry = input("Failed to load file. Try again? (yes/no): ").lower().strip()
                        if retry not in ['yes', 'y']:
                            break
                
                # After replay, ask if user wants to continue
                continue_choice = input("Do you want to continue using the application? (yes/no): ").lower().strip()
                if continue_choice not in ['yes', 'y']:
                    break
            
            else:
                print("Please enter 'yes' or 'no'.")
                continue
        
        except KeyboardInterrupt:
            print("\nApplication interrupted by user.")
            break
        except Exception as e:
            print(f"An error occurred: {e}")
            continue
    
    print("Thank you for using Mouse and Keyboard Replayer!")


if __name__ == "__main__":
    main()