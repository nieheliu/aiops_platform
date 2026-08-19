# Allow VM 136 Alertmanager to call Windows backend webhook on port 8080
netsh advfirewall firewall add rule name="AIOps Backend 8080" dir=in action=allow protocol=TCP localport=8080
Write-Host "Firewall rule added for TCP 8080 inbound"
