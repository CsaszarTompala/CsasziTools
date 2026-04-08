# CsasziJira

Bulk Jira story closer. Reads a JSON input file with a project key and story numbers, then for each story:

1. Fetches the issue and discovers subtasks.
2. Sets the **Fix Version** on each subtask and transitions it to **Done**.
3. Sets the **Fix Version** on the parent story and transitions it to **Done**.

## Setup

1. **Install dependencies**

   ```bash
   pip install -r requirements.txt
   ```

2. **Configure Jira credentials** — edit `config.json`:

   | Key                    | Description                                      |
   |------------------------|--------------------------------------------------|
   | `jira_url`             | Base URL of the Jira instance                    |
   | `email`                | Your Jira account email                          |
   | `api_token`            | Jira API token (generate at Atlassian account)   |
   | `fix_version`          | Version name to set on issues (e.g. `ARS620DP28_SW_R5.1_GC`) |
   | `done_transition_name` | Name of the workflow transition to Done           |
   | `dry_run`              | `true` to preview without changes                |

3. **Prepare the stories file** — edit `stories.json`:

   ```json
   {
     "project": "ARS620DP28",
     "stories": [4087, 4088, 4089]
   }
   ```

## Usage

### Quick start (Windows)

```bash
run.bat
```

### Command line

```bash
# Default: reads stories.json, uses config.json settings
python main.py

# Custom stories file
python main.py --stories my_batch.json

# Preview mode (no changes)
python main.py --dry-run

# Override fix version
python main.py --fix-version "ARS620DP28_SW_R5.1_GC"
```

## How it works

- **Get issue**: `GET /rest/api/2/issue/{key}` — retrieves summary, status, subtasks, fix versions.
- **Set fix version**: `PUT /rest/api/2/issue/{key}` — sets `fixVersions` field.
- **Get transitions**: `GET /rest/api/2/issue/{key}/transitions` — lists available workflow transitions.
- **Transition to Done**: `POST /rest/api/2/issue/{key}/transitions` — executes the transition.

The tool processes subtasks before the parent story to satisfy any workflow constraints that require all subtasks to be closed first.

## Configuration reference

### config.json

```json
{
  "jira_url": "https://central.jira.automotive.cloud",
  "email": "your.email@example.com",
  "api_token": "YOUR_API_TOKEN_HERE",
  "fix_version": "ARS620DP28_SW_R5.1_GC",
  "done_transition_name": "Done",
  "dry_run": true
}
```

### stories.json

```json
{
  "project": "ARS620DP28",
  "stories": [4087]
}
```

## Generating a Jira API token

1. Go to [Atlassian API Tokens](https://id.atlassian.com/manage-profile/security/api-tokens).
2. Click **Create API token**, give it a label, and copy the token.
3. Paste it into `config.json` as `api_token`.

## Dependencies

- Python 3.7+
- `requests`
