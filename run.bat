@echo off
setlocal enabledelayedexpansion
title Museum Backend
cd /d "%~dp0"

where py >nul 2>nul
if %errorlevel%==0 (
    set "PYLAUNCH=py"
) else (
    where python >nul 2>nul
    if !errorlevel!==0 (
        set "PYLAUNCH=python"
    ) else (
        echo [ERROR] Python was not found on PATH.
        echo Run setup.bat first.
        echo.
        pause
        exit /b 1
    )
)

if not exist "backend\.env" (
    echo [ERROR] backend\.env was not found. Run setup.bat first.
    echo.
    pause
    exit /b 1
)

echo Starting the museum backend. This window must stay open while the app is in use.
echo Press CTRL+C to stop. MongoDB and Qdrant data are preserved when you stop.
echo.

%PYLAUNCH% start_backend.py

echo.
pause
