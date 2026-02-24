"""RDP connection helpers — credential storage and mstsc launch."""

from __future__ import annotations

import subprocess


def store_credentials(computer_name: str, username: str, password: str) -> bool:
    """Store credentials using Windows cmdkey utility.

    Arguments:
        computer_name: The name or IP address of the remote computer.
        username:      The username for authentication.
        password:      The password for authentication.

    Returns:
        True if credentials were stored successfully, False otherwise.
    """
    cmd = f"cmdkey /generic:TERMSRV/{computer_name} /user:{username} /pass:{password}"
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0


def connect_to_remote_desktop(
    computer_name: str,
    multimon: bool = False,
) -> bool:
    """Launch Remote Desktop Connection to the specified computer.

    Uses ``Popen`` so the GUI thread is not blocked.

    Arguments:
        computer_name: The name or IP address of the remote computer.
        multimon:      If True, span the session across all monitors.

    Returns:
        True if the process was started, False otherwise.
    """
    try:
        cmd = f"mstsc /v:{computer_name}"
        if multimon:
            cmd += " /multimon"
        subprocess.Popen(
            cmd,
            shell=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return True
    except Exception:
        return False
