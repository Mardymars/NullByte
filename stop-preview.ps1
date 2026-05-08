$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidFile = Join-Path $workspace "preview\\preview-server.pid"

if (-not (Test-Path $pidFile)) {
    Write-Output "No preview PID file found."
    exit 0
}

$pidValue = Get-Content $pidFile | Select-Object -First 1
if ($pidValue) {
    Stop-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
}

Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
Write-Output "Preview server stopped."
