$status = Get-MpComputerStatus
Write-Host ("Defender real-time: " + $status.RealTimeProtectionEnabled)
$files = @("dist\pigeonhub.exe", "packaging\windows\installer\PigeonHub-Setup-0.20.0-beta.2.exe")
foreach ($f in $files) {
    $resolved = (Resolve-Path $f).Path
    Start-MpScan -ScanType CustomScan -ScanPath $resolved
    Write-Host ("scanned: " + $f)
}
Start-Sleep -Seconds 3
$threats = Get-MpThreatDetection -ErrorAction SilentlyContinue | Where-Object { $_.InitialDetectionTime -gt (Get-Date).AddMinutes(-10) }
Write-Host ("recent_threats: " + ($threats | Measure-Object).Count)
