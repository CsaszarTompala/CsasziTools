# Jenkins Node Auto-Starter

An automated service for monitoring and starting Jenkins agent nodes on a scheduled basis.

## Overview

Jenkins Node Auto-Starter is a system utility that monitors whether a Jenkins agent process is running and automatically starts it if needed. It runs a scheduled check at a configurable time each day and provides a countdown window allowing manual intervention before the agent starts.

## Features

- 🤖 Automatic Jenkins agent process detection
- ⏰ Scheduled daily checks at 2 AM (configurable)
- ⏱️ 60-second countdown window before auto-start (with option to cancel)
- 🔔 Visual notification with tkinter GUI countdown
- 📝 Detailed process monitoring via Windows WMIC
- 🛡️ Safe startup with manual override capability
- 🔄 Continuous monitoring in background

## Installation

### Requirements

- Python 3.6+
- Windows OS (uses Windows-specific tools: WMIC, mstsc, cmdkey)
- schedule library
- tkinter (usually included with Python)

### Setup

1. Install required dependencies:
```bash
pip install -r requirements.txt
```

Or manually:
```bash
pip install schedule
```

2. Configure the application:
   - Edit `config.json` in the project directory
   - Update `batch_file_path` to point to your Jenkins startup batch file
   - Adjust other settings as needed (see Configuration section)

3. Run the application:
```bash
python jenkins_auto_starter.py
```

Or use the batch file:
```bash
run_jenkins_auto_starter.bat
```

## Configuration

All settings are configured via `config.json` file in the project directory:

```json
{
  "batch_file_path": "C:\\Path\\To\\Your\\START_jenkins_via_application.bat",
  "java_process_name": "java.exe",
  "jenkins_agent_identifier": "agent.jar",
  "countdown_seconds": 60
}
```

### Configuration Parameters

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `batch_file_path` | string | Full path to the batch file that starts Jenkins | (required) |
| `java_process_name` | string | Java process name to monitor | java.exe |
| `jenkins_agent_identifier` | string | String to identify Jenkins in process command line | agent.jar |
| `countdown_seconds` | integer | Seconds for countdown before auto-start | 60 |

**Note:** Use double backslashes (`\\`) in JSON paths. The batch file path is required and must be customized for your environment.

### Schedule Configuration

Default schedule: **2:00 AM daily**

To change the schedule, modify the scheduling section in the main loop within `jenkins_auto_starter.py`:
```python
schedule.every().day.at("02:00").do(check_and_start_jenkins)
```

## Usage

### Start the Service

```bash
python jenkins_auto_starter.py
```

The application will:
1. Start in the background
2. Check for running Jenkins agent process
3. Every day at 2 AM:
   - If agent is running: Do nothing
   - If agent is NOT running: Show countdown window and start it

### What Happens During Auto-Start

1. **Countdown Window Appears**: Shows a tkinter GUI with 60-second countdown
2. **User Options**:
   - Let it count down: Jenkins will start automatically
   - Click "Cancel": Abort the startup for this session
3. **Agent Starts**: The configured batch file is executed
4. **Logging**: Status messages are printed to console

### Manual Check

The script checks the Jenkins agent status by:
1. Querying Windows for running Java processes via WMIC
2. Looking for `agent.jar` in the command line arguments
3. If found: Agent is running
4. If not found: Schedule startup

## Process Monitoring

The application uses Windows WMIC (Windows Management Instrumentation Command-line) to:
- Detect Java processes (`java.exe`)
- Parse command line arguments
- Identify if `agent.jar` is being executed

## GUI Countdown Interface

When Jenkins needs to start, a countdown window appears showing:
- Time remaining (60 seconds)
- Cancel button for manual intervention
- Application name and status message

## Project Structure

```
OpenJenkins/
├── jenkins_auto_starter.py    # Main application
├── config.json                # Configuration file (customize for your environment)
├── README.md                  # This file
├── requirements.txt           # Dependencies
└── run_jenkins_auto_starter.bat # Batch runner
```

## Windows Integration

### Running as a Windows Service

To run continuously as a Windows service:

1. Install NSSM (Non-Sucking Service Manager):
```bash
choco install nssm
```

2. Create a service:
```bash
nssm install JenkinsAutoStarter "C:\path\to\python.exe" "C:\path\to\jenkins_auto_starter.py"
nssm start JenkinsAutoStarter
```

### Adding to Task Scheduler

Alternatively, use Windows Task Scheduler:

1. Open Task Scheduler
2. Create Basic Task
3. Set trigger: Daily at 2:00 AM
4. Set action: Start program - python jenkins_auto_starter.py
5. Configure: Run with highest privileges

## Troubleshooting

### Configuration errors
- **"config.json not found"**: Ensure `config.json` exists in the same directory as `jenkins_auto_starter.py`
- **"config.json is not valid JSON"**: Check JSON syntax - use a JSON validator online
- **Invalid batch file path**: Verify the path exists and use double backslashes (`\\`) in the JSON file

### Jenkins not starting
- Verify `batch_file_path` in `config.json` points to the correct file
- Check batch file works correctly when run manually
- Ensure Python has execute permissions
- Check Windows Firewall isn't blocking the batch file

### Process detection fails
- Run as Administrator for WMIC queries
- Verify Jenkins is actually running with `tasklist /v | findstr java`
- Check `jenkins_agent_identifier` in `config.json` matches your agent.jar location

### Countdown window doesn't appear
- Install tkinter: `pip install tk`
- Check console for error messages
- Verify Python is running with GUI capabilities

### Script doesn't run at scheduled time
- Ensure Python is in system PATH
- Use full absolute paths for batch files
- Check Windows Task Scheduler logs for errors
- Verify time zone settings are correct

## Logging

All events are printed to console:
```
[2026-02-17 14:30:00] Jenkins agent check started
[2026-02-17 14:30:01] Jenkins agent is running - no action needed
[2026-02-17 02:00:00] Jenkins agent not running - starting now!
```

## Performance Considerations

- Very lightweight - minimal CPU and memory usage
- Checks run on schedule, not continuously
- GUI countdown window unblocks after selection
- Safe to keep running 24/7

## Security Notes

- Keep batch file path secure
- Don't share batch file containing credentials in comments
- Consider storing credentials in Windows Credential Manager
- Run with minimal required privileges

## License

Personal utility project - use freely in your projects.

## Author Notes

Jenkins Node Auto-Starter is useful for:
- Development environments that need Jenkins running
- CI/CD pipelines requiring auto-recovery
- Automated deployment systems
- Lab environments with scheduled maintenance

## Related Tools

- NSSM - Windows Service Manager
- Windows Task Scheduler - Alternative scheduling
- Jenkins Documentation - Learning Jenkins
