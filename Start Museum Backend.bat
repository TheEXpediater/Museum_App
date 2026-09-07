@echo off
setlocal enabledelayedexpansion
title Museum Backend

REM This file can be copied to the Desktop (or targeted by a Desktop shortcut).
REM It only locates the already-installed Museum_App project and runs its
REM existing start_backend.py - it does not set up, migrate, or configure anything.

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "PROJECT_DIR="

REM 1) This file is sitting inside the already-set-up project (e.g. run from
REM    Museum_App itself, or a shortcut with "Start in" pointed at it).
if exist "%SCRIPT_DIR%\start_backend.py" set "PROJECT_DIR=%SCRIPT_DIR%"

REM 2) This file was copied to the Desktop: fall back to the known install location.
if not defined PROJECT_DIR (
    if exist "C:\Capstone-client\Museum_App\start_backend.py" set "PROJECT_DIR=C:\Capstone-client\Museum_App"
)

REM 3) A couple of other common locations, just in case.
if not defined PROJECT_DIR (
    if exist "%USERPROFILE%\Desktop\Museum_App\start_backend.py" set "PROJECT_DIR=%USERPROFILE%\Desktop\Museum_App"
)
if not defined PROJECT_DIR (
    if exist "C:\Museum_App\start_backend.py" set "PROJECT_DIR=C:\Museum_App"
)

if not defined PROJECT_DIR (
    echo ========================================
    echo  MUSEUM BACKEND - CANNOT START
    echo ========================================
    echo.
    echo [ERROR] Could not find the Museum_App project folder.
    echo.
    echo Open this file in Notepad and edit the line under "set PROJECT_DIR="
    echo to point at the Museum_App folder on this computer, for example:
    echo     set "PROJECT_DIR=C:\Capstone-client\Museum_App"
    echo.
    pause
    exit /b 1
)

cd /d "%PROJECT_DIR%"

echo ========================================
echo  Museum Backend Starting...
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
        echo This computer's backend was set up with Python already installed -
        echo if this message appears, the original setup may have been changed.
        echo.
        pause
        exit /b 1
    )
)

if not exist "%PROJECT_DIR%\backend\.env" (
    echo [ERROR] backend\.env was not found in:
    echo   %PROJECT_DIR%
    echo This backend does not look like it has been set up on this computer yet.
    echo.
    pause
    exit /b 1
)

echo Backend is running.
echo Keep this window open while using the Museum App.
echo Press CTRL+C to stop. MongoDB and Qdrant data are preserved when you stop.
echo.

%PYLAUNCH% start_backend.py
set "EXIT_CODE=%errorlevel%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo ========================================
    echo  MUSEUM BACKEND FAILED TO START
    echo ========================================
    echo Exit code: %EXIT_CODE%
    echo Review the messages above for details.
    echo.
)

pause
