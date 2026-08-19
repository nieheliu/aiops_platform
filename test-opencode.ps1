param([string]$Model = "deepseek-v4-flash")

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "local.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            Set-Item -Path "env:$($matches[1].Trim())" -Value $matches[2].Trim()
        }
    }
}
if (-not $env:ARK_API_KEY) {
    Write-Host "local.env 中未找到 ARK_API_KEY"
    exit 1
}
$headers = @{
    Authorization = "Bearer $env:ARK_API_KEY"
    "Content-Type" = "application/json"
}
$body = "{`"model`":`"$Model`",`"messages`":[{`"role`":`"user`",`"content`":`"ping`"}]}"
Write-Host "Testing model: $Model"
try {
    $r = Invoke-WebRequest -Uri "https://opencode.ai/zen/v1/chat/completions" -Method POST -Headers $headers -Body $body -UseBasicParsing
    Write-Host "STATUS: $($r.StatusCode)"
    Write-Host $r.Content.Substring(0, [Math]::Min(300, $r.Content.Length))
} catch {
    Write-Host "FAILED: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd()
    }
}
