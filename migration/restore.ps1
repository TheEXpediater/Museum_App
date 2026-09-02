param(
    [switch]$RestoreEnvSecrets,
    [switch]$SkipAiTools,
    [switch]$StartFrontendBuild
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir "..")
$ComposeFile = Join-Path $RootDir "compose.yaml"
$MongoBackupDir = Join-Path $ScriptDir "mongodb-backup"
$VolumeBackupDir = Join-Path $ScriptDir "docker-volume-backup"
$UploadBackupDir = Join-Path $ScriptDir "uploads"
$OpenClipBackupDir = Join-Path $ScriptDir "openclip-backup"

function Write-Info($Message) { Write-Host "[INFO] $Message" }
function Write-Ok($Message) { Write-Host "[OK] $Message" }
function Write-Warn($Message) { Write-Warning $Message }
function Fail($Message) { throw $Message }

function Test-Command($Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-DotEnvValue($Path, $Name) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $pattern = "^\s*$([regex]::Escape($Name))\s*=(.*)$"
    $line = Get-Content -LiteralPath $Path | Where-Object { $_ -match $pattern } | Select-Object -Last 1
    if (-not $line) { return $null }
    $value = ($line -replace $pattern, '$1').Trim()
    $value = $value.Trim('"').Trim("'")
    return $value
}

function Invoke-Compose {
    & docker compose -f $ComposeFile @args
}

function Validate-Docker {
    if (-not (Test-Command docker)) { Fail "Docker was not found. Install Docker Desktop first." }
    cmd /c "docker info >nul 2>nul"
    if ($LASTEXITCODE -ne 0) { Fail "Docker is installed but not running." }
    cmd /c "docker compose version >nul 2>nul"
    if ($LASTEXITCODE -ne 0) { Fail "Docker Compose was not found." }
    if (-not (Test-Path -LiteralPath $ComposeFile)) { Fail "compose.yaml was not found." }
    Write-Ok "Docker and Compose are available"
}

function Create-Networks {
    cmd /c "docker network inspect museum_app_default >nul 2>nul"
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Docker network exists: museum_app_default"
    } else {
        cmd /c "docker network create museum_app_default >nul 2>nul"
        if ($LASTEXITCODE -ne 0) { Fail "Could not create Docker network museum_app_default." }
        Write-Ok "Docker network created: museum_app_default"
    }
}

function Restore-OneVolume($Archive) {
    $file = Split-Path -Leaf $Archive
    $volume = $file -replace '\.tar\.gz$', ''
    Write-Info "Restoring Docker volume $volume"
    & docker volume create $volume *> $null
    & docker run --rm `
        -v "${volume}:/volume" `
        -v "${VolumeBackupDir}:/backup:ro" `
        alpine sh -lc "find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xzf /backup/$file -C /volume"
    if ($LASTEXITCODE -ne 0) { Fail "Docker volume restore failed for $volume." }
}

function Restore-DockerVolumes {
    $archives = @(Get-ChildItem -LiteralPath $VolumeBackupDir -Filter "*.tar.gz" -File -ErrorAction SilentlyContinue)
    if ($archives.Count -eq 0) {
        Write-Warn "No Docker volume archives found in migration/docker-volume-backup."
        return
    }
    foreach ($archive in $archives) {
        Restore-OneVolume $archive.FullName
    }
    Write-Ok "Docker volume restore pass complete"
}

function Start-Infrastructure {
    Write-Info "Starting MongoDB and Qdrant"
    Invoke-Compose up -d mongodb qdrant
    if ($LASTEXITCODE -ne 0) { Fail "Could not start MongoDB and Qdrant." }
    Write-Ok "Infrastructure containers started"
}

function Restore-MongoDb {
    $archive = Join-Path $MongoBackupDir "all-databases.archive.gz"
    if (-not (Test-Path -LiteralPath $archive)) {
        Write-Warn "No MongoDB archive found at migration/mongodb-backup/all-databases.archive.gz"
        return
    }

    $envFile = Join-Path $RootDir "backend\.env"
    $mongoUrl = if ($env:MONGODB_RESTORE_URI) { $env:MONGODB_RESTORE_URI } elseif ($env:MONGODB_URL) { $env:MONGODB_URL } else { Get-DotEnvValue $envFile "MONGODB_URL" }
    if (-not $mongoUrl) { $mongoUrl = "mongodb://localhost:27018" }

    Write-Info "Restoring MongoDB archive with mongorestore"
    if (Test-Command mongorestore) {
        & mongorestore "--uri=$mongoUrl" "--archive=$archive" "--gzip" "--drop"
        if ($LASTEXITCODE -ne 0) { Fail "mongorestore failed." }
        Write-Ok "MongoDB restored from all-databases archive"
    } else {
        Write-Warn "mongorestore was not found. Install MongoDB Database Tools and re-run restore."
    }
}

function Restore-UploadedFiles {
    $archives = @(Get-ChildItem -LiteralPath $UploadBackupDir -Filter "*.tar.gz" -File -ErrorAction SilentlyContinue)
    if ($archives.Count -eq 0) {
        Write-Warn "No upload/static archives found in migration/uploads."
    } else {
        foreach ($archive in $archives) {
            Write-Info "Restoring $($archive.Name)"
            & tar -xzf $archive.FullName -C $RootDir
            if ($LASTEXITCODE -ne 0) { Fail "tar restore failed for $($archive.Name)." }
        }
    }

    $backendUploads = Join-Path $RootDir "backend\uploads"
    if (Test-Path -LiteralPath $backendUploads) {
        & docker volume create museum_app_museum_backend_uploads *> $null
        & docker run --rm `
            -v "museum_app_museum_backend_uploads:/volume" `
            -v "${backendUploads}:/source:ro" `
            alpine sh -lc "find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} + && cp -a /source/. /volume/"
        if ($LASTEXITCODE -ne 0) { Fail "Could not restore backend uploads to Docker volume." }
        Write-Ok "Backend upload files restored to Docker volume"
    }
}

function Restore-OpenClipModels {
    & docker volume create museum_app_museum_openclip_models *> $null
    & docker volume create museum_app_museum_openclip_embeddings *> $null

    $archives = @(Get-ChildItem -LiteralPath $OpenClipBackupDir -Filter "*-cache.tar.gz" -File -ErrorAction SilentlyContinue)
    if ($archives.Count -eq 0) {
        Write-Warn "No OpenCLIP model cache archives found in migration/openclip-backup."
    } else {
        foreach ($archive in $archives) {
            Write-Info "Restoring OpenCLIP cache $($archive.Name)"
            & docker run --rm `
                -v "museum_app_museum_openclip_models:/models" `
                -v "${OpenClipBackupDir}:/backup:ro" `
                alpine sh -lc "tar -xzf /backup/$($archive.Name) -C /models"
            if ($LASTEXITCODE -ne 0) { Fail "Could not restore OpenCLIP cache $($archive.Name)." }
        }
        Write-Ok "OpenCLIP model cache restored to Docker volume"
    }

    $secretArchive = Join-Path $OpenClipBackupDir "config\env-secret-files.tar.gz"
    if (Test-Path -LiteralPath $secretArchive) {
        if ($RestoreEnvSecrets) {
            & tar -xzf $secretArchive -C $RootDir
            if ($LASTEXITCODE -ne 0) { Fail "Could not restore secret-bearing env files." }
            Write-Ok "Secret-bearing env files restored because -RestoreEnvSecrets was set"
        } else {
            Write-Warn "Secret-bearing env backup exists but was not restored. Re-run with -RestoreEnvSecrets on a trusted private machine."
        }
    }
}

function Start-Application {
    Write-Info "Starting application containers"
    Invoke-Compose up -d --build mongodb qdrant backend
    if ($LASTEXITCODE -ne 0) { Fail "Could not start core application containers." }

    if (-not $SkipAiTools) {
        & docker compose -f $ComposeFile --profile ai-tools up -d --build openclip
        if ($LASTEXITCODE -ne 0) { Fail "Could not start OpenCLIP utility container." }
    }

    if ($StartFrontendBuild) {
        & docker compose -f $ComposeFile --profile frontend run --rm frontend
        if ($LASTEXITCODE -ne 0) { Fail "Frontend Android build container failed." }
    }
    Write-Ok "Application containers started"
}

function Verify-System {
    Write-Info "Verifying container state"
    Invoke-Compose ps
    Write-Warn "If MongoDB auth variables are set, ensure MONGODB_RESTORE_URI and MONGODB_URL include the matching credentials."
    Write-Ok "Restore sequence complete"
}

Validate-Docker
Create-Networks
Restore-DockerVolumes
Start-Infrastructure
Restore-MongoDb
Restore-UploadedFiles
Restore-OpenClipModels
Start-Application
Verify-System
