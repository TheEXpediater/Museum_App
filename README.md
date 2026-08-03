# Museum Guide System

Give 2 implements the Admin Artifact Management System with AI image recognition: secure admin login, JWT session handling, artifact CRUD, multipart image upload, local image storage, OpenCLIP embeddings, Qdrant vector search, artifact matching, AI maintenance tools, and green Material 3 Android admin screens.

Out of scope for Give 2: visitor camera scanning workflow, visitor artifact information flow, 3D model viewing, visitor navigation, visitor analytics, and reports. Those belong to Give 3 or later.

## Simplified Backend Startup

From the repository root, run:

```powershell
python start_backend.py
```

Optional commands:

```powershell
python start_backend.py --test
python start_backend.py --check
python start_backend.py --setup-ai
python start_backend.py --check-ai
python start_backend.py --test-ai
python start_backend.py --index-ai
python start_backend.py --index-ai --rebuild
python start_backend.py --stop
```

The launcher starts MongoDB with Docker Compose, prepares `backend\.venv` when needed, creates `backend\.env` only if it is missing, checks the setup, creates the first admin account when needed, and starts FastAPI.

## Backend Setup

Run these commands from Windows PowerShell:

```powershell
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

Edit `backend\.env` before starting the API:

```properties
MONGODB_URL=mongodb://localhost:27017
MONGODB_DATABASE=museum_guide
JWT_SECRET_KEY=<replace with a long random secret, at least 24 characters>
UPLOAD_DIRECTORY=uploads/images
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=<at least 12 characters>
ADMIN_FULL_NAME=Museum Administrator
AI_ENABLED=true
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION=artifact_images
QDRANT_DISTANCE=cosine
OPENCLIP_MODEL_NAME=ViT-B-32
OPENCLIP_PRETRAINED=laion2b_s34b_b79k
OPENCLIP_DEVICE=auto
AI_MODEL_DOWNLOAD_ALLOWED=true
AI_WARMUP_ON_STARTUP=false
AI_RECOGNITION_STRONG_THRESHOLD=0.45
AI_RECOGNITION_POSSIBLE_THRESHOLD=0.32
AI_RECOGNITION_MAX_RESULTS=5
AI_RECOGNITION_VECTOR_CANDIDATES=25
```

`UPLOAD_DIRECTORY=uploads/images` is resolved relative to the `backend` directory. Absolute upload paths are also supported.

Start MongoDB Community Edition, then run:

```powershell
python -m scripts.check_setup
python -m scripts.create_admin
python -m scripts.check_setup
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

The first `check_setup` can report that the admin account is missing on a fresh database. After `create_admin`, the second check should pass. If the admin already exists and you intentionally changed `ADMIN_PASSWORD`, reset it explicitly:

```powershell
python -m scripts.create_admin --update-existing
```

Verification URLs on the Windows computer:

```text
Swagger UI: http://localhost:8000/docs
Health check: http://localhost:8000/api/v1/health
Uploaded images: http://localhost:8000/uploads/images/<filename>
```

The health response should include:

```json
{
  "status": "healthy",
  "database": "connected",
  "uploads_directory": "available"
}
```

## Backend Tests

The backend tests use `mongomock` and a temporary upload directory, so they do not modify development data.

```powershell
cd backend
.venv\Scripts\activate
python -m pytest -q
```

## Give 2 AI Recognition

Architecture:

```text
FastAPI
|-- MongoDB
|-- Local artifact images
|-- OpenCLIP
`-- Qdrant
```

OpenCLIP generates normalized image embeddings. Qdrant stores one vector per artifact image using deterministic point IDs derived from the artifact ID and stored image path. MongoDB remains the source of truth for artifact metadata.

Install and verify AI support from the repository root:

```powershell
python start_backend.py --setup-ai
```

AI verification commands:

```powershell
python start_backend.py --check-ai
python start_backend.py --test-ai
```

Start the complete backend:

```powershell
python start_backend.py
```

Health endpoints:

```text
http://localhost:8000/api/v1/health
http://localhost:8000/api/v1/ai/health
```

Qdrant local ports:

```text
REST: http://localhost:6333
gRPC: localhost:6334
```

The FastAPI server loads OpenCLIP lazily inside the live API process. Setup, check, and test commands run in separate Python processes, so they can verify installation without warming the running server. The first live model load may require internet access and can take several minutes while model weights are downloaded into the normal local PyTorch/OpenCLIP cache. Later use can rely on that cache, and model weight files are not committed to the repository. Qdrant data is stored in a Docker named volume, so `python start_backend.py --stop` does not delete vector data.

Live-server warmup:

```text
POST /api/v1/ai/warmup
GET  /api/v1/ai/warmup/status
```

Both warmup endpoints require an administrator token. `POST /api/v1/ai/warmup` starts a non-blocking in-process model load and returns `202` while loading. It embeds a generated in-memory RGB image through the existing OpenCLIP embedding service; it does not write a test image, create a fake artifact, or add a vector to Qdrant. Set `AI_WARMUP_ON_STARTUP=true` only when you want FastAPI startup to begin the same background warmup automatically.

AI health states:

```text
disabled       AI is turned off by configuration.
not_installed  Required AI packages are missing.
idle           OpenCLIP is installed and ready to load.
loading        Live-server warmup or embedding load is in progress.
loaded         OpenCLIP is loaded in the running API process.
failed         Warmup failed; check backend logs and retry.
```

Zero indexed vectors means Qdrant is connected but no artifact image vectors have been stored yet. Use System Status -> Load AI Model, then Index Artifact Images. A recognition query image is not required for first-run indexing.

Changing `OPENCLIP_MODEL_NAME` or `OPENCLIP_PRETRAINED` can change the embedding dimension and may require rebuilding the Qdrant collection. Artifact CRUD remains usable when AI, OpenCLIP, or Qdrant is disabled or temporarily unavailable; artifacts are saved first, then vector indexing is attempted.

### Artifact Indexing

Artifact create and update operations synchronize Qdrant after MongoDB and local image storage succeed. New images are indexed automatically, removed images have their vectors deleted, and changes to artifact code, name, or category refresh vector payloads. Primary-image changes do not regenerate vectors because searchable image bytes and payload fields are unchanged.

Artifacts may include these optional AI status fields:

```text
ai_index_status: not_indexed | pending | indexed | partial | failed
ai_indexed_image_count
ai_indexed_at
ai_index_error
```

Backfill existing records:

```powershell
cd backend
python -m scripts.index_existing_artifacts
python -m scripts.index_existing_artifacts --artifact-id <artifact_id>
python -m scripts.index_existing_artifacts --dry-run
python -m scripts.index_existing_artifacts --rebuild
```

From the repository root:

```powershell
python start_backend.py --index-ai
python start_backend.py --index-ai --rebuild
```

`--rebuild` deletes and recreates only the configured Qdrant artifact collection after confirmation. It never deletes MongoDB records or uploaded artifact images. The Android System Status screen exposes the same dangerous rebuild behind a confirmation dialog.

For a small capstone dataset, `POST /api/v1/ai/index/all` runs synchronously. Large collections should move this work into a background job with progress tracking.

The Android System Status screen labels this action `Index Artifact Images`. It confirms before starting and warns that the first run may load OpenCLIP. When there are no artifact images, the app shows `Add at least one artifact image before indexing.`

### Recognition Thresholds

Recognition returns ranked artifact matches using `similarity_score` and `match_level`. Scores are visual similarity signals, not scientific certainty or probability. Default cosine-similarity thresholds:

```properties
AI_RECOGNITION_STRONG_THRESHOLD=0.45
AI_RECOGNITION_POSSIBLE_THRESHOLD=0.32
```

Use real museum photos to calibrate thresholds. Test same-artifact images under different angles, lighting, distance, partial views, and unrelated objects before treating the thresholds as operational.

### Admin UI Navigation

After login, the Android admin interface provides:

```text
Dashboard
Artifacts
Recognize
Settings
```

Add Artifact and Edit Artifact remain nested artifact-management destinations. The bottom navigation uses the short `Recognize` label, while the screen title remains `AI Recognition`. System Status now opens from Settings, and Logout lives only inside Settings behind a confirmation dialog. The Settings screen shows account information from the stored admin session or `/api/v1/auth/me`, concise system summaries, app version, Give 2 about text, and the destructive logout action.

Passwords are hashed and verified with the direct `bcrypt` package. Existing `$2a$`, `$2b$`, and `$2y$` bcrypt hashes remain valid, malformed stored hashes fail safely, and passlib is no longer required for password verification.

## Android Setup

Open the repository root in Android Studio and run the `android:app` configuration.

`BuildConfig.API_BASE_URL` is compiled from these sources, in order:

1. Gradle property `API_BASE_URL`, such as `-PAPI_BASE_URL=...`
2. Environment variable `API_BASE_URL`
3. Untracked project `local.properties`
4. Fallback `http://10.0.2.2:8000/`

For Android Studio, prefer the repository-root `local.properties` file because it is already ignored by Git:

```properties
API_BASE_URL=http://10.0.2.2:8000/
DEBUG_ADMIN_EMAIL=
DEBUG_ADMIN_PASSWORD=
```

For command-line builds, you can also pass the value directly:

```powershell
.\gradlew.bat :android:app:assembleDebug -PAPI_BASE_URL=http://192.168.100.12:8000/
```

The URL is normalized to end in `/`, and malformed URLs fail the Gradle build with a clear error. Rebuild the APK after changing `API_BASE_URL`; it is a compile-time value.

Use [local.properties.example](local.properties.example) as the safe tracked template. Never commit real passwords. `local.properties` must remain ignored by Git.

### Emulator Configuration

Use the emulator host alias:

```properties
API_BASE_URL=http://10.0.2.2:8000/
```

`10.0.2.2` is only for an Android emulator. Do not use it for a physical phone build.

### Physical-Device Configuration

Find the active Windows IPv4 address:

```powershell
ipconfig
```

Use the Wi-Fi adapter IPv4 address in `local.properties` or `-PAPI_BASE_URL`:

```properties
API_BASE_URL=http://<WINDOWS_LAN_IP>:8000/
```

For the current Wi-Fi development machine:

```properties
API_BASE_URL=http://192.168.100.12:8000/
DEBUG_ADMIN_EMAIL=<your local admin email>
DEBUG_ADMIN_PASSWORD=<your local admin password>
```

Physical-device requirements:

- Phone and computer must use the same Wi-Fi network.
- Uvicorn must run with `--host 0.0.0.0`.
- Guest Wi-Fi networks may block device-to-device communication.
- VPNs may interfere with LAN routing.
- Windows Firewall may block inbound TCP port `8000`.
- The APK must be rebuilt after changing the compile-time base URL.

Verify that port `8000` is listening:

```powershell
Get-NetTCPConnection -LocalPort 8000 -State Listen
```

or:

```powershell
netstat -ano | findstr :8000
```

Optional Administrator PowerShell firewall rule:

```powershell
New-NetFirewallRule `
  -DisplayName "Museum FastAPI 8000" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 8000 `
  -Action Allow
```

Optional USB testing with ADB reverse:

```powershell
adb devices
adb reverse tcp:8000 tcp:8000
adb reverse --list
```

When using ADB reverse on a physical device, compile the debug APK with:

```properties
API_BASE_URL=http://127.0.0.1:8000/
```

`127.0.0.1` is for a USB-connected physical phone only when `adb reverse tcp:8000 tcp:8000` is active. It is not the same as normal same-Wi-Fi LAN testing.

Debug builds allow local cleartext HTTP through `android/app/src/debug/res/xml/network_security_config.xml`. Release builds keep unrestricted cleartext disabled through the main network security config.

### Physical Phone LAN Verification

Start FastAPI from Windows:

```powershell
cd C:\Capstone-client\Museum_App\backend
.venv\Scripts\activate
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Check these URLs on the Windows computer:

```text
http://localhost:8000/api/v1/health
http://192.168.100.12:8000/api/v1/health
```

Before opening the APK, check this URL in the physical phone browser:

```text
http://192.168.100.12:8000/api/v1/health
```

If the phone browser cannot open the health endpoint, the issue is outside the Android app. Check same Wi-Fi, temporarily disable mobile data, disable VPNs, avoid guest Wi-Fi/AP isolation, confirm the Windows network profile and Firewall, verify the active Wi-Fi IPv4 address, and confirm FastAPI is still running.

When the phone calls the backend through `http://192.168.100.12:8000/`, artifact image URLs are generated from that request base URL and should begin with:

```text
http://192.168.100.12:8000/uploads/images/
```

Coil can load these local HTTP image URLs in debug builds because the debug network security override permits local cleartext traffic.

Android checks:

```powershell
.\gradlew.bat :android:app:testDebugUnitTest
.\gradlew.bat :android:app:assembleDebug
```

After changing `local.properties`, clean, rebuild, and install the debug APK on the connected phone:

```powershell
cd C:\Capstone-client\Museum_App
.\gradlew.bat clean
.\gradlew.bat :android:app:assembleDebug
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

## Postman

Import:

```text
postman/Museum_Guide_Give2.postman_collection.json
```

Set collection variables:

```text
base_url=http://localhost:8000
admin_email=<created admin email>
admin_password=<created admin password>
```

Run this sequence:

1. `Health check`
2. `AI health`
3. `Admin login`
4. `Dashboard`
5. `Create artifact`
6. `List artifacts`
7. `AI Warmup`
8. `AI Warmup Status`
9. `Index all artifacts`
10. `Index status`
11. `Recognize image`

For Give 1-only regression checks, `postman/Museum_Guide_Give1.postman_collection.json` is still available.

The collection stores `access_token` automatically after `Admin login`. Postman stays configured for requests from the Windows development computer; it does not configure the Android app.

## API Summary

Authentication:

```text
POST /api/v1/auth/login
GET  /api/v1/auth/me
```

Artifacts:

```text
GET    /api/v1/artifacts
GET    /api/v1/artifacts/{artifact_id}
POST   /api/v1/artifacts
PATCH  /api/v1/artifacts/{artifact_id}
DELETE /api/v1/artifacts/{artifact_id}
POST   /api/v1/artifacts/{artifact_id}/images
DELETE /api/v1/artifacts/{artifact_id}/images/{image_name}
PATCH  /api/v1/artifacts/{artifact_id}/primary-image
```

AI and dashboard:

```text
GET  /api/v1/ai/health
POST /api/v1/ai/warmup
GET  /api/v1/ai/warmup/status
POST /api/v1/ai/recognize
POST /api/v1/ai/index/artifacts/{artifact_id}
POST /api/v1/ai/index/all
POST /api/v1/ai/index/failed
POST /api/v1/ai/index/rebuild
GET  /api/v1/ai/index/status
GET  /api/v1/admin/dashboard
```

MongoDB collections:

```text
users
artifacts
```

Uploaded files are stored under:

```text
backend/uploads/images/
```

Qdrant stores artifact image vectors in the configured collection, default:

```text
artifact_images
```

## Give 2 Verification Commands

Run from the repository root:

```powershell
python start_backend.py --check
python start_backend.py --check-ai
python start_backend.py --test-ai
python start_backend.py --test
.\gradlew.bat :android:app:testDebugUnitTest
.\gradlew.bat :android:app:assembleDebug
```

## Troubleshooting

If AI health is degraded, check `python start_backend.py --check-ai`, Qdrant at `http://localhost:6333`, and the OpenCLIP dependency installation. If recognition returns `no_match`, first confirm that artifacts have indexed vectors, then tune thresholds with real museum photos.

If Android cannot connect, verify `API_BASE_URL`, rebuild the APK, and confirm the health endpoint opens from the emulator or phone browser. Debug builds allow local cleartext HTTP; release builds keep unrestricted cleartext disabled.
