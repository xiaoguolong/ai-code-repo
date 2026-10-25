param(
    [Parameter(Mandatory = $true)][string]$Node,
    [Parameter(Mandatory = $true)][string]$Pnpm,
    [switch]$SkipInstall,
    [switch]$Smoke,
    [string]$WebBase = 'http://127.0.0.1:5173',
    [string]$LogDir = 'logs\week-30\ci'
)

# Week 30 frontend gate: install -> typecheck -> test -> build (optional Playwright smoke).
# Example:
#   .\tools\web-ci.ps1 -Node <node.exe> -Pnpm <pnpm.mjs>
#   .\tools\web-ci.ps1 -Node <node.exe> -Pnpm <pnpm.mjs> -Smoke -WebBase http://127.0.0.1:18080

$ErrorActionPreference = 'Stop'
$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Web = Join-Path $Repo 'apps\web'
$LogDirAbs = Join-Path $Repo $LogDir
New-Item -ItemType Directory -Force -Path $LogDirAbs | Out-Null
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$Log = Join-Path $LogDirAbs "web-ci-$Stamp.log"

function Invoke-Step {
    param([string]$Name, [scriptblock]$Action)
    $start = Get-Date
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    # pnpm writes a "$ <script>" banner to stderr; do not treat that as a terminating error.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # Force stderr records to plain strings so PowerShell 5.1 does not surface
        # pnpm's "$ <script>" banner as NativeCommandError to the caller.
        $lines = & $Action 2>&1 | ForEach-Object { "$_" }
        $lines | Tee-Object -FilePath $Log -Append | Out-Host
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
        throw "Step failed: $Name (exit=$LASTEXITCODE)"
    }
    $seconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    Write-Host "--- $Name OK (${seconds}s)" -ForegroundColor Green
}

if (-not (Test-Path $Node)) { throw "Node not found: $Node" }
if (-not (Test-Path $Pnpm)) { throw "pnpm not found: $Pnpm" }
if (-not (Test-Path $Web)) { throw "web dir not found: $Web" }

# pnpm spawns "node" by name; make the provided runtime discoverable for the whole run.
$env:PATH = "$(Split-Path -Parent $Node);$env:PATH"

Push-Location $Web
try {
    if (-not $SkipInstall) {
        Invoke-Step 'install' { & $Node $Pnpm install --config.strict-dep-builds=false }
    } else {
        Write-Host 'skip install (-SkipInstall)' -ForegroundColor Yellow
    }
    Invoke-Step 'typecheck' { & $Node $Pnpm run typecheck }
    Invoke-Step 'test' { & $Node $Pnpm run test }
    Invoke-Step 'build' { & $Node $Pnpm run build }
    if ($Smoke) {
        $env:WEB_BASE = $WebBase
        $env:LOG_DIR = Join-Path $LogDirAbs 'smoke'
        Invoke-Step 'smoke' { & $Node 'scripts\smoke.mjs' }
    }
    Write-Host "ALL GREEN (log: $Log)" -ForegroundColor Green
} finally {
    Pop-Location
}
