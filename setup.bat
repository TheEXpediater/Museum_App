@echo off
setlocal enabledelayedexpansion
title Museum System - Setup
cd /d "%~dp0"

echo ========================================
echo  MUSEUM SYSTEM - SETUP
echo ========================================
echo.

where py >nul 2>nul
if %errorlevel%==0 (
    set "PYLAUNCH=py"
) else (
    where python >nul 2>nul
    if !errorlevel!==0 (
        set "PYLAUNCH=python"
    ) else (
        echo [ERROR] Python was not found on PATH.
        echo Install Python 3.12 or 3.13 from https://www.python.org/downloads/ and re-run setup.bat.
        echo.
        pause
        exit /b 1
    )
)

where docker >nul 2>nul
if not %errorlevel%==0 (
    echo [ERROR] Docker was not found on PATH.
    echo Install Docker Desktop from https://www.docker.com/products/docker-desktop/ and re-run setup.bat.
    echo.
    pause
    exit /b 1
)

docker info >nul 2>nul
if not %errorlevel%==0 (
    echo [ERROR] Docker is installed but not running.
    echo Start Docker Desktop, wait for it to finish starting, then re-run setup.bat.
    echo.
    pause
    exit /b 1
)

docker volume inspect museum_app_museum_mongodb_data >nul 2>nul
if not %errorlevel%==0 (
    if exist "migration\mongodb-backup" (
        echo [INFO] No existing museum database was found on this computer, but a migration
        echo        backup exists in migration\mongodb-backup.
        echo.
        echo        If this project folder was copied from another computer, run this first:
        echo            powershell -ExecutionPolicy Bypass -File migration\restore.ps1
        echo.
        echo        Continuing with a normal first-time setup instead...
        echo.
    )
)

echo [INFO] Running backend setup. This installs dependencies and can take several minutes
echo        the first time, especially the AI model download.
echo.
%PYLAUNCH% start_backend.py --setup
set SETUP_RESULT=%errorlevel%

echo.
if %SETUP_RESULT%==0 (
    echo ========================================
    echo  SETUP COMPLETE
    echo ========================================
    echo Next: double-click run.bat to start the museum backend.
) else (
    echo ========================================
    echo  SETUP FINISHED WITH ISSUES
    echo ========================================
    echo Review the messages above, fix the reported issue, then run setup.bat again.
    echo Setup is safe to run again - it will not delete existing data.
)

echo.
pause
exit /b %SETUP_RESULT%
