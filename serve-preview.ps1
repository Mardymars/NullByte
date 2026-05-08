$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$previewDir = Join-Path $workspace "preview"
$port = 4173
$pidFile = Join-Path $previewDir "preview-server.pid"
$stdoutLog = Join-Path $previewDir "preview-server.log"
$stderrLog = Join-Path $previewDir "preview-server.err.log"
$pythonExe = (Get-Command python).Source

if (-not (Test-Path $previewDir)) {
    throw "Preview directory not found at $previewDir"
}

$existing = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty OwningProcess

if ($existing) {
    Set-Content -Path $pidFile -Value $existing
    Write-Output "Preview already running at http://127.0.0.1:$port/"
    exit 0
}

$process = Start-Process -FilePath $pythonExe `
    -ArgumentList @("-m", "http.server", "$port", "--bind", "127.0.0.1") `
    -WorkingDirectory $previewDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

Set-Content -Path $pidFile -Value $process.Id
Write-Output "Preview started at http://127.0.0.1:$port/"
