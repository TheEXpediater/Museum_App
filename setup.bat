@echo off
setlocal enabledelayedexpansion
title Museum Guide System - Setup

REM Always operate relative to this script's own location, never the CMD working directory,
REM and never a hardcoded development path.
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
cd /d "%ROOT_DIR%"

set "COMPOSE_FILE=%ROOT_DIR%\compose.yaml"
set "VENV_PY=%ROOT_DIR%\backend\.venv\Scripts\python.exe"
set "APK_PATH=%ROOT_DIR%\Museum_App.apk"

echo ========================================
echo  Museum Guide System - First Time Setup
echo ========================================
echo.

if not "%OS%"=="Windows_NT" (
    echo [ERROR] This setup script requires Windows.
    pause
    exit /b 1
)

set "PY_OK=0"
set "DOCKER_OK=0"
set "MIGRATION_READY=0"
set "RESTORE_DONE=0"
set "SETUP_RESULT=1"

REM ============================================================
echo [1/8] Checking Python...
REM ============================================================
call :find_python
if defined PYLAUNCH (
    echo [OK] Using Python %PYVER% via: %PYLAUNCH%
    set "PY_OK=1"
) else (
    echo [ERROR] Python 3.12 or 3.13 could not be found or installed automatically.
    echo         Install it manually from https://www.python.org/downloads/ ^(choose 3.12 or 3.13^),
    echo         then re-run setup.bat. Backend preparation will be skipped this run.
)
echo.

REM ============================================================
echo [2/8] Checking Docker...
REM ============================================================
call :ensure_docker
if "%DOCKER_OK%"=="1" (
    echo [OK] Docker Desktop is installed and the engine is running.
) else (
    echo [ERROR] Docker Desktop is not installed, not running, or Compose is unavailable.
    echo         Start Docker Desktop manually, wait until it says "Docker Desktop is running",
    echo         then re-run setup.bat. MongoDB/Qdrant/migration restore will be skipped this run.
)
echo.

REM ============================================================
echo [3/8] Checking migration backup files...
REM ============================================================
call :check_migration_files
echo.

REM ============================================================
echo [4/8] Restoring MongoDB and Qdrant Docker volumes...
echo [5/8] Restoring OpenCLIP model cache and backend uploads...
REM ============================================================
call :restore_migration
echo.

REM ============================================================
echo [6/8] Freeing port 8000 for the native backend...
REM ============================================================
call :free_port_8000
echo.

REM ============================================================
echo [7/8] Preparing backend ^(Python environment, AI setup, admin account^)...
REM ============================================================
if "%PY_OK%"=="1" (
    %PYLAUNCH% start_backend.py --setup
    set "SETUP_RESULT=!errorlevel!"
) else (
    echo [WARN] Skipped: Python 3.12/3.13 is not available.
    set "SETUP_RESULT=1"
)
echo.

REM ============================================================
echo [8/8] Running final verification...
REM ============================================================
call :final_verification

echo.
if "%SETUP_RESULT%"=="0" (
    echo ========================================
    echo  SETUP COMPLETE
    echo ========================================
) else (
    echo ========================================
    echo  SETUP FINISHED WITH ISSUES
    echo ========================================
    echo Review the messages above. Setup is safe to run again - it will not delete
    echo existing MongoDB, Qdrant, or image data.
)
echo.
echo Next steps:
echo   1. Install the app on the Android phone: %APK_PATH%
echo      ^(Android may ask to allow installing from unknown sources - allow it.^)
echo   2. Double-click run.bat to start the museum backend.
echo   3. Connect the Android phone and this laptop to the same Wi-Fi or hotspot.
echo   4. Open the app on the phone.
echo.
pause
exit /b %SETUP_RESULT%

REM ============================================================
REM Subroutines
REM ============================================================

:find_python
set "PYLAUNCH="
set "PYVER="
set "PREFERRED_FOUND=0"

REM Prefer 3.13/3.12 (matches the pinned AI dependencies) whenever available, regardless of
REM whether a venv already exists.
where py >nul 2>nul
if %errorlevel%==0 (
    for %%V in (3.13 3.12) do (
        if not defined PYLAUNCH (
            py -%%V --version >nul 2>nul
            if not errorlevel 1 (
                set "PYLAUNCH=py -%%V"
                set "PYVER=%%V"
                set "PREFERRED_FOUND=1"
            )
        )
    )
)

if not defined PYLAUNCH (
    where python >nul 2>nul
    if %errorlevel%==0 (
        for /f "tokens=2 delims= " %%v in ('python --version 2^>^&1') do set "RAWVER=%%v"
        if defined RAWVER (
            echo !RAWVER!| findstr /b "3.13" >nul
            if not errorlevel 1 (
                set "PYLAUNCH=python"
                set "PYVER=3.13"
                set "PREFERRED_FOUND=1"
            )
        )
        if not defined PYLAUNCH if defined RAWVER (
            echo !RAWVER!| findstr /b "3.12" >nul
            if not errorlevel 1 (
                set "PYLAUNCH=python"
                set "PYVER=3.12"
                set "PREFERRED_FOUND=1"
            )
        )
    )
)

if "%PREFERRED_FOUND%"=="1" goto :eof

REM No 3.12/3.13 found yet. If a backend virtual environment already exists, it was already
REM built with a working interpreter (start_backend.py will not recreate it) - reuse whatever
REM system launcher is available to run start_backend.py itself instead of forcing a reinstall.
if exist "%VENV_PY%" (
    if not defined PYLAUNCH (
        where py >nul 2>nul
        if %errorlevel%==0 (
            set "PYLAUNCH=py"
            for /f "tokens=2 delims= " %%v in ('py --version 2^>^&1') do set "PYVER=%%v"
        )
    )
    if not defined PYLAUNCH (
        where python >nul 2>nul
        if %errorlevel%==0 (
            set "PYLAUNCH=python"
            for /f "tokens=2 delims= " %%v in ('python --version 2^>^&1') do set "PYVER=%%v"
        )
    )
    if defined PYLAUNCH (
        echo [INFO] backend\.venv already exists; reusing it instead of installing a new Python.
        goto :eof
    )
)

REM Fresh install with no compatible Python and no existing venv: attempt an unattended install.
echo [INFO] Compatible Python ^(3.12 or 3.13^) was not found on PATH.
where winget >nul 2>nul
if not %errorlevel%==0 (
    echo [WARN] winget is not available on this computer. Automatic install was skipped.
    goto :eof
)

echo [INFO] Attempting unattended install of Python 3.13 via winget. This can take several minutes...
winget install --id Python.Python.3.13 -e --source winget --silent --accept-package-agreements --accept-source-agreements >nul 2>nul

py -3.13 --version >nul 2>nul
if not errorlevel 1 (
    set "PYLAUNCH=py -3.13"
    set "PYVER=3.13"
    goto :eof
)

for %%P in (
    "%LocalAppData%\Programs\Python\Python313\python.exe"
    "%ProgramFiles%\Python313\python.exe"
) do (
    if not defined PYLAUNCH (
        if exist %%P (
            set "PYLAUNCH="%%~P""
            set "PYVER=3.13"
        )
    )
)

REM Last resort: any working system Python, even if not 3.12/3.13. start_backend.py's own
REM AI-compatibility check will detect and report incompatibility gracefully rather than
REM setup.bat blocking the whole run.
if not defined PYLAUNCH (
    where python >nul 2>nul
    if %errorlevel%==0 (
        set "PYLAUNCH=python"
        for /f "tokens=2 delims= " %%v in ('python --version 2^>^&1') do set "PYVER=%%v"
        echo [WARN] Falling back to the available Python !PYVER!. AI dependency installation may be skipped
        echo        if it is incompatible; core backend features will still work.
    )
)
goto :eof

:ensure_docker
where docker >nul 2>nul
if not %errorlevel%==0 (
    echo [INFO] Docker Desktop was not found on PATH.
    where winget >nul 2>nul
    if %errorlevel%==0 (
        echo [INFO] Attempting unattended install of Docker Desktop via winget. This can take several minutes...
        winget install --id Docker.DockerDesktop -e --source winget --silent --accept-package-agreements --accept-source-agreements >nul 2>nul
        echo [INFO] Docker Desktop usually needs one manual first launch to finish WSL2 setup and accept its license.
        if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
            echo [INFO] Starting Docker Desktop...
            start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
        )
    ) else (
        echo [WARN] winget is not available. Install Docker Desktop manually from https://www.docker.com/products/docker-desktop/
    )
)

where docker >nul 2>nul
if not %errorlevel%==0 (
    set "DOCKER_OK=0"
    goto :eof
)

echo [INFO] Docker CLI found. Waiting for the Docker engine to be ready ^(up to 2 minutes^)...
set "DOCKER_OK=0"
for /l %%i in (1,1,24) do (
    if "!DOCKER_OK!"=="0" (
        docker info >nul 2>nul
        if not errorlevel 1 (
            docker compose version >nul 2>nul
            if not errorlevel 1 (
                set "DOCKER_OK=1"
            )
        )
        if "!DOCKER_OK!"=="0" timeout /t 5 /nobreak >nul
    )
)
goto :eof

:check_migration_files
set "MF_MONGO_ARCHIVE=%ROOT_DIR%\migration\mongodb-backup\all-databases.archive.gz"
set "MF_MONGO_VOLUME=%ROOT_DIR%\migration\docker-volume-backup\museum_app_museum_mongodb_data.tar.gz"
set "MF_QDRANT_VOLUME=%ROOT_DIR%\migration\docker-volume-backup\museum_app_museum_qdrant_data.tar.gz"
set "MF_OPENCLIP_CACHE=%ROOT_DIR%\migration\openclip-backup\huggingface-cache.tar.gz"
set "MF_UPLOADS=%ROOT_DIR%\migration\uploads\backend-uploads.tar.gz"

set "MIGRATION_READY=1"

if exist "%MF_MONGO_ARCHIVE%" (echo [FOUND]   mongodb-backup\all-databases.archive.gz) else (echo [MISSING] mongodb-backup\all-databases.archive.gz & set "MIGRATION_READY=0")
if exist "%MF_MONGO_VOLUME%" (echo [FOUND]   docker-volume-backup\museum_app_museum_mongodb_data.tar.gz) else (echo [MISSING] docker-volume-backup\museum_app_museum_mongodb_data.tar.gz & set "MIGRATION_READY=0")
if exist "%MF_QDRANT_VOLUME%" (echo [FOUND]   docker-volume-backup\museum_app_museum_qdrant_data.tar.gz) else (echo [MISSING] docker-volume-backup\museum_app_museum_qdrant_data.tar.gz & set "MIGRATION_READY=0")
if exist "%MF_UPLOADS%" (echo [FOUND]   uploads\backend-uploads.tar.gz) else (echo [MISSING] uploads\backend-uploads.tar.gz & set "MIGRATION_READY=0")
if exist "%MF_OPENCLIP_CACHE%" (echo [FOUND]   openclip-backup\huggingface-cache.tar.gz ^(recommended^)) else (echo [MISSING] openclip-backup\huggingface-cache.tar.gz ^(recommended, not required^))

if "%MIGRATION_READY%"=="1" (
    echo [INFO] Bundled migration backup detected and looks complete.
) else (
    echo [INFO] No complete migration backup was found. Setup will continue as a brand-new installation.
)
goto :eof

:restore_migration
if "%MIGRATION_READY%"=="1" (
    if not "%DOCKER_OK%"=="1" (
        echo [WARN] Skipped Docker volume restore: Docker is not ready.
    ) else (
        docker volume inspect museum_app_museum_mongodb_data >nul 2>nul
        if !errorlevel!==0 (
            echo [INFO] An existing museum_app_museum_mongodb_data Docker volume was already found on
            echo        this computer. To protect existing data, the bundled migration backup will NOT
            echo        be restored over it. This is expected on the second or later run of setup.bat.
        ) else (
            echo [INFO] No existing museum database volume found. Restoring the bundled migration
            echo        backup now. This runs the project's existing migration\restore.ps1 and can
            echo        take several minutes the first time ^(large archives, first Docker image pulls^)...
            powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%\migration\restore.ps1" -SkipAiTools
            if !errorlevel!==0 (
                echo [OK] Migration restore finished.
                set "RESTORE_DONE=1"
            ) else (
                echo [WARN] Migration restore reported an error. Review the messages above.
                echo        Re-run setup.bat after resolving the issue - restore.ps1 is safe to re-run
                echo        as long as the museum_app_museum_mongodb_data volume was not created yet.
            )
        )
    )
) else (
    echo [INFO] Skipped Docker volume restore: no complete migration backup is bundled in migration\.
)

REM The native/host backend (used by run.bat) reads the OpenCLIP model cache from the user's
REM default Hugging Face cache directory, not from the Docker volume that restore.ps1 populates
REM for the containerized services. Restore it there too, independently of the volume restore
REM above, so the native backend reuses the restored model instead of re-downloading it. This is
REM self-gated on the cache not already existing, so it is safe to run on every invocation.
if exist "%MF_OPENCLIP_CACHE%" (
    if not exist "%UserProfile%\.cache\huggingface" (
        echo [INFO] Restoring OpenCLIP model cache for the native backend...
        if not exist "%UserProfile%\.cache" mkdir "%UserProfile%\.cache" >nul 2>nul
        tar -xzf "%MF_OPENCLIP_CACHE%" -C "%UserProfile%\.cache"
        if !errorlevel!==0 (
            echo [OK] OpenCLIP model cache restored to %UserProfile%\.cache\huggingface
        ) else (
            echo [WARN] Could not extract the OpenCLIP cache for native use. The model will download on first use instead.
        )
    ) else (
        echo [INFO] Native OpenCLIP cache already present at %UserProfile%\.cache\huggingface - not overwritten.
    )
)
goto :eof

:free_port_8000
if not "%DOCKER_OK%"=="1" (
    echo [WARN] Skipped: Docker is not ready.
    goto :eof
)
docker compose -f "%COMPOSE_FILE%" stop backend openclip >nul 2>nul
echo [OK] Any Docker "backend"/"openclip" containers occupying port 8000 were stopped.
echo      MongoDB and Qdrant Docker containers are left running for run.bat to use.
goto :eof

:final_verification
if not "%PY_OK%"=="1" (
    echo [WARN] Skipped: Python is not available.
    goto :eof
)
if not exist "%VENV_PY%" (
    echo [WARN] Skipped: backend\.venv was not created successfully.
    goto :eof
)

pushd "%ROOT_DIR%\backend"
"%VENV_PY%" -m scripts.check_migration_data
popd

if "%DOCKER_OK%"=="1" (
    echo.
    echo [INFO] Docker container status:
    docker compose -f "%COMPOSE_FILE%" ps
)

if exist "%APK_PATH%" (
    echo [OK] Android APK is present at %APK_PATH%
) else (
    echo [WARN] Museum_App.apk was not found at the project root.
)
goto :eof
