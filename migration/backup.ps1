param(
    [switch]$IncludeSecrets
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

function Get-SafeName($Name) {
    return ($Name -replace '[^A-Za-z0-9_.-]', '_')
}

function Ensure-Dirs {
    foreach ($dir in @($MongoBackupDir, $VolumeBackupDir, $UploadBackupDir, $OpenClipBackupDir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    foreach ($dir in @(
        (Join-Path $OpenClipBackupDir "config"),
        (Join-Path $OpenClipBackupDir "detected-files"),
        (Join-Path $OpenClipBackupDir "vector-indexes")
    )) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
}

function Test-DockerReady {
    if (-not (Test-Command docker)) { return $false }
    & docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Invoke-Compose {
    & docker compose -f $ComposeFile @args
}

function Get-MongoContainer {
    if (Test-DockerReady) {
        $cid = (& docker compose -f $ComposeFile ps -q mongodb 2>$null | Select-Object -First 1)
        if ($cid) {
            $running = (& docker inspect -f "{{.State.Running}}" $cid 2>$null)
            if ($running -eq "true") {
                return ((& docker inspect -f "{{.Name}}" $cid) -replace '^/', '')
            }
        }
        $named = (& docker ps --filter "name=museum-guide-mongodb" --format "{{.Names}}" 2>$null | Select-Object -First 1)
        if ($named) { return $named }
    }
    return $null
}

function Backup-MongoDb {
    Write-Info "Backing up MongoDB with mongodump"
    $envFile = Join-Path $RootDir "backend\.env"
    $mongoUrl = if ($env:MONGODB_URL) { $env:MONGODB_URL } else { Get-DotEnvValue $envFile "MONGODB_URL" }
    if (-not $mongoUrl) { $mongoUrl = "mongodb://localhost:27017" }
    $mongoDb = if ($env:MONGODB_DATABASE) { $env:MONGODB_DATABASE } else { Get-DotEnvValue $envFile "MONGODB_DATABASE" }
    $mongoContainer = Get-MongoContainer
    $archive = Join-Path $MongoBackupDir "all-databases.archive.gz"

    if (Test-Command mongodump) {
        & mongodump "--uri=$mongoUrl" "--archive=$archive" "--gzip"
        if ($LASTEXITCODE -ne 0) { throw "mongodump failed." }
        Write-Ok "MongoDB all-database dump written to migration/mongodb-backup/all-databases.archive.gz"

        if ($mongoDb) {
            $dbArchive = Join-Path $MongoBackupDir "$mongoDb-with-users-and-roles.archive.gz"
            & mongodump "--uri=$mongoUrl" "--db=$mongoDb" "--archive=$dbArchive" "--gzip" "--dumpDbUsersAndRoles"
            if ($LASTEXITCODE -eq 0) {
                Write-Ok "MongoDB database users/roles dump written for configured database"
            } else {
                Write-Warn "Configured database user/role dump failed; all-database dump was still created."
                Remove-Item -LiteralPath $dbArchive -Force -ErrorAction SilentlyContinue
            }
        }
    } else {
        Write-Warn "mongodump was not found. Install MongoDB Database Tools and re-run this script."
        if ($mongoContainer) {
            Write-Warn "MongoDB container was detected ($mongoContainer), but this PowerShell script requires host mongodump for binary-safe archive output."
        }
    }

    [pscustomobject]@{
        created_at_utc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        mongo_container_detected = $mongoContainer
        mongodb_url_present = [bool]$mongoUrl
        mongodb_database_present = [bool]$mongoDb
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $MongoBackupDir "manifest.json") -Encoding UTF8
}

function Test-VolumeExists($VolumeName) {
    & docker volume inspect $VolumeName *> $null
    return ($LASTEXITCODE -eq 0)
}

function Backup-OneVolume($VolumeName) {
    $safe = Get-SafeName $VolumeName
    Write-Info "Backing up Docker volume $VolumeName"
    & docker run --rm `
        -v "${VolumeName}:/volume:ro" `
        -v "${VolumeBackupDir}:/backup" `
        alpine sh -lc "cd /volume && tar -czf /backup/$safe.tar.gz ."
    if ($LASTEXITCODE -ne 0) { throw "Docker volume backup failed for $VolumeName." }
    Add-Content -LiteralPath (Join-Path $VolumeBackupDir "manifest.tsv") -Value "$VolumeName`t$safe.tar.gz"
}

function Backup-DockerVolumes {
    if (-not (Test-DockerReady)) {
        Write-Warn "Docker is not available; skipping Docker volume backup."
        return
    }

    Set-Content -LiteralPath (Join-Path $VolumeBackupDir "manifest.tsv") -Value "" -Encoding UTF8
    $required = @(
        "museum_app_museum_mongodb_data",
        "museum_app_museum_qdrant_data",
        "museum_app_museum_backend_uploads",
        "museum_app_museum_openclip_models",
        "museum_app_museum_openclip_embeddings",
        "museum_app_museum_frontend_gradle_cache"
    )
    $discovered = @(& docker volume ls --filter "label=com.docker.compose.project=museum_app" --format "{{.Name}}" 2>$null)
    $volumes = @($required + $discovered | Where-Object { $_ } | Select-Object -Unique)
    foreach ($volume in $volumes) {
        if (Test-VolumeExists $volume) {
            Backup-OneVolume $volume
        } else {
            Write-Warn "Docker volume not found, skipped: $volume"
        }
    }
    Write-Ok "Docker volume backup pass complete"
}

function Backup-PathTar($RelativePath, $OutputName) {
    $fullPath = Join-Path $RootDir $RelativePath
    if (Test-Path -LiteralPath $fullPath) {
        Write-Info "Backing up $RelativePath"
        $archive = Join-Path $UploadBackupDir "$OutputName.tar.gz"
        $tarPath = $RelativePath -replace '\\', '/'
        & tar -czf $archive -C $RootDir $tarPath
        if ($LASTEXITCODE -ne 0) { throw "tar failed for $RelativePath." }
    } else {
        Write-Warn "Path not found, skipped: $RelativePath"
    }
}

function Backup-UploadedAndStaticFiles {
    Backup-PathTar "backend\uploads" "backend-uploads"
    Backup-PathTar "android\app\src\main\assets" "android-assets"
    Backup-PathTar "visitor_ui" "visitor-ui-source"
    Backup-PathTar "visitor_images_source" "visitor-images-source"
    Backup-PathTar "artifact_image_source" "artifact-image-source-zips"
    Backup-PathTar "postman" "postman"

    foreach ($file in @(
        "ASSET_MANIFEST.json",
        "ANDROID_IMAGE_COPY_INSTRUCTIONS.txt",
        "museum_visitor_entry_assets.zip",
        "PSAU_Museum_Visitor_UI_Production_Assets.zip",
        "visitor_entry.png"
    )) {
        if (Test-Path -LiteralPath (Join-Path $RootDir $file)) {
            $archive = Join-Path $UploadBackupDir "$(Get-SafeName $file).tar.gz"
            & tar -czf $archive -C $RootDir $file
            if ($LASTEXITCODE -ne 0) { throw "tar failed for $file." }
        }
    }
    Write-Ok "Uploaded/static file backup pass complete"
}

function Backup-OpenClipData {
    Write-Info "Backing up OpenCLIP and AI data"
    $configDir = Join-Path $OpenClipBackupDir "config"
    Copy-Item -LiteralPath (Join-Path $RootDir "backend\requirements-ai.txt") -Destination (Join-Path $configDir "requirements-ai.txt") -Force -ErrorAction SilentlyContinue
    Copy-Item -LiteralPath (Join-Path $RootDir "backend\.env.example") -Destination (Join-Path $configDir "backend.env.example") -Force -ErrorAction SilentlyContinue

    $envNamesPath = Join-Path $configDir "environment-variable-names.txt"
    "Environment variable names detected in backend/.env:" | Set-Content -LiteralPath $envNamesPath -Encoding UTF8
    $backendEnv = Join-Path $RootDir "backend\.env"
    if (Test-Path -LiteralPath $backendEnv) {
        Get-Content -LiteralPath $backendEnv |
            Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } |
            ForEach-Object { ($_ -split '=', 2)[0].Trim() } |
            Add-Content -LiteralPath $envNamesPath
    } else {
        "backend/.env not found" | Add-Content -LiteralPath $envNamesPath
    }
    "" | Add-Content -LiteralPath $envNamesPath
    "Environment variable names detected in local.properties:" | Add-Content -LiteralPath $envNamesPath
    $localProperties = Join-Path $RootDir "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_.-]*\s*=' } |
            ForEach-Object { ($_ -split '=', 2)[0].Trim() } |
            Add-Content -LiteralPath $envNamesPath
    } else {
        "local.properties not found" | Add-Content -LiteralPath $envNamesPath
    }

    if ($IncludeSecrets) {
        $secretPaths = @()
        if (Test-Path -LiteralPath $backendEnv) { $secretPaths += "backend/.env" }
        if (Test-Path -LiteralPath $localProperties) { $secretPaths += "local.properties" }
        if ($secretPaths.Count -gt 0) {
            & tar -czf (Join-Path $configDir "env-secret-files.tar.gz") -C $RootDir @secretPaths
            if ($LASTEXITCODE -ne 0) { throw "Secret env archive failed." }
            Write-Warn "Secret-bearing env files were copied because -IncludeSecrets was provided. Protect this backup."
        }
    }

    $detected = Join-Path $OpenClipBackupDir "detected-files\project-ai-files.txt"
    $excluded = @("\.git\", "\.gradle\", "\android\app\build\", "\backend\.venv\", "\migration\")
    $aiExtensions = @(".pt", ".pth", ".bin", ".onnx", ".safetensors", ".npy", ".npz", ".pkl", ".faiss")
    $files = Get-ChildItem -LiteralPath $RootDir -Recurse -File -Force -ErrorAction SilentlyContinue |
        Where-Object {
            $full = $_.FullName
            -not ($excluded | Where-Object { $full -like "*$_*" }) -and
            $aiExtensions -contains $_.Extension.ToLowerInvariant()
        }
    $relativeFiles = @($files | ForEach-Object { $_.FullName.Substring($RootDir.Path.Length + 1).Replace('\', '/') })
    $relativeFiles | Set-Content -LiteralPath $detected -Encoding UTF8
    if ($relativeFiles.Count -gt 0) {
        & tar -czf (Join-Path $OpenClipBackupDir "project-ai-files.tar.gz") -C $RootDir -T $detected
    }

    $cacheCandidates = @()
    if ($env:HF_HOME) { $cacheCandidates += $env:HF_HOME }
    if ($env:TORCH_HOME) { $cacheCandidates += $env:TORCH_HOME }
    if ($env:XDG_CACHE_HOME) { $cacheCandidates += (Join-Path $env:XDG_CACHE_HOME "huggingface") }
    $cacheCandidates += @(
        (Join-Path $HOME ".cache\huggingface"),
        (Join-Path $HOME ".cache\clip"),
        (Join-Path $HOME ".cache\torch")
    )

    foreach ($cache in ($cacheCandidates | Where-Object { $_ } | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $cache) {
            $item = Get-Item -LiteralPath $cache
            $parent = Split-Path -Parent $item.FullName
            $base = Split-Path -Leaf $item.FullName
            & tar -czf (Join-Path $OpenClipBackupDir "$(Get-SafeName $base)-cache.tar.gz") -C $parent $base
            if ($LASTEXITCODE -ne 0) { throw "Cache archive failed for $cache." }
        }
    }

    $qdrantVolumeArchive = Join-Path $VolumeBackupDir "museum_app_museum_qdrant_data.tar.gz"
    if (Test-Path -LiteralPath $qdrantVolumeArchive) {
        Copy-Item -LiteralPath $qdrantVolumeArchive -Destination (Join-Path $OpenClipBackupDir "vector-indexes\qdrant-volume.tar.gz") -Force
    }

    @(
        "Created at UTC: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))",
        "Detected model/vector file list: detected-files/project-ai-files.txt",
        "Qdrant vector data is also backed up through Docker volume museum_app_museum_qdrant_data when present.",
        "OpenCLIP defaults: model ViT-B-32, pretrained laion2b_s34b_b79k, Qdrant collection artifact_images."
    ) | Set-Content -LiteralPath (Join-Path $OpenClipBackupDir "manifest.txt") -Encoding UTF8
    Write-Ok "OpenCLIP/AI backup pass complete"
}

Ensure-Dirs
Backup-MongoDb
Backup-DockerVolumes
Backup-UploadedAndStaticFiles
Backup-OpenClipData
Write-Ok "Backup complete. Review migration/README.md before committing or moving backup artifacts."
