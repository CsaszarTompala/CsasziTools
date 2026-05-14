# Keyboard layout: amerikai - Custom (Y/Z swap)

Custom US English keyboard layout with the **Y** and **Z** keys swapped,
created with Microsoft Keyboard Layout Creator.

## Files

| File              | Where it goes                  |
|-------------------|--------------------------------|
| `Layout01.x64.dll`| `C:\Windows\System32\Layout01.dll` (64-bit) |
| `Layout01.x86.dll`| `C:\Windows\SysWOW64\Layout01.dll` (32-bit) |
| `layout.reg`      | Imported into HKLM (machine-wide registration) |

The setup tool also writes per-user input-method entries:

```
HKCU\Keyboard Layout\Preload\1                      = "d0010409"
HKCU\Keyboard Layout\Substitutes\d0010409           = "a0000409"
```

so the custom layout becomes the default input method for the current user.
A logoff/logon (or reboot) is required for Windows to pick it up.
