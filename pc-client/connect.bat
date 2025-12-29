@echo off
setlocal enabledelayedexpansion

:: Remote ADB Connect for Windows
:: Usage: connect.bat DEVICE_ID [port]

set DEVICE=%1
set PORT=%2
if "%PORT%"=="" set PORT=5555
set HOSTNAME=%DEVICE%.676967.xyz

echo.
echo  ========================================
echo       Remote ADB Connect v1.0
echo  ========================================
echo.

if "%DEVICE%"=="" (
    echo Usage: connect.bat YOUR_DEVICE_ID [port]
    echo.
    echo Get your device ID from the Remote ADB app on your phone.
    exit /b 1
)

echo Connecting to %HOSTNAME%...
echo.

:: Check if cloudflared exists
where cloudflared >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo cloudflared not found. Downloading...
    
    :: Download cloudflared
    curl -sL -o cloudflared.exe https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
    
    if not exist cloudflared.exe (
        echo Failed to download cloudflared.
        echo Please download manually from:
        echo https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
        exit /b 1
    )
    
    echo cloudflared downloaded!
    set CLOUDFLARED=cloudflared.exe
) else (
    set CLOUDFLARED=cloudflared
)

echo.
echo Starting tunnel to %HOSTNAME% on local port %PORT%...
echo.
echo In another terminal run:
echo   adb connect localhost:%PORT%
echo.
echo Press Ctrl+C to disconnect
echo.

%CLOUDFLARED% access tcp --hostname %HOSTNAME% --url localhost:%PORT%
