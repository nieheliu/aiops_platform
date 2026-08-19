param(
    [string]$BackendUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

$demoAlerts = @(
    @{
        AlertName = 'HighCpuUsage'
        Severity = 'critical'
        Instance = '192.168.88.136'
        Summary = 'CPU usage above 80% for 5 minutes'
    },
    @{
        AlertName = 'MemoryPressure'
        Severity = 'warning'
        Instance = '192.168.88.137'
        Summary = 'Memory usage above 80% for 10 minutes'
    },
    @{
        AlertName = 'DiskSpaceHigh'
        Severity = 'critical'
        Instance = '192.168.88.139'
        Summary = 'Disk free space below 20% on root filesystem'
    },
    @{
        AlertName = 'HighLoadAverage'
        Severity = 'warning'
        Instance = '192.168.88.136'
        Summary = '5-minute load average is abnormally high'
    },
    @{
        AlertName = 'NetworkReceiveErrors'
        Severity = 'warning'
        Instance = '192.168.88.137'
        Summary = 'Network interface reporting receive errors'
    },
    @{
        AlertName = 'NodeExporterDown'
        Severity = 'critical'
        Instance = '192.168.88.139:9100'
        Summary = 'Node exporter target is unreachable'
    }
)

$alerts = foreach ($item in $demoAlerts) {
    @{
        status = 'firing'
        labels = @{
            alertname = $item.AlertName
            severity = $item.Severity
            instance = $item.Instance
            job = 'node-exporter'
        }
        annotations = @{
            summary = $item.Summary
            description = "Demo alert injected for $($item.AlertName)"
        }
        startsAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        fingerprint = "$($item.AlertName)-$($item.Instance)-$(Get-Date -Format 'yyyyMMddHHmmssfff')"
    }
}

$body = @{
    receiver = 'aiops-webhook'
    status = 'firing'
    alerts = $alerts
} | ConvertTo-Json -Depth 6

$result = Invoke-RestMethod -Uri "$BackendUrl/api/alerts/webhook" -Method POST -ContentType 'application/json' -Body $body
Write-Host "Injected demo alerts: success=$($result.success) saved=$($result.saved) received=$($result.received)"
Write-Host 'Open alert list / ticket list to verify multiple alert types.'
