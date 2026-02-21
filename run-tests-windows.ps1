# ============================================================
#  SmartParking — Download JUnit 5 & Run Tests
#
#  Run this ONCE to download JUnit, then anytime to run tests.
#  Usage: Right-click → Run with PowerShell
# ============================================================

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$LibDir  = Join-Path $Root "lib"
$TestDir = Join-Path $Root "test"
$SrcDir  = Join-Path $Root "src"
$OutDir  = Join-Path $Root "out"
$TestOut = Join-Path $Root "test-out"

# ---- Download JUnit 5 if not already present ----
$standaloneJar = Join-Path $LibDir "junit-platform-console-standalone.jar"
if (-not (Test-Path $standaloneJar)) {
    Write-Host "[1/4] Downloading JUnit 5 standalone runner..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $LibDir -Force | Out-Null

    $url = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar"
    Invoke-WebRequest -Uri $url -OutFile $standaloneJar
    Write-Host "Downloaded JUnit runner." -ForegroundColor Green
} else {
    Write-Host "[1/4] JUnit already downloaded." -ForegroundColor Green
}

# ---- Compile main sources ----
Write-Host "`n[2/4] Compiling main sources..." -ForegroundColor Yellow
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutDir | Out-Null

$mainFiles = Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
$mainFiles | Set-Content (Join-Path $env:TEMP "main_sources.txt")
& javac --release 17 -d $OutDir "@$(Join-Path $env:TEMP 'main_sources.txt')"
if ($LASTEXITCODE -ne 0) { Write-Host "Compile failed!" -ForegroundColor Red; pause; exit 1 }
Write-Host "Main sources compiled." -ForegroundColor Green

# ---- Compile test sources ----
Write-Host "`n[3/4] Compiling test sources..." -ForegroundColor Yellow
if (Test-Path $TestOut) { Remove-Item $TestOut -Recurse -Force }
New-Item -ItemType Directory -Path $TestOut | Out-Null

$testFiles = Get-ChildItem -Path $TestDir -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
$testFiles | Set-Content (Join-Path $env:TEMP "test_sources.txt")

& javac --release 17 `
    -cp "$OutDir;$standaloneJar" `
    -d $TestOut `
    "@$(Join-Path $env:TEMP 'test_sources.txt')"
if ($LASTEXITCODE -ne 0) { Write-Host "Test compile failed!" -ForegroundColor Red; pause; exit 1 }
Write-Host "Test sources compiled." -ForegroundColor Green

# ---- Run tests ----
Write-Host "`n[4/4] Running tests..." -ForegroundColor Yellow
Write-Host "======================================" -ForegroundColor Cyan

& java -jar $standaloneJar `
    --class-path "$OutDir;$TestOut" `
    --scan-class-path `
    --details=tree

Write-Host "======================================" -ForegroundColor Cyan
if ($LASTEXITCODE -eq 0) {
    Write-Host "All tests PASSED!" -ForegroundColor Green
} else {
    Write-Host "Some tests FAILED — see output above." -ForegroundColor Red
}

pause
