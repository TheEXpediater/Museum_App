# Museum Guide System

Give 2 implements the Admin Artifact Management System with AI image recognition: secure admin login, JWT session handling, artifact CRUD, multipart image upload, local image storage, OpenCLIP embeddings, Qdrant vector search, artifact matching, AI maintenance tools, and green Material 3 Android admin screens.

Give 3 adds a separate guest and student visitor experience inside the same Android app and FastAPI backend. Fresh installs open visitor onboarding instead of administrator login. The completed administrator application, administrator navigation, artifact management, OpenCLIP setup, Qdrant indexing, camera test, System Status, and Settings remain separate and preserved.

Out of scope for this phase: continuous/video recognition, face recognition, registrar-backed Student ID verification, email verification, password reset, visitor analytics beyond guest session records, 3D artifacts, social comments, and admin authoring screens for news/articles.

## Handoff Quick Start

The museum's data lives in three places that always move together: **MongoDB** (artifact records, categories, admin/visitor accounts), **Qdrant** (one AI recognition vector per managed artifact image), and **`backend\uploads\images\`** (the actual managed image files that MongoDB records point to and that Qdrant's vectors were generated from). Moving this project to a new computer means transferring all three together through the migration backup, not recreating them from scratch.

### Moving to a new computer (existing installation)

**On the OLD computer**, from the project folder:

```text
migration\backup.ps1
```

This reads the running MongoDB and Qdrant containers and the `backend\uploads\images\` folder and writes a backup into the `migration\` folder (`migration\mongodb-backup\`, `migration\docker-volume-backup\`, `migration\uploads\`). It only reads the existing system; it never changes it. See [migration/README.md](migration/README.md) for full details and prerequisites (Docker, and MongoDB Database Tools for the most reliable database dump).

Two things then need to move to the new computer, separately:

1. **The Git repository** — the application source code (`android\`, `backend\`, scripts, `compose.yaml`, this README). Clone it normally, or copy the folder.
2. **The `migration\` backup folder's contents** — `mongodb-backup\`, `docker-volume-backup\`, and `uploads\` are large and are not tracked by Git (see `.gitignore`), so they must be copied separately, for example on a USB drive or external storage, into the same `migration\` folder on the new computer.

**On the NEW computer**, in order:

1. **Install prerequisites** — required: Python 3.12 or 3.13, and Docker Desktop, installed and **running**. Optional: MongoDB Database Tools (`mongorestore`), needed only to restore the database backup produced above.
2. **Clone/copy the repository** to the new computer, for example `C:\Capstone-client\Museum_App`.
3. **Copy the backup folder contents** from the old computer into `migration\mongodb-backup\`, `migration\docker-volume-backup\`, and `migration\uploads\` on the new computer, if not already done.
4. **Restore the existing data**, once, **before** running `setup.bat`:

   ```text
   migration\restore.ps1
   ```

   This restores the existing MongoDB records, the existing Qdrant vector collection, and the existing managed artifact images in `backend\uploads\images\` — exactly as they were on the old computer. It is **not** an indexing or rebuild operation: no vectors are regenerated and no images are reprocessed, they are restored as-is. See [migration/README.md](migration/README.md).

   Only run this against a new/empty destination. `restore.ps1` overwrites the target MongoDB and Qdrant Docker volumes with the backup's contents, so never re-run it on a computer that already holds the current live data — that would replace live data with the (possibly older) backup.
5. Continue with **Run setup** below.

### Setting up a brand-new installation (no existing museum data)

Skip the backup/restore steps above and start directly with **Install prerequisites** and **Clone the project**, then go to **Run setup**.

### Run setup

Double-click:

```text
setup.bat
```

This checks that Python and Docker are available, then runs `python start_backend.py --setup`, which:

- prepares the backend's Python virtual environment
- creates `backend\.env` from `backend\.env.example` if it does not already exist
- starts and validates the MongoDB and Qdrant Docker containers
- installs the AI/OpenCLIP dependencies
- prepares/verifies OpenCLIP can load and produce embeddings
- imports any artifact ZIP files found in `artifact_image_source\` (see "Adding new artifacts" below) — already-imported artifacts are recognized and skipped automatically, so running this after a migration restore does not duplicate or re-create the existing dataset
- creates the first admin account if one does not already exist
- attempts to add a narrowly-scoped Windows Firewall rule for the backend port (this step needs an elevated/Administrator prompt to succeed; if it cannot, it prints the one-line PowerShell command to run manually — see Troubleshooting)
- runs a final setup validation check

`setup.bat` is safe to run more than once. It does not intentionally delete, overwrite, regenerate, or rebuild existing MongoDB data, Qdrant collections, vectors, or managed artifact images.

### Adding new artifacts (optional, not needed to restore an existing installation)

To add artifacts that do not already exist in the system, provide their photos one of two ways:

**Preferred method** — place the per-artifact ZIP files directly inside:

```text
Museum_App\artifact_image_source\
```

Filenames do not need to match anything predefined; the importer (`backend/scripts/import_artifact_zips.py`) automatically scans this folder for `.zip` files and uses each ZIP's own filename as the artifact name. One ZIP = one artifact. An optional one-level subfolder becomes that artifact's initial category.

**Optional alternative** — place a single ZIP in the project root named either `Museum_App\museum-images.zip` or `Museum_App\image-assets.zip`, containing the per-artifact ZIPs described above. `setup.bat` extracts it into `artifact_image_source\` automatically.

`artifact_image_source\` is not tracked by Git (it is listed in `.gitignore`) because it holds large image data; it must exist inside the cloned `Museum_App` folder, not beside it. This raw source is only ever needed to create *new* artifacts — it is not required to restore the existing museum dataset, which already lives in MongoDB, Qdrant, and `backend\uploads\images\`.

### Run the backend

Double-click:

```text
run.bat
```

Keep this window open the entire time the Android app is being used — closing it stops the backend. It starts MongoDB, Qdrant, and the FastAPI backend, and prints the available network addresses.

### Get the Android backend address

Look at every address printed under the `Network:` heading, not only the single `Android Backend Address:` line — on a laptop with more than one active network adapter (for example a Docker-internal virtual adapter alongside the real Wi-Fi adapter), that single highlighted line is not guaranteed to be the correct one. Identify the address that belongs to the same Wi-Fi network or mobile hotspot the phone is connected to. Example:

```text
Network:
http://192.168.1.20:8000
```

The address to use in the app is the `host:port` part, e.g. `192.168.1.20:8000`.

### Install the APK

The provided debug build can be installed directly on the Android phone:

```text
android\app\build\outputs\apk\debug\app-debug.apk
```

Android may prompt to allow installing from unknown sources, since this is a debug build and not a Play Store-signed release. The APK never needs to be rebuilt to work with a different laptop or a different IP address.

### Connect phone and laptop

The phone and the laptop must be connected to the same Wi-Fi network, or the same mobile hotspot. The laptop is the one providing the local backend; internet access is not required for normal museum operation once setup has completed and the required model dependencies are already available locally (see "Internet Requirements" below).

### Open the Android app

The app's backend connection follows this flow (implemented by `BackendConnectionManager`, see "Android Backend Connection"):

```text
App opens
    |
    v
Try the previously saved successful backend address -> health check
    |
    +-- succeeds --> connect and keep using it
    |
    +-- fails ------> scan the phone's current local Wi-Fi /24 subnet
                          |
                          +-- backend found -----> connect and save the address
                          |
                          +-- backend not found -> show "Backend Not Found"
                                                        |
                                                        v
                                          user enters laptop IP:port manually
                                                        |
                                                        v
                                                  health check
                                                        |
                                                        v
                                        successful address is saved
```

No mDNS, no NSD, no Bonjour, and no cloud-based discovery are used. No laptop IP is ever hardcoded into the APK, and the APK never needs to be rebuilt when the laptop's IP changes.

### Network changes

If the phone later moves to a different Wi-Fi network or hotspot while the app is already open, the current implementation does not detect that change mid-session. Close and reopen the app to trigger the saved-address check and local-network scan again.

### Normal operation

Once setup is complete, day-to-day use is:

```text
Start laptop
    |
    v
Start Docker Desktop
    |
    v
Double-click run.bat
    |
    v
Connect phone and laptop to the same Wi-Fi or hotspot
    |
    v
Open the installed APK
    |
    v
App connects to the local backend
    |
    v
Use the museum system
```

## Internet Requirements

Internet access may be required during initial setup, to download Python packages, AI/OpenCLIP dependencies, and OpenCLIP model weights that are not already cached locally.

After setup is complete and the required model files already exist locally, normal museum operation is fully local:

```text
Android phone
      |
      | local Wi-Fi / hotspot
      |
      v
Windows laptop
      |
      +-- FastAPI backend
      +-- MongoDB
      +-- Qdrant
      `-- OpenCLIP
```

No internet connection is required for normal backend communication, artifact browsing, or AI recognition once setup has completed.

## Visitor Application

Startup routing uses DataStore state for onboarding completion and the active account type:

```text
No session + onboarding incomplete -> Visitor Onboarding
No session + onboarding complete   -> Visitor Entry
guest or student session           -> Visitor Home
admin session                      -> existing Administrator app
```

Account types are `guest`, `student`, and `admin`. Guest and student sessions use the same app token store as admin sessions with an `account_type` marker, but onboarding completion is kept separately so logout and token expiry do not reset onboarding.

Visitor screens:

```text
Onboarding
Visitor Entry
Guest Information
Student Registration
Student Login
Visitor Home
Artifacts / Facts and Articles / Museum Information
Visitor Artifact Details
AI Scan bottom sheet
Visitor Camera Scan
Visitor Settings
```

Visitor bottom navigation is separate from the administrator shell:

```text
Home | Artifacts | raised center Scan action | Settings
```

The center Scan action opens a Material 3 bottom sheet with visitor-friendly readiness text. It does not expose OpenCLIP, Qdrant, reindexing, model dimensions, System Status, or other administrator AI controls. The camera scan reuses the existing recognition ViewModel state machine, image preparation helper, Retrofit client, multipart upload path, and `/api/v1/ai/recognize` backend pipeline.

Visitor Home shows latest published news, active announcements, featured/recent artifacts, and backend-configured museum information. Missing museum details are shown as `To be configured.` No official hours, address, contact details, coordinates, statistics, PSAU seal, or exact-building claims are invented.

## Visitor Assets

The removable source folder `visitor_images_source` was copied into Android runtime assets:

```text
android/app/src/main/assets/visitor_ui/illustrations/onboarding_welcome.webp
android/app/src/main/assets/visitor_ui/illustrations/onboarding_explore.webp
android/app/src/main/assets/visitor_ui/illustrations/onboarding_ai_scan.webp
android/app/src/main/assets/visitor_ui/illustrations/auth_guest_student.webp
android/app/src/main/assets/visitor_ui/illustrations/home_museum_hero.webp
android/app/src/main/assets/visitor_ui/illustrations/artifacts_facts_articles.webp
android/app/src/main/assets/visitor_ui/illustrations/museum_location.webp
android/app/src/main/assets/visitor_ui/illustrations/news_announcements.webp
android/app/src/main/assets/visitor_ui/icons/psau_museum_app_logo.webp
android/app/src/main/assets/visitor_ui/icons/ai_scan_icon.webp
```

Runtime image loading uses Coil asset URIs from `VisitorAssets`, for example:

```text
file:///android_asset/visitor_ui/illustrations/onboarding_welcome.webp
file:///android_asset/visitor_ui/icons/ai_scan_icon.webp
```

Runtime code does not reference `visitor_images_source`. After the APK builds successfully, the root `visitor_images_source` folder is safe to delete. The supplied app logo is used only as an in-app visitor brand image, not an official PSAU seal.

## Visitor Backend

Visitor authentication and authorization use typed JWT roles:

```text
admin
student
guest
```

Dependencies:

```text
require_admin   -> admin only
require_student -> student only
require_visitor -> student or guest
```

Admin routes still require `admin`. Guest sessions are stored in `guest_sessions`, not in the administrator `users` collection. Student accounts are stored in `students`, not mixed into administrator users.

New visitor and public endpoints:

```text
POST /api/v1/visitor/guest-session
POST /api/v1/student/register
POST /api/v1/student/login
GET  /api/v1/visitor/me
POST /api/v1/visitor/logout

GET /api/v1/public/home
GET /api/v1/public/news
GET /api/v1/public/news/{id}
GET /api/v1/public/announcements
GET /api/v1/public/articles
GET /api/v1/public/articles/{id}
GET /api/v1/public/museum-info
GET /api/v1/public/programs

GET /api/v1/visitor/artifacts
GET /api/v1/visitor/artifacts/{id}
POST /api/v1/ai/recognize
```

`POST /api/v1/ai/recognize` now requires an authenticated `admin`, `student`, or `guest` token. AI maintenance endpoints remain administrator-only.

New MongoDB collections:

```text
students
guest_sessions
news
announcements
museum_articles
museum_information
programs
```

Indexes include unique normalized Student ID, unique normalized student email, guest-session expiry TTL, guest device/session timestamps, publication/activity filters for public content, article category, and unique normalized program names.

Optional demonstration content can be seeded only when collections are empty:

```powershell
cd backend
python -m scripts.seed_public_content
```

The seed content is clearly labeled demonstration content and should be replaced with official museum content before production use.

## Simplified Backend Startup

Double-click `setup.bat` once (or run `python start_backend.py --setup`), then double-click `run.bat` (or run `python start_backend.py`) every time you want to start the backend. `run.bat` prints the laptop's current LAN address and port to enter into the Android app if it is not detected automatically.

Optional commands, from the repository root:

```powershell
python start_backend.py --setup
python start_backend.py --test
python start_backend.py --check
python start_backend.py --setup-ai
python start_backend.py --check-ai
python start_backend.py --test-ai
python start_backend.py --index-ai
python start_backend.py --index-ai --rebuild
python start_backend.py --import-artifacts
python start_backend.py --import-artifacts --dry-run
python start_backend.py --stop
```

`--setup` runs the full idempotent first-time setup: creates `backend\.env` from `backend\.env.example` if missing, extracts `museum-images.zip`/`image-assets.zip` from the project root into `artifact_image_source\` if present, starts MongoDB and Qdrant through Docker Compose, installs AI dependencies, imports any artifact ZIP files found in `artifact_image_source\`, and creates the first admin account. It never deletes or rebuilds existing MongoDB data, Qdrant collections, or vectors.

Running the launcher with no flags starts MongoDB with Docker Compose, prepares `backend\.venv` when needed, creates `backend\.env` only if it is missing, checks the setup, creates the first admin account when needed, and starts FastAPI listening on `0.0.0.0:8000` (reachable from other devices on the same LAN).

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

## Bulk Artifact Import

Place removable ZIP collections under the repository-root folder `artifact_image_source/`. This folder is ignored by Git and is only an import source for *new* artifacts; the app continues to store managed runtime images under `backend/uploads/images/`. Restoring an existing installation's dataset does not use this folder — see "Handoff Quick Start" for the MongoDB/Qdrant/managed-image migration procedure.

To add new artifacts, a single ZIP named `museum-images.zip` or `image-assets.zip` can be placed in the project root, containing the per-artifact ZIPs (and optional category subfolders) below. `setup.bat` (`python start_backend.py --setup`) extracts it into `artifact_image_source/` and imports it automatically.

Example (either placed directly, or inside `museum-images.zip`):

```text
artifact_image_source/
    Agricultural Tools/
        Wooden Plow.zip
        Hand Sickle.zip
    Rice Mortar.zip
```

Each ZIP represents one artifact. The ZIP filename becomes the initial artifact name with only `.zip` removed, so `Wooden Plow.zip` becomes `Wooden Plow`. A direct parent folder becomes the initial category; ZIPs directly inside `artifact_image_source/` use `Uncategorized`. Imported records are created as Draft artifacts with generated temporary codes such as `DRAFT-WOODEN-PLOW-A13F72`. Administrators can rename the artifact, replace the temporary code with the official accession code, choose a managed category, add custom metadata fields, and publish later.

Run from `backend`:

```powershell
python -m scripts.import_artifact_zips
python -m scripts.import_artifact_zips --source ../artifact_image_source
python -m scripts.import_artifact_zips --dry-run
python -m scripts.import_artifact_zips --update-existing
```

Or from the repository root:

```powershell
python start_backend.py --import-artifacts
python start_backend.py --import-artifacts --dry-run
python start_backend.py --setup
```

The importer validates ZIP paths defensively, rejects path traversal and nested archives, ignores unrelated files, validates real JPEG/JPG/PNG/WEBP image contents, and imports every valid image in an accepted artifact ZIP. It keeps archive-level safety thresholds for suspicious entry counts and total uncompressed size, but there is no product-level maximum number of images per artifact. Source ZIPs are never deleted and are never required at runtime after import.

Main-image selection is deterministic. The importer first looks for `main`, `primary`, or `cover` image filenames, then `01_main`, `01_primary`, or `01_cover` prefixes, all case-insensitive. If no explicit main image is found, it sorts image filenames with case-insensitive natural sorting and uses the first image, setting `primary_image_needs_review=true` so the Android admin app shows a review banner before publishing.

Duplicate protection uses internal import provenance: `import_source_name` and the ZIP SHA-256 `import_source_hash`. Re-running the importer skips previously imported ZIPs by default. Use `--update-existing` explicitly to replace images for matching imported artifacts. AI indexing is separate by default; use the existing `Index Artifact Images` workflow or the indexing commands above after artifact data is reviewed.

Artifact metadata now supports typed `custom_fields` for Additional Information. Supported field types are Text, Number with optional unit, Long Text, and Date. Standard fields such as name, artifact code, category, description, primary image, images, and status remain first-class fields.

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

Open the repository root in Android Studio and run the `android:app` configuration. No `API_BASE_URL` configuration is required to connect to a backend — see "Android Backend Connection" below. `local.properties`/`-PAPI_BASE_URL` still exist only to seed `BuildConfig.API_BASE_URL`, a legacy compile-time value that is no longer used to reach the backend (kept only so existing Gradle tooling and tests that reference it keep working). `DEBUG_ADMIN_EMAIL`/`DEBUG_ADMIN_PASSWORD` in `local.properties` still prefill the debug admin login form.

Use [local.properties.example](local.properties.example) as the safe tracked template. Never commit real passwords. `local.properties` must remain ignored by Git.

## Android Backend Connection

The Android app never has a laptop IP compiled into it. `BackendConnectionManager` (`android/app/src/main/java/com/example/museumapp/data/network/`) is the single source of truth for the backend address, for both Visitor and Admin flows:

```text
App opens
  |
  v
Try the saved backend address (DataStore) -> health check
  |
  +-- succeeds --> Connect, keep using it
  |
  +-- fails ------> Scan this device's current /24 Wi-Fi subnet for the health endpoint
                       |
                       +-- found -----> Connect and save the address
                       |
                       +-- not found -> Show "Backend Not Found" with manual IP:port entry
```

No mDNS/NSD/Bonjour/cloud discovery is used. An address is only saved after its `/api/v1/health` response is verified. If the phone later moves to a different Wi-Fi/hotspot, the next app launch retries the saved address, fails fast, rescans the new subnet, and falls back to manual entry — it never gets stuck retrying a dead address. To force a reconnect without restarting the phone, close and reopen the app.

Requirements this depends on, both already satisfied by `run.bat`/Uvicorn:

- The backend must listen on `0.0.0.0` (not `127.0.0.1`), so it is reachable from other LAN devices.
- The phone and the laptop must be on the same Wi-Fi network or the same mobile hotspot.
- Windows Firewall must allow inbound TCP on the backend port (see below).

Verify that port `8000` is listening:

```powershell
Get-NetTCPConnection -LocalPort 8000 -State Listen
```

`setup.bat` attempts to add this rule automatically (it needs an Administrator prompt to succeed). If it could not, add it manually with this Administrator PowerShell command — the name matches what `setup.bat` checks for, so it will not be duplicated on the next run:

```powershell
New-NetFirewallRule `
  -DisplayName "Museum Backend 8000" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 8000 `
  -Action Allow
```

Cleartext (plain HTTP) LAN traffic is permitted in both debug and release builds (`android/app/src/main/res/xml/network_security_config.xml`) because the backend has no TLS certificate and this app is never distributed outside the local museum network.

Artifact image URLs (`primary_image_url`, etc.) are generated by the backend from the incoming request's own host (`request.base_url`), so they automatically match whatever LAN address the phone used to reach the backend — no separate image-URL configuration is needed.

### Physical Phone LAN Verification

Start the backend (`run.bat`, or manually):

```powershell
cd C:\Capstone-client\Museum_App\backend
.venv\Scripts\activate
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Check these URLs on the Windows computer:

```text
http://localhost:8000/api/v1/health
http://<laptop LAN IP>:8000/api/v1/health
```

Before opening the app, check the same LAN URL in the physical phone's browser. If the phone browser cannot open the health endpoint, the issue is outside the Android app: check same Wi-Fi, temporarily disable mobile data, disable VPNs, avoid guest Wi-Fi/AP isolation, confirm the Windows Firewall rule above, verify the laptop's current LAN IP (`run.bat` prints it), and confirm the backend is still running.

Android checks:

```powershell
.\gradlew.bat :android:app:testDebugUnitTest
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
student_id=<demo student id>
student_email=<demo student email>
student_password=<demo student password, not committed>
```

Run this sequence:

1. `Health check`
2. `AI health`
3. `Guest Session`
4. `Visitor Me`
5. `Public Home`
6. `Public News`
7. `Public Announcements`
8. `Public Articles`
9. `Museum Information`
10. `Programs`
11. `Visitor Artifact List`
12. `Visitor Recognition`
13. `Admin login`
14. `Dashboard`
15. `Create artifact`
16. `List artifacts`
17. `AI Warmup`
18. `AI Warmup Status`
19. `Index all artifacts`
20. `Index status`
21. `Recognize image`

`Guest Session`, `Student Register`, and `Student Login` store `visitor_token` automatically for visitor requests. `Admin login` stores `access_token` automatically for admin requests. For Give 1-only regression checks, `postman/Museum_Guide_Give1.postman_collection.json` is still available.

Postman stays configured for requests from the Windows development computer; it does not configure the Android app.

## API Summary

Authentication:

```text
POST /api/v1/auth/login
GET  /api/v1/auth/me
POST /api/v1/visitor/guest-session
POST /api/v1/student/register
POST /api/v1/student/login
GET  /api/v1/visitor/me
POST /api/v1/visitor/logout
```

Public content:

```text
GET /api/v1/public/home
GET /api/v1/public/news
GET /api/v1/public/news/{id}
GET /api/v1/public/announcements
GET /api/v1/public/articles
GET /api/v1/public/articles/{id}
GET /api/v1/public/museum-info
GET /api/v1/public/programs
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
GET    /api/v1/visitor/artifacts
GET    /api/v1/visitor/artifacts/{artifact_id}
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
artifact_categories
students
guest_sessions
news
announcements
museum_articles
museum_information
programs
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

These commands cover the preserved Give 1/Give 2 admin behavior and the Give 3 visitor additions. Backend tests include guest session creation, guest validation, student registration/login, duplicate handling, password hashing, typed JWT roles, visitor/admin authorization boundaries, token expiry, public content filtering, visitor artifact access, and visitor recognition authorization. Android unit tests include startup routing, visitor form validation, visitor navigation destinations, asset URI constants, Home state, scan readiness, and settings logout.

## Known Limitations

Public news, announcements, articles, museum information, and programs are read-only for Android visitors in this phase. Administrator content management screens are intentionally excluded until a separate approved phase. Museum information must be configured in MongoDB or seeded with clearly labeled demonstration data. Map actions are disabled until latitude and longitude are configured. Student ID verification against a PSAU registrar source, email verification, password reset, visitor analytics, and continuous recognition are not implemented in this phase.

If the laptop switches to a different Wi-Fi/hotspot network while the Android app is already open, the app does not detect this mid-session; close and reopen the app to trigger a fresh backend search. The local-network backend scan only checks the phone's own `/24` subnet (matching how a Wi-Fi/hotspot network is normally addressed) and does not scan across VPNs, VLANs, or routed subnets.

## Troubleshooting

**Python missing** — `setup.bat`/`run.bat` report this and exit. Install Python 3.12 or 3.13 from [python.org](https://www.python.org/downloads/) and make sure it is on PATH, then re-run.

**Docker missing** — `setup.bat` reports this and exits. Install Docker Desktop, then re-run `setup.bat`.

**Docker installed but not running** — `setup.bat` reports this and exits. Start Docker Desktop, wait for it to finish starting, then re-run `setup.bat`.

**MongoDB restore failure** (`migration\restore.ps1`) — confirm Docker is running and `mongorestore` (MongoDB Database Tools) is installed and on PATH, then re-run `migration\restore.ps1`. See [migration/README.md](migration/README.md).

**Qdrant restore failure** — confirm Docker is running; if no Qdrant volume backup is available, the Qdrant collection can be re-created empty and re-indexed from MongoDB with `python start_backend.py --index-ai --rebuild`, but only do this when there genuinely is no existing vector backup to restore — never as a shortcut when real vectors already exist.

**Backend not starting / port `8000` already in use** — another process is already listening on port 8000. Check with `netstat -ano | findstr :8000`, stop the conflicting process, then run `run.bat` again.

**MongoDB or Qdrant will not start** — check whether another process already holds their ports (`netstat -ano | findstr :27018` for MongoDB, `netstat -ano | findstr :6333` for Qdrant — MongoDB is published on host port `27018`, not the default `27017`, to avoid colliding with a MongoDB service already installed on the laptop).

**AI health is degraded** — check `python start_backend.py --check-ai`, confirm Qdrant is reachable at `http://localhost:6333`, and confirm the AI/OpenCLIP dependencies installed correctly during setup. If recognition returns `no_match`, first confirm that artifacts have indexed vectors, then tune thresholds with real museum photos.

**Phone cannot reach the backend** — confirm the phone and laptop are on the same Wi-Fi network or hotspot, confirm `run.bat` is still running and shows `Status: RUNNING`, and confirm the Windows Firewall rule for port `8000` exists (see below).

**Wrong LAN IP selected** — a laptop with more than one active network adapter (for example a Docker-internal virtual adapter) can have `run.bat` highlight an address on the wrong adapter. Check every address under `Network:` and use the one on the same network as the phone, not only the single highlighted `Android Backend Address:` line.

**Windows Firewall blocking port 8000** — `setup.bat` tries to add a scoped inbound rule automatically, but this normally requires an Administrator prompt to succeed. If it could not be added, run this once in an Administrator PowerShell:

```powershell
New-NetFirewallRule -DisplayName "Museum Backend 8000" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow
```

**Phone and laptop on different networks** — the app's local-network scan only checks the phone's own `/24` subnet, so it cannot find a backend on a different network. Connect both devices to the same Wi-Fi network or the same mobile hotspot.

**App shows "Backend Not Found"** — enter the laptop's `host:port` from the `Network:` list printed by `run.bat` into the app's manual-entry field. A wrong or stale address is never retried forever or silently kept; the app only saves an address after a real health check against it succeeds.

**Stale saved IP after changing networks** — this is expected and self-resolving: on the next app launch (or after closing and reopening the app), the app retries the saved address, fails fast if it no longer works, rescans the current network, and falls back to manual entry if nothing is found. See "Network changes" above.

**General rule when troubleshooting a migration**: never delete MongoDB or Qdrant Docker volumes, and never rebuild the vector database, as a troubleshooting shortcut. Investigate the actual cause (wrong port, wrong `.env` value, Docker not running, missing backup file) first.
