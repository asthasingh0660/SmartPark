@echo off
:: ============================================================
::  SmartParking — Quick JAR Build (no installer, just runs)
::  Double-click this to compile + launch the app.
::  Requires JDK 17+ in PATH.
:: ============================================================

echo ======================================
echo   Smart Parking — Quick Build ^& Run
echo ======================================

:: Check for javac
where javac >nul 2>&1
if errorlevel 1 (
    echo.
    echo ERROR: javac not found!
    echo Install JDK 17+ from https://adoptium.net
    echo Then add the JDK bin folder to your PATH.
    pause
    exit /b 1
)

echo.
echo [1/3] Compiling...
if exist out rmdir /s /q out
mkdir out

:: Collect all .java files
dir /s /b src\*.java > sources.txt
javac --release 17 -d out @sources.txt
if errorlevel 1 (
    echo ERROR: Compilation failed. See errors above.
    del sources.txt
    pause
    exit /b 1
)
del sources.txt

echo [2/3] Building JAR...
echo Main-Class: src.ui.MainFrame> MANIFEST.MF
echo.>> MANIFEST.MF
jar cfm SmartParking.jar MANIFEST.MF -C out .
del MANIFEST.MF
if errorlevel 1 (
    echo ERROR: JAR creation failed.
    pause
    exit /b 1
)

echo [3/3] Launching Smart Parking...
echo.
start javaw -jar SmartParking.jar

echo Done! The app should open shortly.
echo (If nothing opens, run: java -jar SmartParking.jar)
timeout /t 3
