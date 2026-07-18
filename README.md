# Museum Guide System

Give 1 implements the Admin Artifact Management System: secure admin login, JWT session handling, artifact CRUD, multipart image upload, local image storage, and Android Jetpack Compose admin screens.

Out of scope for this phase: OpenCLIP, Qdrant, visitor scanning, 3D viewing, analytics, and reports.

## Simplified Backend Startup

From the repository root, run:

```powershell
python start_backend.py
```

Optional commands:

```powershell
python start_backend.py --test
python start_backend.py --check
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
postman/Museum_Guide_Give1.postman_collection.json
```

Set collection variables:

```text
base_url=http://localhost:8000
admin_email=<created admin email>
admin_password=<created admin password>
```

Run this sequence:

1. `Health check`
2. `Admin login`
3. `Current admin`
4. `List artifacts`

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

MongoDB collections:

```text
users
artifacts
```

Uploaded files are stored under:

```text
backend/uploads/images/
```
