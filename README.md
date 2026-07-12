# Museum Guide System

Give 1 implements the Admin Artifact Management System: secure admin login, JWT session handling, artifact CRUD, multipart image upload, local image storage, and Android Jetpack Compose admin screens.

Out of scope for this phase: OpenCLIP, Qdrant, visitor scanning, 3D viewing, analytics, and reports.

## Backend Setup

```bash
cd backend
python -m venv .venv
```

Windows:

```bash
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

Edit `.env` before starting the backend:

- Replace `JWT_SECRET_KEY` with a long random secret.
- Keep `MONGODB_URL=mongodb://localhost:27017` for local MongoDB.
- Set `ADMIN_EMAIL`, `ADMIN_PASSWORD`, and `ADMIN_FULL_NAME` for the first admin account.

Start MongoDB Community Edition and confirm the connection in MongoDB Compass.

Create the initial admin account:

```bash
python -m scripts.create_admin
```

Run the backend:

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Verification URLs:

```text
Swagger UI: http://localhost:8000/docs
Health check: http://localhost:8000/api/v1/health
Uploaded images: http://localhost:8000/uploads/images/<filename>
```

## Backend Tests

The backend tests use `mongomock` and a temporary upload directory, so they do not modify development data.

```bash
cd backend
.venv\Scripts\activate
python -m pytest -q
```

## Android Setup

Open the repository root in Android Studio and run the `android:app` configuration.

The API base URL is configured in:

```text
android/app/build.gradle.kts
```

Default emulator URL:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
```

`10.0.2.2` is the Android emulator alias for the host machine. For a physical Android device, change the base URL to your computer LAN IP, for example `http://192.168.1.20:8000/`, run Uvicorn with `--host 0.0.0.0`, and make sure the phone and computer are on the same network.

Local HTTP development is enabled in:

```text
android/app/src/main/res/xml/network_security_config.xml
android/app/src/main/AndroidManifest.xml
```

The app uses Android Photo Picker through `ActivityResultContracts.PickMultipleVisualMedia`, so no storage permission is needed for supported Android versions.

Build from a shell with Gradle available:

```bash
gradle :android:app:assembleDebug
```

Or use Android Studio:

```text
File > Sync Project with Gradle Files
Build > Make Project
Run > app
```

## Postman

Import:

```text
postman/Museum_Guide_Give1.postman_collection.json
```

Set collection variables:

```text
base_url=http://localhost:8000
admin_email=<created admin email>
admin_password=<created admin password>
```

Run `Admin login` first. The collection stores `access_token` automatically for protected artifact requests.

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

MongoDB collections:

```text
users
artifacts
```

Uploaded files are stored under:

```text
backend/uploads/images/
```
