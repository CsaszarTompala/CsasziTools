#Requires -Version 5.1
<#
.SYNOPSIS
    Installs and configures Claude Code for the corporate LLM gateway.
    Run without parameters  --  the script will prompt for all required inputs.

.PARAMETER InstallDir
    Directory where claudecode.bat will be saved. Defaults to C:\tools\claudecode.

.PARAMETER CACertPath
    Path to a corporate CA certificate file (.pem or .cer). When provided, the installer
    uses a session-only TLS callback and the launcher uses NODE_EXTRA_CA_CERTS instead of
    disabling TLS verification.
#>
[CmdletBinding()]
param(
    [string]$InstallDir = 'C:\tools\claudecode',
    [string]$CACertPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ANTHROPIC_BASE_URL = 'https://llm-gateway.ve42034x.automotive-wan.com'
$NO_PROXY           = 'localhost,127.0.0.1,automotive-wan.com'
$BAT_FILENAME       = 'claudecode.bat'

# Set to $true when claudecode.bat is placed on the Machine PATH  --  triggers multi-user settings update
$script:InstalledToMachinePath = $false

# Git cmd directory if Git was freshly installed by this script  --  added to PATH in Step 3
$script:GitCmdDir = $null

# ---------------------------------------------------------------------------
# Validate -CACertPath early (before any function uses it)
# ---------------------------------------------------------------------------

if ($CACertPath) {
    if (-not (Test-Path $CACertPath -PathType Leaf)) {
        Write-Host "  [ERROR] -CACertPath file not found: $CACertPath" -ForegroundColor Red
        exit 1
    }

    # Resolve to absolute path so the .bat launcher works from any directory
    $CACertPath = [System.IO.Path]::GetFullPath($CACertPath)

    # Validate it's a loadable certificate (not an arbitrary file)
    try {
        $null = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CACertPath)
    } catch {
        Write-Host "  [ERROR] -CACertPath is not a valid certificate: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

# ---------------------------------------------------------------------------
# Helpers  (defined before any interactive prompt so Write-Err is available)
# ---------------------------------------------------------------------------

function Write-Step([string]$Num, [string]$Title) {
    Write-Host ""
    Write-Host ('=' * 60)
    Write-Host "  Step $Num`: $Title"
    Write-Host ('=' * 60)
}

function Write-Ok([string]$Msg)   { Write-Host "  [OK]   $Msg" -ForegroundColor Green }
function Write-Skip([string]$Msg) { Write-Host "  [SKIP] $Msg" -ForegroundColor DarkGray }
function Write-Info([string]$Msg) { Write-Host "  [INFO] $Msg" -ForegroundColor Cyan }
function Write-Err([string]$Msg)  { Write-Host "  [ERROR] $Msg" -ForegroundColor Red }

# ---------------------------------------------------------------------------
# Validate -InstallDir parameter
# ---------------------------------------------------------------------------

if ([string]::IsNullOrWhiteSpace($InstallDir)) {
    Write-Err "-InstallDir cannot be empty."
    exit 1
}

# ---------------------------------------------------------------------------
# Prompt for required inputs interactively
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "Claude Code Installer  --  Corporate LLM Gateway Configuration"
Write-Host ""

$MaxRetries = 3

$Token = $null
for ($i = 1; $i -le $MaxRetries; $i++) {
    $SecureToken = Read-Host "  Enter your JWT token (ANTHROPIC_AUTH_TOKEN)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureToken)
    try {
        $Token = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
    if (-not [string]::IsNullOrWhiteSpace($Token)) { break }
    $Token = $null
    $remaining = $MaxRetries - $i
    if ($remaining -gt 0) {
        Write-Err "Please do not leave this parameter empty. ($remaining attempt(s) remaining)"
    }
}
if ($null -eq $Token) {
    Write-Err "JWT token was not provided after $MaxRetries attempts. Aborting."
    Read-Host "  Press Enter to exit" | Out-Null
    exit 1
}

$UID = $null
for ($i = 1; $i -le $MaxRetries; $i++) {
    $UID = Read-Host "  Enter your UID (e.g. uix12345)"
    if (-not [string]::IsNullOrWhiteSpace($UID)) { break }
    $UID = $null
    $remaining = $MaxRetries - $i
    if ($remaining -gt 0) {
        Write-Err "Please do not leave this parameter empty. ($remaining attempt(s) remaining)"
    }
}
if ($null -eq $UID) {
    Write-Err "UID was not provided after $MaxRetries attempts. Aborting."
    Read-Host "  Press Enter to exit" | Out-Null
    exit 1
}

Write-Host ""
Write-Host "  JWT token    : $('*' * [Math]::Min($Token.Length, 8))... (hidden)"
Write-Host "  UID          : $UID"
Write-Host "  Install dir  : $InstallDir"
Write-Host "  Gateway URL  : $ANTHROPIC_BASE_URL"

function Broadcast-EnvChange {
    try {
        $signature = '[DllImport("user32.dll", SetLastError=true, CharSet=CharSet.Auto)]
            public static extern IntPtr SendMessageTimeout(
                IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam,
                uint fuFlags, uint uTimeout, out UIntPtr lpdwResult);'
        $type = Add-Type -MemberDefinition $signature -Name 'Win32SendMessage' -Namespace 'Win32' -PassThru -ErrorAction SilentlyContinue
        $result = [UIntPtr]::Zero
        $type::SendMessageTimeout([IntPtr]0xFFFF, 0x001A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]$result) | Out-Null
    } catch { }
}

function Add-ToMachinePath([string]$Dir) {
    try {
        $regKey = [Microsoft.Win32.Registry]::LocalMachine.OpenSubKey(
            'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', $true)
        if ($null -eq $regKey) { return $false }

        $current = $regKey.GetValue('PATH', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
        $entries = $current -split ';' | Where-Object { $_ -ne '' }

        if ($Dir -in $entries) {
            Write-Skip "Directory already in Machine PATH: $Dir"
            $regKey.Close()
            $script:InstalledToMachinePath = $true
            return $true
        }

        $newPath = ($current.TrimEnd(';') + ';' + $Dir)
        $regKey.SetValue('PATH', $newPath, [Microsoft.Win32.RegistryValueKind]::ExpandString)
        $regKey.Close()
        Write-Ok "Machine PATH updated: added $Dir"
        Broadcast-EnvChange
        $script:InstalledToMachinePath = $true
        return $true
    } catch {
        return $false
    }
}

function Add-ToUserPath([string]$Dir) {
    $regKey = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true)
    if ($null -eq $regKey) {
        $regKey = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Environment')
    }

    $current = $regKey.GetValue('PATH', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
    $entries = $current -split ';' | Where-Object { $_ -ne '' }

    if ($Dir -in $entries) {
        Write-Skip "Directory already in User PATH: $Dir"
        $regKey.Close()
        return
    }

    $newPath = ($current.TrimEnd(';') + ';' + $Dir)
    $regKey.SetValue('PATH', $newPath, [Microsoft.Win32.RegistryValueKind]::ExpandString)
    $regKey.Close()
    Write-Ok "User PATH updated: added $Dir"
    Broadcast-EnvChange
}

# ---------------------------------------------------------------------------
# TLS and HTTP helpers (for status line setup)
# ---------------------------------------------------------------------------

function Get-CurlTlsArgs {
    # Returns curl TLS args: CA cert validation or -k (insecure) fallback
    if ($CACertPath) {
        return @('--cacert', $CACertPath)
    }
    return @('-k')
}

function Invoke-GatewayGet([string]$Url, [string]$OutPath) {
    # Use curl.exe (bundled with Windows 10/11)  --  avoids PowerShell's HttpWebRequest
    # TLS limitations and handles corporate proxy/cert scenarios more reliably.
    $curlArgs = @(
        '--silent', '--show-error', '--fail',
        '--max-time', '30',
        '--header', "Authorization: Bearer $Token"
    ) + (Get-CurlTlsArgs)

    if ($OutPath) {
        $tmpPath = "$OutPath.tmp"
        $curlArgs += @('--output', $tmpPath)
    } else {
        $curlArgs += @('--output', 'NUL')
    }
    $curlArgs += $Url

    & curl.exe @curlArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        if ($OutPath) { Remove-Item "$OutPath.tmp" -Force -ErrorAction SilentlyContinue }
        throw "curl exited with code $LASTEXITCODE for $Url"
    }

    if ($OutPath) {
        $size = (Get-Item $tmpPath).Length
        if ($size -eq 0) {
            Remove-Item $tmpPath -Force -ErrorAction SilentlyContinue
            throw "Downloaded file is empty (0 bytes)."
        }
        if (Test-Path $OutPath -PathType Leaf) { Remove-Item $OutPath -Force }
        Move-Item $tmpPath $OutPath -Force
        Write-Ok "Saved: $OutPath ($size bytes)"
    }
}

function Download-GatewayFile([string]$Endpoint, [string]$OutPath) {
    $url = "$ANTHROPIC_BASE_URL$Endpoint"
    Write-Info "Downloading $url ..."
    Invoke-GatewayGet $url $OutPath
}

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------

function Check-Prerequisites {
    Write-Step 'PRE' 'Checking Prerequisites'

    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Err "winget is not available. Install 'App Installer' from the Microsoft Store (pre-installed on Windows 11)."
        exit 1
    }
    Write-Ok "winget is available."

    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
        Write-Err "curl.exe is not available. It ships with Windows 10 (1803+) and Windows 11."
        Write-Err "Status line setup requires curl.exe  --  install it or update Windows."
        exit 1
    }
    Write-Ok "curl.exe is available."

    # Check for node.js -- also search user-scope install locations that may not be on PATH yet
    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if (-not $nodeCmd) {
        # winget user-scope installs Node under %LOCALAPPDATA%\Programs\nodejs
        $userNodeExe = Join-Path $env:LOCALAPPDATA 'Programs\nodejs\node.exe'
        if (Test-Path $userNodeExe) {
            $nodeCmd = $userNodeExe
            $nodeBinDir = Split-Path $userNodeExe
            if ($env:PATH -notlike "*$nodeBinDir*") {
                $env:PATH = "$nodeBinDir;$env:PATH"
            }
        }
    }

    if (-not $nodeCmd) {
        Write-Info "Node.js not found  --  required for the status line plugin. Installing via winget ..."

        # Detect admin rights to choose scope upfront (avoids a failed machine-scope attempt)
        $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
                       [Security.Principal.WindowsBuiltInRole]::Administrator)

        if ($isAdmin) {
            Write-Info "Admin rights detected  --  installing machine-wide ..."
            winget install --id OpenJS.NodeJS.LTS --silent --accept-package-agreements --accept-source-agreements
            if ($LASTEXITCODE -ne 0) {
                Write-Err "Node.js machine-wide installation failed (exit code $LASTEXITCODE)."
                Write-Err "Install manually from https://nodejs.org/, then re-run this script."
                exit 1
            }
            Write-Ok "Node.js installed (machine-wide)."
        } else {
            Write-Info "No admin rights  --  installing in user scope ..."
            winget install --id OpenJS.NodeJS.LTS --silent --accept-package-agreements --accept-source-agreements --scope user
            if ($LASTEXITCODE -ne 0) {
                Write-Err "Node.js user-scope installation failed (exit code $LASTEXITCODE)."
                Write-Err "Install manually from https://nodejs.org/, then re-run this script."
                exit 1
            }
            Write-Ok "Node.js installed (user scope)."
        }

        # Refresh PATH so node is available in this session
        $env:PATH = [System.Environment]::GetEnvironmentVariable('PATH', 'Machine') + ';' +
                    [System.Environment]::GetEnvironmentVariable('PATH', 'User')

        # Also add winget user-scope path if needed
        $userNodeExe = Join-Path $env:LOCALAPPDATA 'Programs\nodejs\node.exe'
        if ((Test-Path $userNodeExe) -and ($env:PATH -notlike "*$(Split-Path $userNodeExe)*")) {
            $env:PATH = "$(Split-Path $userNodeExe);$env:PATH"
        }
    } else {
        $nodePath = if ($nodeCmd -is [string]) { $nodeCmd } else { $nodeCmd.Source }
        Write-Ok "Node.js is available: $nodePath"
    }

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Write-Info "Git not found  --  Claude Code requires Git for Windows. Installing via winget ..."
        winget install --id Git.Git --silent --accept-package-agreements --accept-source-agreements
        $wingetExitCode = $LASTEXITCODE
        if ($wingetExitCode -ne 0) {
            Write-Info "Machine-wide install failed (exit code $wingetExitCode)  --  retrying with user scope (no admin required) ..."
            winget install --id Git.Git --silent --accept-package-agreements --accept-source-agreements --scope user
            $wingetExitCode = $LASTEXITCODE
        }

        if ($wingetExitCode -ne 0) {
            # winget source may be corrupt (e.g. 0x8a15000f).  Try direct download as fallback.
            Write-Info "winget failed (exit code $wingetExitCode)  --  attempting direct installer download ..."
            $gitInstaller = Join-Path $env:TEMP 'Git-Windows-Setup.exe'
            try {
                $gitApiUrl = 'https://api.github.com/repos/git-for-windows/git/releases/latest'
                $release   = Invoke-RestMethod -Uri $gitApiUrl -UseBasicParsing -ErrorAction Stop
                $asset     = $release.assets | Where-Object { $_.name -match '^Git-[\d\.]+-64-bit\.exe$' } | Select-Object -First 1
                if (-not $asset) { throw "Could not locate 64-bit installer asset in GitHub release." }
                Write-Info "Downloading $($asset.name) ..."
                Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $gitInstaller -UseBasicParsing -ErrorAction Stop
                Write-Info "Running Git installer silently ..."
                Start-Process -FilePath $gitInstaller -ArgumentList '/VERYSILENT', '/NORESTART', '/NOCANCEL', '/SP-', '/CLOSEAPPLICATIONS', '/RESTARTAPPLICATIONS', '/COMPONENTS=icons,ext\reg\shellhere,assoc,assoc_sh' -Wait -ErrorAction Stop
                Remove-Item $gitInstaller -Force -ErrorAction SilentlyContinue
                Write-Ok "Git for Windows installed via direct download."
            } catch {
                Write-Err "Git for Windows installation failed: $_"
                Write-Err "Please install manually from https://git-scm.com/download/win, then re-run this script."
                Write-Err "Alternatively, fix winget by running: winget source reset --force"
                exit 1
            }
        } else {
            Write-Ok "Git for Windows installed via winget."
        }

        # Refresh PATH so git is available for the rest of this session
        $env:PATH = [System.Environment]::GetEnvironmentVariable('PATH', 'Machine') + ';' +
                    [System.Environment]::GetEnvironmentVariable('PATH', 'User')
        # Capture git's cmd dir so Step 3 can add it to the registry PATH
        $gitExe = Get-Command git -ErrorAction SilentlyContinue
        if ($gitExe) {
            $script:GitCmdDir = Split-Path -Parent $gitExe.Source
            Write-Info "Git cmd dir: $($script:GitCmdDir)"
        }
    } else {
        Write-Ok "Git is available: $((Get-Command git).Source)"
    }
}

# ---------------------------------------------------------------------------
# Step 1 - Install Claude Code
# ---------------------------------------------------------------------------

function Step1-InstallClaudeCode {
    Write-Step 1 'Install Claude Code'

    if (Get-Command claude -ErrorAction SilentlyContinue) {
        Write-Skip "Claude Code already installed at: $((Get-Command claude).Source)"
        return
    }

    Write-Info "Installing Claude Code via winget (Anthropic.ClaudeCode) ..."
    winget install --id Anthropic.ClaudeCode --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Claude Code installed successfully via winget."
    } else {
        Write-Err "winget install failed (exit code $LASTEXITCODE)."
        Write-Err "Try manually: winget install Anthropic.ClaudeCode"
        exit 1
    }
}

# ---------------------------------------------------------------------------
# Step 2 - Create the launcher .bat file
# ---------------------------------------------------------------------------

function Step2-CreateBatFile {
    Write-Step 2 'Create the Launcher Script'

    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    $batPath = Join-Path $InstallDir $BAT_FILENAME

    $content = "@echo off`r`n" +
               "`r`n" +
               "set `"NO_PROXY=$NO_PROXY`"`r`n" +
               "set `"ANTHROPIC_BASE_URL=$ANTHROPIC_BASE_URL`"`r`n" +
               "set `"ANTHROPIC_AUTH_TOKEN=$Token`"`r`n" +
               "set `"UID=$UID`"`r`n"

    if ($CACertPath) {
        $content += "set `"NODE_EXTRA_CA_CERTS=$CACertPath`"`r`n"
        Write-Info "Launcher will use NODE_EXTRA_CA_CERTS=$CACertPath"
    } else {
        $content += "set NODE_TLS_REJECT_UNAUTHORIZED=0`r`n"
        Write-Info "Launcher will use NODE_TLS_REJECT_UNAUTHORIZED=0 (no CA cert provided)"
    }

    # Locate bash.exe and set CLAUDE_CODE_GIT_BASH_PATH
    $gitCmd = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCmd) {
        $gitRoot = Split-Path -Parent (Split-Path -Parent $gitCmd.Source)
        $bashExe = Join-Path $gitRoot 'bin\bash.exe'
        if (Test-Path $bashExe -PathType Leaf) {
            $content += "set `"CLAUDE_CODE_GIT_BASH_PATH=$bashExe`"`r`n"
            Write-Info "Launcher will use CLAUDE_CODE_GIT_BASH_PATH=$bashExe"
        } else {
            Write-Info "bash.exe not found at expected path: $bashExe"
        }
    } else {
        Write-Info "git not found on PATH  --  CLAUDE_CODE_GIT_BASH_PATH not set"
    }

    $content += "`r`nclaude %*`r`n"

    [System.IO.File]::WriteAllText($batPath, $content, [System.Text.Encoding]::ASCII)
    Write-Ok "Launcher script created: $batPath"
}

# ---------------------------------------------------------------------------
# Step 3 - Add InstallDir to PATH
# ---------------------------------------------------------------------------

function Step3-AddToPath {
    Write-Step 3 'Add Script Location to PATH'

    $installDirNorm = [System.IO.Path]::GetFullPath($InstallDir)

    # Only skip if claudecode resolves to OUR bat file in InstallDir  --  not some other match
    $batOnPath = Get-Command ($BAT_FILENAME -replace '\.bat$', '') -ErrorAction SilentlyContinue
    if ($batOnPath -and ([System.IO.Path]::GetFullPath($batOnPath.Source) -eq [System.IO.Path]::GetFullPath((Join-Path $installDirNorm $BAT_FILENAME)))) {
        Write-Skip "$BAT_FILENAME is already reachable from PATH: $($batOnPath.Source)"
        return
    }

    if (-not (Add-ToMachinePath $installDirNorm)) {
        Write-Info "No admin rights  --  adding to User PATH instead."
        Add-ToUserPath $installDirNorm
    }

    if ($script:GitCmdDir) {
        Write-Info "Adding Git to PATH: $($script:GitCmdDir)"
        if (-not (Add-ToMachinePath $script:GitCmdDir)) {
            Write-Info "No admin rights  --  adding Git to User PATH instead."
            Add-ToUserPath $script:GitCmdDir
        }
    }
}

# ---------------------------------------------------------------------------
# Steps 4-7 - Status Line Setup (non-blocking)
# ---------------------------------------------------------------------------

$ClaudeDir  = Join-Path $env:USERPROFILE '.claude'
$BudgetDir  = Join-Path $ClaudeDir 'gateway-budget'
$ConfigFile = Join-Path $ClaudeDir 'gateway-budget.json'

function Step4-TestGateway {
    Write-Step 4 'Test Gateway Connection'

    $url = "$ANTHROPIC_BASE_URL/v1/budget/status?user_id=$UID"
    Write-Info "Testing: $url"

    try {
        Invoke-GatewayGet $url $null
        Write-Ok "Gateway connection verified."
    } catch {
        throw "Gateway connection failed: $($_.Exception.Message)"
    }
}

function Step5-DownloadScripts {
    Write-Step 5 'Download Status Line Scripts'

    New-Item -ItemType Directory -Path $BudgetDir -Force | Out-Null

    Download-GatewayFile '/v1/plugin/bootstrap' (Join-Path $BudgetDir 'bootstrap.js')
    Download-GatewayFile '/v1/plugin/extended'  (Join-Path $BudgetDir 'extended.js')
}

function Step6-WriteConfig {
    Write-Step 6 'Write Status Line Configuration'

    New-Item -ItemType Directory -Path $ClaudeDir -Force | Out-Null

    $config = [ordered]@{
        gateway_url        = $ANTHROPIC_BASE_URL
        user_id            = $UID
        api_key            = $Token
        cache_ttl_seconds  = 60
        script_ttl_seconds = 3600
        mode               = 'extended'
    }

    $json = $config | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($ConfigFile, $json, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "Configuration written: $ConfigFile"
}

function Step7-UpdateSettings {
    Write-Step 7 'Update Claude Code Settings'

    $settingsPath = Join-Path $ClaudeDir 'settings.json'
    $bootstrapPath = (Join-Path $BudgetDir 'bootstrap.js') -replace '\\', '/'
    $cmd = "node `"$bootstrapPath`""

    if (Test-Path $settingsPath -PathType Leaf) {
        $raw = [System.IO.File]::ReadAllText($settingsPath, [System.Text.Encoding]::UTF8)
        try {
            $settings = $raw | ConvertFrom-Json
        } catch {
            # Back up the unparseable file instead of silently overwriting
            $backupPath = "$settingsPath.bak.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
            Copy-Item $settingsPath $backupPath -Force
            Write-Info "Existing settings.json is invalid JSON  --  backed up to $backupPath"
            $settings = [PSCustomObject]@{}
        }
    } else {
        New-Item -ItemType Directory -Path $ClaudeDir -Force | Out-Null
        $settings = [PSCustomObject]@{}
    }

    # Check for pre-existing non-installer statusLine  --  preserve it for restore on uninstall
    $ourMarker = 'gateway-budget/bootstrap.js'
    $existingCmd = $null
    $statusLineProp = $settings.PSObject.Properties['statusLine']
    if ($null -ne $statusLineProp) {
        $commandProp = $statusLineProp.Value.PSObject.Properties['command']
        if ($null -ne $commandProp) {
            $existingCmd = $commandProp.Value
        }
    }

    if ($existingCmd -and ($existingCmd -notlike "*$ourMarker*")) {
        # User has a custom status line  --  save it to gateway-budget.json for restore on uninstall
        Write-Info "Existing custom statusLine detected: $existingCmd"
        Write-Info "Saving to gateway-budget.json for restore on uninstall."
        if (Test-Path $ConfigFile -PathType Leaf) {
            try {
                $budgetConfig = [System.IO.File]::ReadAllText($ConfigFile, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
                $budgetConfig | Add-Member -NotePropertyName 'existing_command' -NotePropertyValue $existingCmd -Force
                $budgetJson = $budgetConfig | ConvertTo-Json -Depth 5
                [System.IO.File]::WriteAllText($ConfigFile, $budgetJson, [System.Text.UTF8Encoding]::new($false))
            } catch {
                Write-Info "Could not update gateway-budget.json with existing command: $($_.Exception.Message)"
            }
        }
    }

    $statusLineObj = [PSCustomObject]@{
        type    = 'command'
        command = $cmd
    }
    $settings | Add-Member -NotePropertyName 'statusLine' -NotePropertyValue $statusLineObj -Force

    $json = $settings | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($settingsPath, $json, [System.Text.Encoding]::UTF8)
    Write-Ok "settings.json updated: statusLine.type = command, statusLine.command = $cmd"
}

# Returns profile directories (other than the current user) that already have a .claude folder
function Get-UserProfilesWithClaude {
    $currentProfile = [System.IO.Path]::GetFullPath($env:USERPROFILE)
    $profilesRoot   = [System.IO.Path]::GetFullPath((Split-Path $currentProfile -Parent))

    if (-not (Test-Path $profilesRoot -PathType Container)) { return @() }

    Get-ChildItem $profilesRoot -Directory | Where-Object {
        $p = $_.FullName
        $p -ne $currentProfile -and (Test-Path (Join-Path $p '.claude') -PathType Container)
    } | Select-Object -ExpandProperty FullName
}

function Update-SettingsForProfile([string]$ProfilePath) {
    $userClaudeDir  = Join-Path $ProfilePath '.claude'
    $userBudgetDir  = Join-Path $userClaudeDir 'gateway-budget'
    $userSettings   = Join-Path $userClaudeDir 'settings.json'
    $bootstrapPath  = (Join-Path $userBudgetDir 'bootstrap.js') -replace '\\', '/'
    $cmd            = "node `"$bootstrapPath`""

    # Copy gateway-budget scripts into this user's .claude directory
    try {
        New-Item -ItemType Directory -Path $userBudgetDir -Force | Out-Null
        Copy-Item (Join-Path $BudgetDir 'bootstrap.js') (Join-Path $userBudgetDir 'bootstrap.js') -Force
        Copy-Item (Join-Path $BudgetDir 'extended.js')  (Join-Path $userBudgetDir 'extended.js')  -Force
    } catch {
        Write-Info "  Could not copy scripts to $userBudgetDir`: $($_.Exception.Message)"
        return
    }

    # Copy gateway-budget.json with user_id / api_key
    $userConfigFile = Join-Path $userClaudeDir 'gateway-budget.json'
    if (Test-Path $ConfigFile -PathType Leaf) {
        try { Copy-Item $ConfigFile $userConfigFile -Force } catch { }
    }

    # Merge statusLine into the user's settings.json
    if (Test-Path $userSettings -PathType Leaf) {
        $raw = [System.IO.File]::ReadAllText($userSettings, [System.Text.Encoding]::UTF8)
        try {
            $s = $raw | ConvertFrom-Json
        } catch {
            $bak = "$userSettings.bak.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
            Copy-Item $userSettings $bak -Force
            Write-Info "  Backed up invalid settings.json to $bak"
            $s = [PSCustomObject]@{}
        }
    } else {
        $s = [PSCustomObject]@{}
    }

    $statusLineObj = [PSCustomObject]@{
        type    = 'command'
        command = $cmd
    }
    $s | Add-Member -NotePropertyName 'statusLine' -NotePropertyValue $statusLineObj -Force

    $json = $s | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($userSettings, $json, [System.Text.Encoding]::UTF8)
    Write-Ok "  Updated settings.json for profile: $ProfilePath"
}

function Step7b-UpdateAllUsersSettings {
    Write-Step '7b' 'Update Settings for All Users with .claude (Machine PATH install)'

    $otherProfiles = @(Get-UserProfilesWithClaude)
    if ($otherProfiles.Count -eq 0) {
        Write-Skip "No other user profiles with a .claude directory found."
        return
    }

    foreach ($profile in $otherProfiles) {
        Write-Info "Processing: $profile"
        try {
            Update-SettingsForProfile $profile
        } catch {
            Write-Info "  Skipped $profile`: $($_.Exception.Message)"
        }
    }
}

function Install-StatusLine {
    Write-Host ""
    Write-Host ('=' * 60)
    Write-Host "  Extended Status Line Setup"
    Write-Host ('=' * 60)

    try {
        Step4-TestGateway
        Step5-DownloadScripts
        Step6-WriteConfig
        Step7-UpdateSettings
        if ($script:InstalledToMachinePath) {
            Step7b-UpdateAllUsersSettings
        }
        Write-Host ""
        Write-Ok "Status line configured (extended mode)."
    } catch {
        Write-Host ""
        Write-Host "  [WARN] Status line setup failed: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "  [WARN] Claude Code is installed and works  --  status line can be set up later." -ForegroundColor Yellow
        Write-Host "  [WARN] To retry, run this installer again." -ForegroundColor Yellow
    }
}

# ---------------------------------------------------------------------------
# Steps 8 & 9 - Informational
# ---------------------------------------------------------------------------

function Step8-RemindRestart {
    Write-Step 8 'Restart Your Terminal'
    Write-Info "Close and reopen your PowerShell window so the PATH changes take effect."
}

function Step9-UsageHint {
    Write-Step 9 'Launch Claude Code'
    Write-Info "Start Claude Code with the pre-configured environment by running:"
    Write-Host ""
    Write-Host "    claudecode" -ForegroundColor Yellow
    Write-Host ""
    Write-Info "This launches Claude Code pointed at the corporate LLM gateway."
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

Check-Prerequisites
Step1-InstallClaudeCode
Step2-CreateBatFile
Step3-AddToPath
Install-StatusLine
Step8-RemindRestart
Step9-UsageHint

Write-Host ""
Write-Ok "Installation complete!"
Write-Host ""
