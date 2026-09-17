# Builds the MVP-018 Windows distribution:
#   packaging/windows/dist/pigeonhub.exe        (PyInstaller onefile)
#   packaging/windows/installer/PigeonHub-Setup-<version>.exe  (Inno Setup)
#   dist/SHA256SUMS.txt
# Version is single-sourced from pigeonhub/__init__.py.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$winDir = Join-Path $repoRoot "packaging\windows"

# 1. Version (single source of truth)
$version = (& python -c "import sys; sys.path.insert(0, r'$repoRoot'); import pigeonhub; print(pigeonhub.__version__)").Trim()
if (-not $version) { throw "could not read version from pigeonhub/__init__.py" }
Write-Host "== PigeonHub CLI $version =="

# 2. Build venv (PyInstaller); reused across builds
$venv = Join-Path $env:TEMP "ph-build-venv"
if (-not (Test-Path "$venv\Scripts\python.exe")) {
    python -m venv $venv
    & "$venv\Scripts\python.exe" -m pip install --quiet --upgrade pip
}
& "$venv\Scripts\python.exe" -m pip install --quiet pyinstaller $repoRoot
& "$venv\Scripts\python.exe" -m PyInstaller --onefile --name pigeonhub --clean `
    --distpath (Join-Path $winDir "dist") --workpath (Join-Path $env:TEMP "ph-pyi-work") `
    --specpath $winDir (Join-Path $winDir "pigeonhub_entry.py")
if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed" }

# 3. Installer (Inno Setup user-scope install)
$iscc = Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) { throw "ISCC.exe not found; install Inno Setup 6 (winget install JRSoftware.InnoSetup)" }
& $iscc /DAPP_VERSION=$version (Join-Path $winDir "installer.iss")
if ($LASTEXITCODE -ne 0) { throw "ISCC failed" }

# 4. Distribution layout + checksums
$dist = Join-Path $repoRoot "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item (Join-Path $winDir "dist\pigeonhub.exe") $dist -Force
Copy-Item (Join-Path $winDir "installer\PigeonHub-Setup-$version.exe") $dist -Force
Push-Location $dist
try {
    $sums = Get-ChildItem -File | Where-Object { $_.Name -ne "SHA256SUMS.txt" } | ForEach-Object {
        $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $($_.Name)"
    }
    $sums -join "`n" | Set-Content -NoNewline -Encoding ascii "SHA256SUMS.txt"
} finally {
    Pop-Location
}
Get-ChildItem $dist | Format-Table Name, Length -AutoSize
Write-Host "done."
