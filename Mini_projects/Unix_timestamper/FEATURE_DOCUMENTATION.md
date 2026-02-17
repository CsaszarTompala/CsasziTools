# Unix Timestamp Converter

A console application for converting between Unix timestamps and hexadecimal byte representations.

## Features

- ✨ Generate hex representations from current timestamp
- 🔢 Convert custom Unix timestamps to hex format
- ✓ Automatic verification of conversions
- 📊 Human-readable timestamp display

## Byte Formats

### 5-Byte Format
- **Structure**: 1 byte SID (0x31) + 4 bytes seconds
- **Precision**: Exact to the second
- **Use case**: When millisecond precision is not required

### 6-Byte Format
- **Structure**: 1 byte SID (0x31) + 4 bytes seconds + 1 byte milliseconds
- **Precision**: Exact to the millisecond
- **Use case**: When millisecond precision is needed

## Usage

Run the application:
```bash
python unix_timestamper.py
```

Or use the batch file:
```bash
run.bat
```

### Menu Options

- **SPACE + ENTER**: Generate from current time
- **0 + ENTER**: Convert custom timestamp
- **Q + ENTER**: Exit application

## Examples

### Current Timestamp
Press SPACE to generate hex representations of the current Unix timestamp with automatic verification.

### Custom Timestamp
Enter `1234567890` or `1234567890.123` to convert a specific timestamp.

## Technical Details

The converter uses big-endian byte order for all multi-byte values, matching the `_get_meas_start` method's expected format.

## Conversion Logic

The application matches the logic used in your diagnostic event parser:

```python
# 5-byte format (seconds only)
seconds = int.from_bytes(timestamp_bytes, byteorder='big')
return float(seconds)

# 6-byte format (seconds + milliseconds)
seconds = int.from_bytes(timestamp_bytes[:4], byteorder='big')
millis = int.from_bytes(timestamp_bytes[4:], byteorder='big')
return float(f"{seconds}.{millis:03d}")
```

## Version

Current version: 1.0.0