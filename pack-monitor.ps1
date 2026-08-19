$ErrorActionPreference = "Stop"
$root = Join-Path $PSScriptRoot "monitor"
$zip = Join-Path $PSScriptRoot "monitor.zip"

if (-not (Test-Path $root)) {
    Write-Host "monitor folder not found"
    exit 1
}

powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "setup-monitoring.ps1") | Out-Null

if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $root -DestinationPath $zip -Force
Write-Host "Created: $zip"
Write-Host "Size: $([math]::Round((Get-Item $zip).Length / 1KB, 1)) KB"
