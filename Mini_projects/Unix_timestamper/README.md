# Unix Timestamper

A console application for converting between Unix timestamps and hexadecimal byte representations.

## Overview

Unix Timestamper is a utility tool that helps convert Unix timestamps to various hexadecimal byte formats, commonly used in embedded systems and binary protocols. It provides both current timestamp conversion and custom timestamp input capabilities.

## Features

- ✨ Generate hex representations from current timestamp
- 🔢 Convert custom Unix timestamps to hex format
- ✓ Automatic verification of conversions
- 📊 Human-readable timestamp display
- 🎯 Support for multiple byte formats (4-byte and 5-byte)

## Byte Formats

### 4-Byte Format
- **Structure**: 4 bytes seconds (Unix timestamp)
- **Precision**: Exact to the second
- **Use case**: When only second-level precision is required

### 5-Byte Format
- **Structure**: 1 byte SID (0x31) + 4 bytes seconds
- **Precision**: Exact to the second with SID byte prefix
- **Use case**: When SID identifier is needed in the protocol

## Installation

No external dependencies required - uses only Python standard library.

## Usage

### Running the Application

**Using Python directly:**
```bash
python Unix_timestamper.py
```

**Using the batch file:**
```bash
run.bat
```

### Menu Options

- **SPACE + ENTER**: Generate from current time
- **0 + ENTER**: Convert custom timestamp
- **Q + ENTER**: Exit application

## Examples

### Generate Current Timestamp
Press SPACE to generate hex representations of the current Unix timestamp with automatic verification.

**Output Example:**
```
Current Unix Timestamp: 1708025466
4-Byte Format: 65 B1 61 4C
5-Byte Format: 31 65 B1 61 4C
```

### Convert Custom Timestamp
Enter a timestamp to convert a specific point in time.

**Input:** `1234567890`
**Output:**
```
Custom Timestamp: 1234567890
4-Byte Format: 49 96 02 D2
5-Byte Format: 31 49 96 02 D2
```

## Technical Details

- Built with Python 3.x
- Uses custom Terminal Menu System from Common module
- Performs byte-level conversions and verification
- Thread-safe for concurrent operations
- Can be compiled to .exe using PyInstaller (build_exe.bat)

## Projects Structure

```
Unix_timestamper/
├── Unix_timestamper.py      # Main application
├── run.bat                  # Batch runner
├── build_exe.bat            # PyInstaller build script
├── FEATURE_DOCUMENTATION.md # Detailed feature docs
└── README.md               # This file
```

## Building Executable

To create a standalone .exe file:
```bash
build_exe.bat
```

This generates the executable in the `dist/` directory.

## Author Notes

This tool is particularly useful for:
- Protocol implementation and testing
- Debugging binary communication systems
- Timestamp validation in legacy systems
- Educational purposes for understanding byte representations

## License

Personal utility project - use freely in your projects.
