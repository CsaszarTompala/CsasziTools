"""
Unix Timestamper Application

This application converts between Unix timestamps and hexadecimal byte representations.

Features:
- Generate 4-byte and 5-byte hex representations from current timestamp
- Convert custom Unix timestamps to hex representations
- Display results in readable format

The byte representations:
- 4 bytes: Unix timestamp in seconds only (exact to the second)
- 5 bytes: Unix timestamp in seconds + milliseconds (4ms precision)
"""

import sys
import os
import time
import threading
from typing import Tuple

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))) 
from Common.Menu import TerminalMenu

SID_BYTE = 0x31
MENU_LENGTH = 200

class UnixTimestampConverter:
    """
    Handles conversion between Unix timestamps and hexadecimal byte representations.
    """
    
    @staticmethod
    def print_hex(data: bytes) -> str:
        """
        Convert bytes to hexadecimal string representation.
        
        Arguments:
            data (bytes): The byte data to convert
            
        Returns:
            str: Hexadecimal string representation (e.g., "01 02 03")
        """
        return ' '.join(f'{b:02X}' for b in data)
    
    @staticmethod
    def get_current_timestamp() -> float:
        """
        Get the current Unix timestamp with millisecond precision.
        
        Returns:
            float: Current Unix timestamp (seconds.milliseconds)
        """
        return time.time()
    
    @staticmethod
    def timestamp_to_5_bytes(timestamp: float) -> bytes:
        """
        Convert Unix timestamp to 4-byte hexadecimal representation (seconds only).
        
        Arguments:
            timestamp (float): Unix timestamp to convert
            
        Returns:
            bytes: 4-byte representation (4 bytes seconds)
        """
        seconds = int(timestamp)
        timestamp_bytes = seconds.to_bytes(4, byteorder='big', signed=False)
        return timestamp_bytes
    
    @staticmethod
    def timestamp_to_6_bytes(timestamp: float) -> bytes:
        """
        Convert Unix timestamp to 5-byte hexadecimal representation (seconds + milliseconds).
        
        Arguments:
            timestamp (float): Unix timestamp to convert
            
        Returns:
            bytes: 5-byte representation (4 bytes seconds + 1 byte milliseconds/4)
        """
        seconds = int(timestamp)
        millis = int((timestamp - seconds) * 1000)
        millis_scaled = millis // 4
        seconds_bytes = seconds.to_bytes(4, byteorder='big', signed=False)
        millis_byte = bytes([millis_scaled])
        return seconds_bytes + millis_byte
    
    @staticmethod
    def bytes_5_to_timestamp(data: bytes) -> float:
        """
        Convert 4-byte hexadecimal representation back to Unix timestamp.
        
        Arguments:
            data (bytes): 4-byte data (4 bytes seconds)
            
        Returns:
            float: Unix timestamp
        """
        if len(data) != 4:
            raise ValueError(f"Expected 4 bytes, got {len(data)}")
        seconds = int.from_bytes(data, byteorder='big', signed=False)
        return float(seconds)
    
    @staticmethod
    def bytes_6_to_timestamp(data: bytes) -> float:
        """
        Convert 5-byte hexadecimal representation back to Unix timestamp.
        
        Arguments:
            data (bytes): 5-byte data (4 bytes seconds + 1 byte milliseconds/4)
            
        Returns:
            float: Unix timestamp (seconds.milliseconds)
        """
        if len(data) != 5:
            raise ValueError(f"Expected 5 bytes, got {len(data)}")
        seconds = int.from_bytes(data[:4], byteorder='big', signed=False)
        millis_scaled = data[4]
        millis = millis_scaled * 4
        return float(f"{seconds}.{millis:03d}")
    
    def generate_from_current(self) -> Tuple[float, bytes, bytes]:
        """
        Generate hex representations from current timestamp.
        
        Returns:
            tuple: (timestamp, 5_byte_hex, 6_byte_hex)
        """
        timestamp = self.get_current_timestamp()
        bytes_5 = self.timestamp_to_5_bytes(timestamp)
        bytes_6 = self.timestamp_to_6_bytes(timestamp)
        return timestamp, bytes_5, bytes_6
    
    def generate_from_timestamp(self, timestamp: float) -> Tuple[bytes, bytes]:
        """
        Generate hex representations from custom timestamp.
        
        Arguments:
            timestamp (float): Unix timestamp to convert
            
        Returns:
            tuple: (5_byte_hex, 6_byte_hex)
        """
        bytes_5 = self.timestamp_to_5_bytes(timestamp)
        bytes_6 = self.timestamp_to_6_bytes(timestamp)
        return bytes_5, bytes_6
    
    def verify_conversion(self, original: float, bytes_5: bytes, bytes_6: bytes) -> Tuple[bool, bool]:
        """
        Verify that conversion is reversible.
        
        Arguments:
            original (float): Original timestamp
            bytes_5 (bytes): 5-byte representation
            bytes_6 (bytes): 6-byte representation
            
        Returns:
            tuple: (5_byte_match, 6_byte_match)
        """
        converted_5 = self.bytes_5_to_timestamp(bytes_5)
        converted_6 = self.bytes_6_to_timestamp(bytes_6)
        match_5 = int(original) == int(converted_5)
        match_6 = abs(original - converted_6) < 0.001
        return match_5, match_6


def display_timestamp_result(menu: TerminalMenu, converter: UnixTimestampConverter, timestamp: float, bytes_5: bytes, bytes_6: bytes, verify: bool = True) -> None:
    """
    Display timestamp conversion results below the menu.
    
    Arguments:
        menu (TerminalMenu): Menu instance for display
        converter (UnixTimestampConverter): Converter instance for verification
        timestamp (float): Original Unix timestamp
        bytes_5 (bytes): 5-byte hex representation
        bytes_6 (bytes): 6-byte hex representation
        verify (bool): Whether to verify conversion accuracy
        
    Returns:
        None
    """
    readable_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(timestamp))
    millis = int((timestamp - int(timestamp)) * 1000)
    print()
    print("╔" + "═" * MENU_LENGTH + "╗")
    print("║" + " " * MENU_LENGTH + "║")
    print("║" + "TIMESTAMP RESULT".center(MENU_LENGTH) + "║")
    print("║" + " " * MENU_LENGTH + "║")
    print("╠" + "═" * MENU_LENGTH + "╣")
    print("║" + f"  Time:   {readable_time}.{millis:03d}".ljust(MENU_LENGTH) + "║")
    print("║" + f"  Unix:   {timestamp}".ljust(MENU_LENGTH) + "║")
    print("║" + " " * MENU_LENGTH + "║")
    print("║" + f"  5-byte: {converter.print_hex(bytes_5)}".ljust(MENU_LENGTH) + "║")
    print("║" + f"  6-byte: {converter.print_hex(bytes_6)}".ljust(MENU_LENGTH) + "║")
    print("║" + " " * MENU_LENGTH + "║")
    if verify:
        # Verification section
        match_5, match_6 = converter.verify_conversion(timestamp, bytes_5, bytes_6)
        converted_5 = converter.bytes_5_to_timestamp(bytes_5)
        converted_6 = converter.bytes_6_to_timestamp(bytes_6)
        status_5 = "✓ MATCH" if match_5 else "✗ MISMATCH"
        status_6 = "✓ MATCH" if match_6 else "✗ MISMATCH"
        print("║" + f"  Check:  5-byte → {status_5}  |  6-byte → {status_6}".ljust(MENU_LENGTH) + "║")
        print("║" + " " * MENU_LENGTH + "║")
    print("╚" + "═" * MENU_LENGTH + "╝")


def display_current_timestamp(converter: UnixTimestampConverter) -> None:
    """
    Display current timestamp hex values in a compact format.
    
    Arguments:
        converter (UnixTimestampConverter): Converter instance
        
    Returns:
        None
    """
    timestamp, bytes_4, bytes_5 = converter.generate_from_current()
    readable_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(timestamp))
    millis = int((timestamp - int(timestamp)) * 1000)
    print(f"\r  Time: {readable_time}.{millis:03d}  |  Unix: {timestamp:.3f}  |  4-byte: {converter.print_hex(bytes_4)}  |  5-byte: {converter.print_hex(bytes_5)}", end='', flush=True)


def main() -> None:
    """
    Main application loop with menu interface and continuous timestamp display.
    
    Returns:
        None
    """
    converter = UnixTimestampConverter()
    menu = TerminalMenu(80)
    running = True
    frozen_timestamps = []
    # Thread function to continuously update timestamp display
    def update_display() -> None:
        """
        Continuously update the timestamp display.
        
        Returns:
            None
        """
        while running:
            display_current_timestamp(converter)
            time.sleep(0.1)
    # Start display update thread
    display_thread = threading.Thread(target=update_display, daemon=True)
    display_thread.start()
    try:
        while True:
            try:
                # Display main menu
                menu.clear_terminal()
                menu.create_simple_menu([
                    "CENTER:UNIX TIMESTAMP CONVERTER",
                    "CENTER:Continuously displaying current timestamp hex values"
                ])
                print()
                print("OPTIONS:")
                print("  [ENTER] --> Freeze current timestamp")
                print("  [0] + ENTER --> Convert Custom Timestamp")
                print("  [Q] + ENTER --> Exit Application")
                print()
                # Display frozen timestamps
                for frozen in frozen_timestamps:
                    print(frozen)
                # Cursor on new line for input
                print()
                choice = input().strip().lower()
                if choice == '':
                    # Freeze current timestamp
                    timestamp, bytes_4, bytes_5 = converter.generate_from_current()
                    readable_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(timestamp))
                    millis = int((timestamp - int(timestamp)) * 1000)
                    frozen_line = f"  Time: {readable_time}.{millis:03d}  |  Unix: {timestamp:.3f}  |  4-byte: {converter.print_hex(bytes_4)}  |  5-byte: {converter.print_hex(bytes_5)}"
                    frozen_timestamps.append(frozen_line)
                elif choice == '0':
                    # Convert custom timestamp
                    menu.clear_terminal()
                    menu.create_simple_menu([
                        "CENTER:CUSTOM TIMESTAMP CONVERSION",
                        "CENTER:Enter a Unix timestamp to convert"
                    ])
                    print("\nExamples:")
                    print("  • Integer:  1234567890")
                    print("  • Decimal:  1234567890.123")
                    print("  • Current:  " + str(int(time.time())))
                    print()
                    try:
                        timestamp_str = input("Enter Unix timestamp: ").strip()
                        timestamp = float(timestamp_str)
                        bytes_4, bytes_5 = converter.generate_from_timestamp(timestamp)
                        readable_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(timestamp))
                        millis = int((timestamp - int(timestamp)) * 1000)
                        print()
                        print("╔" + "═" * MENU_LENGTH + "╗")
                        print("║" + " " * MENU_LENGTH + "║")
                        print("║" + "CUSTOM TIMESTAMP RESULT".center(MENU_LENGTH) + "║")
                        print("║" + " " * MENU_LENGTH + "║")
                        print("╠" + "═" * MENU_LENGTH + "╣")
                        print("║" + f"  Time:   {readable_time}.{millis:03d}".ljust(MENU_LENGTH) + "║")
                        print("║" + f"  Unix:   {timestamp}".ljust(MENU_LENGTH) + "║")
                        print("║" + " " * MENU_LENGTH + "║")
                        print("║" + f"  4-byte: {converter.print_hex(bytes_4)}".ljust(MENU_LENGTH) + "║")
                        print("║" + f"  5-byte: {converter.print_hex(bytes_5)}".ljust(MENU_LENGTH) + "║")
                        print("║" + " " * MENU_LENGTH + "║")
                        print("╚" + "═" * MENU_LENGTH + "╝")
                        input("\nPress ENTER to continue...")
                    except ValueError:
                        menu.create_error_window("Invalid timestamp format! Please enter a valid number.")
                        input("Press ENTER to continue...")
                    except Exception as e:
                        menu.create_error_window(f"Error during conversion: {str(e)}")
                        input("Press ENTER to continue...")
                elif choice == 'q' or choice == 'quit' or choice == 'exit':
                    # Exit application
                    running = False
                    menu.clear_terminal()
                    print("╔" + "═" * 38 + "╗")
                    print("║" + " " * 38 + "║")
                    print("║" + "Thank you for using".center(38) + "║")
                    print("║" + "Unix Timestamp Converter!".center(38) + "║")
                    print("║" + " " * 38 + "║")
                    print("╚" + "═" * 38 + "╝")
                    print()
                    break
                else:
                    menu.create_error_window("Invalid choice! Please enter ENTER, 0, or Q")
                    input("Press ENTER to continue...")
            except KeyboardInterrupt:
                running = False
                menu.clear_terminal()
                menu.create_info_window("APPLICATION INTERRUPTED", "Program was interrupted by user", 40)
                break
            except Exception as e:
                menu.create_error_window(str(e))
                input("Press ENTER to continue...")
    finally:
        running = False
        time.sleep(0.2)


if __name__ == "__main__":
    main()