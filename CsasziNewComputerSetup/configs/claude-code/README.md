# Claude Code (corporate LLM gateway)

Bundles the corporate `install_claude_code.ps1` plus the credentials needed
to run it unattended, so the `claude-code` app in CsasziNewComputerSetup
installs Claude Code in one shot.

## Files

- `install_claude_code.ps1` — verbatim copy of the corporate installer.
  Refresh from `~\Downloads\install_claude_code.ps1` when a new version drops.
- `credentials.json` — the JWT auth token + UID. Replace `auth_token` when
  it rotates.

The custom action in `main.py` runs PowerShell on the script and pipes the
credentials into its `Read-Host` prompts (token first as SecureString, then
UID as plain text).
