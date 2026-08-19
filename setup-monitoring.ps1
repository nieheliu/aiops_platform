$ErrorActionPreference = "Stop"
$monitorDir = Join-Path $PSScriptRoot "monitor"

if (-not (Test-Path $monitorDir)) {
    Write-Host "monitor directory not found"
    exit 1
}

$envFile = Join-Path $monitorDir "env"
$envExample = Join-Path $monitorDir "env.example"
if (-not (Test-Path $envFile)) {
    Copy-Item $envExample $envFile
    Write-Host "Created monitor/env - please verify BACKEND_WEBHOOK_URL"
}

$envMap = @{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        $envMap[$matches[1].Trim()] = $matches[2].Trim()
    }
}

if (-not $envMap["BACKEND_WEBHOOK_URL"] -or $envMap["BACKEND_WEBHOOK_URL"] -like "*192.168.88.1*") {
    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
        $_.IPAddress -like "192.168.88.*" -and $_.PrefixOrigin -ne "WellKnown"
    } | Select-Object -First 1).IPAddress
    if ($lanIp) {
        $envMap["BACKEND_WEBHOOK_URL"] = "http://${lanIp}:8080/api/alerts/webhook"
        ($envMap.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) | Set-Content $envFile
        Write-Host "Set BACKEND_WEBHOOK_URL = $($envMap['BACKEND_WEBHOOK_URL'])"
    }
}

$template = Get-Content (Join-Path $monitorDir "alertmanager\alertmanager.yml.template") -Raw
$generated = $template.Replace("__BACKEND_WEBHOOK_URL__", $envMap["BACKEND_WEBHOOK_URL"])
Set-Content -Path (Join-Path $monitorDir "alertmanager\alertmanager.yml") -Value $generated -Encoding UTF8

Write-Host ""
Write-Host "VM 136 (~2.3GB free): Prometheus + Alertmanager + Grafana + Node Exporter"
Write-Host "VM 137 (~1.8GB free): Node Exporter only"
Write-Host "VM 139: Node Exporter only"
Write-Host ""
Write-Host "On VM 136: copy monitor/ to /opt/aiops-monitor, run: docker compose up -d"
Write-Host "On VM 137/139: copy node-exporter-compose.yml, run: docker compose -f node-exporter-compose.yml up -d"
Write-Host ""
Write-Host "Prometheus:   http://192.168.88.136:9090"
Write-Host "Alertmanager: http://192.168.88.136:9093"
Write-Host "Grafana:      http://192.168.88.136:3000 (admin / admin123)"
Write-Host "Webhook:      $($envMap['BACKEND_WEBHOOK_URL'])"
Write-Host "Allow Windows inbound TCP 8080 for VM 136 webhook calls."
