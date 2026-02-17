"""
Remote Desktop Connection Script.

This script connects to a remote desktop using provided credentials.
It uses Windows' mstsc (Remote Desktop Connection) with cmdkey for credential management.

Usage:
    python remote_desktop.py <computer_name> <username> <password>

Example:
    python remote_desktop.py 192.168.1.100 admin MyPassword123
"""

import argparse
import subprocess
import sys


def parse_arguments() -> argparse.Namespace:
    """
    Parse command line arguments for remote desktop connection.

    Arguments:
        None

    Returns:
        argparse.Namespace: Parsed arguments containing computer_name, username, and password.
    """
    parser = argparse.ArgumentParser(
        description="Connect to a remote desktop using provided credentials."
    )
    parser.add_argument(
        "computer_name",
        type=str,
        help="The name or IP address of the remote computer."
    )
    parser.add_argument(
        "username",
        type=str,
        help="The username for authentication."
    )
    parser.add_argument(
        "password",
        type=str,
        help="The password for authentication."
    )
    return parser.parse_args()


def store_credentials(computer_name: str, username: str, password: str) -> bool:
    """
    Store credentials using Windows cmdkey utility.

    Arguments:
        computer_name (str): The name or IP address of the remote computer.
        username (str): The username for authentication.
        password (str): The password for authentication.

    Returns:
        bool: True if credentials were stored successfully, False otherwise.
    """
    cmd = f'cmdkey /generic:TERMSRV/{computer_name} /user:{username} /pass:{password}'
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0


def connect_to_remote_desktop(computer_name: str) -> bool:
    """
    Launch Remote Desktop Connection to the specified computer.

    Arguments:
        computer_name (str): The name or IP address of the remote computer.

    Returns:
        bool: True if the connection was initiated successfully, False otherwise.
    """
    cmd = f'mstsc /v:{computer_name}'
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0


def delete_credentials(computer_name: str) -> bool:
    """
    Delete stored credentials for the specified computer.

    Arguments:
        computer_name (str): The name or IP address of the remote computer.

    Returns:
        bool: True if credentials were deleted successfully, False otherwise.
    """
    cmd = f'cmdkey /delete:TERMSRV/{computer_name}'
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0


def main() -> int:
    """
    Main function to orchestrate remote desktop connection.

    Arguments:
        None

    Returns:
        int: Exit code (0 for success, 1 for failure).
    """
    args = parse_arguments()
    print(f"Connecting to {args.computer_name} as {args.username}...")
    # Store credentials
    if not store_credentials(args.computer_name, args.username, args.password):
        print("ERROR: Failed to store credentials.", file=sys.stderr)
        return 1
    print("Credentials stored successfully.")
    # Launch Remote Desktop Connection
    if not connect_to_remote_desktop(args.computer_name):
        print("ERROR: Failed to launch Remote Desktop Connection.", file=sys.stderr)
        return 1
    print("Remote Desktop Connection launched successfully.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
