# Museum_App Migration Guide

This folder contains the backup and restore tooling needed to move the Museum_App project to another computer while preserving Docker services, MongoDB data, uploaded files, OpenCLIP model cache, and Qdrant vector data.

## Requirements

Install these on the old and new computers:

- Docker Desktop
- Docker Compose
- MongoDB Database Tools (`mongodump` and `mongorestore`)
- Python
- Node.js
- Git

The project is an Android/Kotlin frontend plus a Python FastAPI backend. Node.js is listed as a general workstation requirement, but this scan did not find a project `package.json`.

## What Gets Backed Up

- MongoDB databases through `mongodump`
- Docker volumes created by `compose.yaml`
- Backend uploaded images from `backend/uploads`
- Android runtime assets from `android/app/src/main/assets`
- Visitor UI source assets
- Artifact source ZIPs from `artifact_image_source`
- OpenCLIP/Hugging Face/Torch cache folders when present
- Qdrant vector storage through the Docker volume backup
- AI configuration references and environment variable names

Secret-bearing env files are not copied by default. Use `--include-secrets` or `-IncludeSecrets` only for a trusted private migration bundle.

## OLD COMPUTER BACKUP

From Git Bash, WSL, macOS, or Linux:

```bash
git pull main

cd migration

./backup.sh
```

On Windows PowerShell:

```powershell
git pull main

cd migration

.\backup.ps1
```

To include `backend/.env` and `local.properties` in the backup bundle:

```bash
./backup.sh --include-secrets
```

```powershell
.\backup.ps1 -IncludeSecrets
```

Protect secret backups carefully. They may contain database credentials, JWT secrets, admin credentials, and local Android settings.

## Backup Output

The scripts write to:

- `migration/mongodb-backup/`
- `migration/docker-volume-backup/`
- `migration/uploads/`
- `migration/openclip-backup/`

Large data is expected. Current scan found about 968 MB of backend uploaded artifact images, about 968 MB of source artifact ZIPs, and about 577 MB of cached OpenCLIP model weights. Use Git LFS or a private artifact transfer if your Git host rejects large files.

## NEW COMPUTER RESTORE

Clone the repository and restore:

```bash
git clone <your-repository-url>
cd Museum_App/migration
./restore.sh
```

On Windows PowerShell:

```powershell
git clone <your-repository-url>
cd Museum_App\migration
.\restore.ps1
```

To restore secret-bearing env files from a trusted backup:

```bash
RESTORE_ENV_SECRETS=1 ./restore.sh
```

```powershell
.\restore.ps1 -RestoreEnvSecrets
```

The restore script validates Docker, creates the Compose network, restores Docker volumes, starts MongoDB/Qdrant, restores MongoDB from `mongorestore`, restores uploads, restores OpenCLIP model caches, starts the backend container, and starts the OpenCLIP utility container.

## Docker Compose Services

- `mongodb`: MongoDB database container with persistent volume `museum_mongodb_data`.
- `qdrant`: Qdrant vector database with persistent volume `museum_qdrant_data`.
- `backend`: FastAPI API container exposing port `8000`.
- `openclip`: AI utility container with OpenCLIP dependencies and shared model cache. Enabled by the `ai-tools` profile.
- `frontend`: Android build container. Enabled by the `frontend` profile.

Start core services:

```bash
docker compose -f compose.yaml up -d --build mongodb qdrant backend
```

Start OpenCLIP utility tools:

```bash
docker compose -f compose.yaml --profile ai-tools up -d --build openclip
```

Build the Android APK in the frontend container:

```bash
docker compose -f compose.yaml --profile frontend run --rm frontend
```

## MongoDB Authentication

The Compose file supports MongoDB authentication through these variables:

- `MONGO_INITDB_ROOT_USERNAME`
- `MONGO_INITDB_ROOT_PASSWORD`
- `MONGO_INITDB_DATABASE`
- `MONGODB_URL`
- `MONGODB_DATABASE`

For compatibility with the existing local setup, authentication is optional by default. If you enable MongoDB root credentials, set `MONGODB_URL` to include matching credentials and `authSource=admin`.

Example:

```bash
export MONGO_INITDB_ROOT_USERNAME=museum_admin
export MONGO_INITDB_ROOT_PASSWORD=<strong-password>
export MONGODB_URL='mongodb://museum_admin:<strong-password>@mongodb:27017/museum_guide?authSource=admin'
export MONGODB_DATABASE=museum_guide
```

For host restore with MongoDB authentication, set:

```bash
export MONGODB_RESTORE_URI='mongodb://museum_admin:<strong-password>@localhost:27017/?authSource=admin'
```

## OpenCLIP and Qdrant

The application uses OpenCLIP `ViT-B-32` with pretrained weights `laion2b_s34b_b79k`. Qdrant stores the image vectors in the `artifact_images` collection with cosine distance.

The backend and OpenCLIP utility containers mount:

- `museum_openclip_models` at `/models`
- `museum_openclip_embeddings` at `/embeddings`
- `museum_backend_uploads` at `/app/uploads`

If Qdrant volume backup is unavailable, rebuild vectors after MongoDB and uploads are restored:

```bash
docker compose -f compose.yaml run --rm backend python -m scripts.index_existing_artifacts --rebuild --force
```

## Verification

After restore:

```bash
docker compose -f compose.yaml ps
curl http://localhost:8000/api/v1/health
curl http://localhost:8000/api/v1/ai/health
```

For Android emulator builds, use:

```bash
.\gradlew.bat :android:app:assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8000/
```

For a physical phone, set `API_BASE_URL` to the new computer LAN IP and run Uvicorn/backend on `0.0.0.0`.
