@echo off
cd /d %~dp0
if not exist monitor.zip (
  echo monitor.zip not found, run pack-monitor.ps1 first
  pause
  exit /b 1
)
echo Serving monitor.zip on port 8888
echo VM download: wget http://YOUR_WINDOWS_IP:8888/monitor.zip
echo Press Ctrl+C to stop
python -m http.server 8888
