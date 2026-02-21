# ============================================================
#  SmartParking — Windows Build & Package Script
#  Produces: SmartParking-1.0.exe (self-contained installer)
#
#  Requirements:
#    - JDK 17+ installed (https://adoptium.net)
#    - WiX Toolset 3.x installed for .exe (https://github.com/wixtoolset/wix3/releases)
#      OR use --type app-image below to skip WiX (produces a folder, not installer)
#
#  Usage: Right-click this file → "Run with PowerShell"
# ============================================================

$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$SrcDir      = Join-Path $ProjectRoot "src"
$OutDir      = Join-Path $ProjectRoot "out"
$JarFile     = Join-Path $ProjectRoot "SmartParking.jar"
$PackageDir  = Join-Path $ProjectRoot "package-output"
$AppName     = "SmartParking"
$AppVersion  = "1.0"
$MainClass   = "src.ui.MainFrame"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Smart Parking — Build & Package" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

# ---- Step 1: Check JDK ----
Write-Host "`n[1/4] Checking JDK..." -ForegroundColor Yellow
$javac = & where.exe javac 2>$null
if (-not $javac) {
    Write-Host "ERROR: javac not found. Install JDK 17+ from https://adoptium.net" -ForegroundColor Red
    Write-Host "Make sure JDK bin folder is in your PATH." -ForegroundColor Red
    pause; exit 1
}
$javaVersion = & javac -version 2>&1
Write-Host "Found: $javaVersion" -ForegroundColor Green

# ---- Step 2: Compile ----
Write-Host "`n[2/4] Compiling source files..." -ForegroundColor Yellow
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutDir | Out-Null

$javaFiles = Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
if ($javaFiles.Count -eq 0) {
    Write-Host "ERROR: No .java files found in $SrcDir" -ForegroundColor Red
    pause; exit 1
}

# Write file list to temp file (avoids command-line length limits)
$fileList = Join-Path $env:TEMP "smartparking_sources.txt"
$javaFiles | Set-Content $fileList

& javac --release 17 -d $OutDir "@$fileList"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Compilation failed." -ForegroundColor Red
    pause; exit 1
}
Write-Host "Compiled $($javaFiles.Count) files successfully." -ForegroundColor Green

# ---- Step 3: Create JAR ----
Write-Host "`n[3/4] Creating JAR..." -ForegroundColor Yellow

# Write manifest
$ManifestFile = Join-Path $env:TEMP "MANIFEST.MF"
@"
Manifest-Version: 1.0
Main-Class: $MainClass

"@ | Set-Content $ManifestFile -Encoding ASCII

& jar cfm $JarFile $ManifestFile -C $OutDir .
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: JAR creation failed." -ForegroundColor Red
    pause; exit 1
}
Write-Host "JAR created: $JarFile" -ForegroundColor Green

# ---- Step 4: Package with jpackage ----
Write-Host "`n[4/4] Packaging with jpackage..." -ForegroundColor Yellow
if (Test-Path $PackageDir) { Remove-Item $PackageDir -Recurse -Force }
New-Item -ItemType Directory -Path $PackageDir | Out-Null

# Try .exe first (needs WiX), fall back to app-image folder
$packageType = "exe"
$wix = Get-Command candle.exe -ErrorAction SilentlyContinue
if (-not $wix) {
    Write-Host "WiX not found — building app-image folder instead of .exe installer." -ForegroundColor DarkYellow
    Write-Host "To get a proper .exe, install WiX 3.x: https://github.com/wixtoolset/wix3/releases" -ForegroundColor DarkYellow
    $packageType = "app-image"
}

& jpackage `
    --input        $ProjectRoot `
    --main-jar     "SmartParking.jar" `
    --main-class   $MainClass `
    --name         $AppName `
    --app-version  $AppVersion `
    --description  "AI-Powered Smart Parking & Route Planning Visualizer" `
    --vendor       "SmartParking Dev" `
    --dest         $PackageDir `
    --type         $packageType `
    --win-shortcut `
    --win-menu `
    --win-menu-group "SmartParking" `
    --java-options "-Xmx512m"

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: jpackage failed." -ForegroundColor Red
    pause; exit 1
}

Write-Host "`n======================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
if ($packageType -eq "exe") {
    Write-Host "Installer: $PackageDir\$AppName-$AppVersion.exe" -ForegroundColor Cyan
    Write-Host "Share this .exe — no Java install needed on target machine." -ForegroundColor Cyan
} else {
    Write-Host "App folder: $PackageDir\$AppName\" -ForegroundColor Cyan
    Write-Host "Run:        $PackageDir\$AppName\$AppName.exe" -ForegroundColor Cyan
    Write-Host "(Install WiX to produce a single .exe installer next time)" -ForegroundColor DarkYellow
}

pause
