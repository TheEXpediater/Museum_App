Museum App — VPS Migration & Production Deployment Engineering Guide

Mission

Migrate the existing working Museum App from a developer-PC/LAN backend to a stable Hostinger VPS deployment without destabilizing the application, losing museum data, exposing internal services, or removing the existing local/offline deployment path.

This phase is a migration and production-hardening phase, not a product rewrite.

The target result is:

Android App
    |
    | HTTPS
    v
https://api.museuma7k9x3.tech
    |
    v
Caddy reverse proxy
    |
    v
FastAPI backend
    |
    +-- MongoDB
    +-- Qdrant
    +-- OpenCLIP CPU
    +-- persistent uploads/images/models

The developer PC must not be required for normal hosted client/student testing after migration.

The existing local/LAN museum deployment must remain available unless a later requirement explicitly removes it.

Current Priority Order

Preserve the working Museum App.

Protect existing museum data before any migration or restore.

Prepare and secure the VPS.

Deploy the existing backend architecture with the smallest production-specific changes.

Restore MongoDB, Qdrant, uploads, and existing published 3D assets safely.

Verify all restored data before exposing the API publicly.

Configure DNS and valid HTTPS.

Update Android networking to support the hosted endpoint without breaking local/LAN mode.

Build and place a new test APK in the repository root.

Verify the complete Android-to-VPS flow on an emulator or physical device.

Document exact rollback and operational procedures.

Do not add unrelated features during migration.

Do not refactor working application architecture merely because the server environment changed.

Engineering Method

Work from evidence.

The loop is:

observe
-> inventory
-> backup
-> define one migration step
-> execute
-> verify
-> compare with baseline
-> keep or roll back

Before editing or migrating:

Read this file completely.

Inspect the current Git working tree.

Inspect actual Docker, backend, Android, backup, and migration configuration.

Record current local service/data counts.

Confirm existing backups are usable.

Confirm the VPS state.

Do not assume filenames, environment-variable names, ports, service names, or backup formats.

Prefer:

small diffs

separate production configuration

explicit validation

persistent storage

logical database backups

reversible steps

least-privilege network exposure

one source of truth for hosted API configuration

verified Android runtime behavior

Avoid:

destructive volume replacement without backups

exposing MongoDB or Qdrant publicly

hardcoding passwords or JWT secrets

weakening TLS

disabling certificate validation

making the private repository public for deployment

deleting local/LAN support

re-indexing Qdrant as a shortcut when valid vectors already exist

regenerating existing 3D assets unnecessarily

large unrelated refactors

declaring success because only Docker or Gradle says BUILD SUCCESSFUL

Confirmed VPS Baseline

The current Hostinger VPS has been manually verified.

Provider: Hostinger VPS
Public IPv4: 72.61.120.104
OS: Ubuntu 24.04.4 LTS
Architecture: x86_64
RAM: approximately 7.8 GiB
Disk: approximately 95 GiB free at initial setup
Docker: 29.8.1
Docker Compose: v5.5.1
Initial swap: 0 B
SSH user: root
SSH key on development PC:
C:\Users\Admin\.ssh\id_ed25519_hostinger_vps

Production domain:

museuma7k9x3.tech

Production API hostname:

api.museuma7k9x3.tech

Target public API URL:

https://api.museuma7k9x3.tech/

The api DNS A record is intended to point to:

72.61.120.104

DNS propagation must be verified before troubleshooting ACME/TLS issuance.

Existing Working System

The following are existing working product capabilities and must remain working through migration:

Kotlin Android app

Jetpack Compose UI

FastAPI backend

MongoDB persistence

Qdrant vector search

OpenCLIP startup/readiness and artifact recognition

Admin authentication

Visitor flow

Student flow and account approval lifecycle

Artifact CRUD

Artifact images

Museum catalogue

Admin Accounts management

published 3D model metadata and files

Admin 3D Preview

Accept / Reject workflow

Visitor View 3D

Android persistent model cache

SceneView rendering

Do not rewrite these systems merely for VPS deployment.

Existing 3D Architecture Must Be Preserved

The current 3D workflow is not the focus of migration.

Quick AI path:

source image
-> TripoSR
-> candidate GLB
-> validation
-> Admin Preview
-> Accept / Reject
-> publish
-> Visitor

Advanced path:

multiple overlapping images
-> COLMAP
-> reconstruction quality gate
-> candidate GLB
-> validation
-> Admin Preview
-> Accept / Reject
-> publish
-> Visitor

For initial VPS production deployment:

existing published GLB files must remain viewable

GLB metadata and file paths must remain consistent

do not require online COLMAP/TripoSR generation to declare the core deployment ready

if new 3D generation is not production-ready on the VPS, document that limitation separately

do not delete, regenerate, or replace published models as part of migration

Authorization for Routine Work

Proceed autonomously for normal non-destructive engineering operations required for this migration.

Routine authorized operations include:

reading/searching project files

git status, git diff, git diff --stat

running tests and builds

SSH read-only inspection

installing normal deployment dependencies on the new VPS

creating application directories

creating production configuration files

building Docker images

starting/restarting the Museum App Docker stack

inspecting logs

health checks

DNS lookups

safe firewall configuration after SSH is explicitly allowed

creating a swapfile

copying migration data to a new deployment directory

creating a new test APK after a successful build

Ask before genuinely destructive actions, including:

deleting source files or data

deleting existing Docker volumes

deleting backups

git reset --hard

git clean

force-pushing Git history

overwriting a destination database that already contains newer production data

clearing Android application data if preserving state matters

uninstalling an application as a workaround

A local Claude Code permission dialog may still require tool-level approval; do not repeatedly ask conversationally for commands already covered by this authorization.

Phase 0 — Git and Local Baseline

Before changing anything:

git status --short
git diff --stat
git diff

Record which existing changes are unrelated to migration.

Do not overwrite or revert unrelated work.

Inspect at minimum:

CLAUDE.md
compose.yaml
Dockerfile / backend Dockerfile(s)
backend/.env.example
backend/app/config.py
backend/app/main.py
backend/app/database/*
backend/app/routes/*
backend/app/repositories/*
backend/app/auth/*
backend/app/ai/*
backend/app/vector/*
backend/app/services/*
backend/uploads/*
migration/*
backup.ps1
restore.ps1
setup.bat
start_backend.py
.gitignore
android/app/build.gradle.kts
android/app/src/main/AndroidManifest.xml
Android networking / Retrofit configuration
Backend connection/discovery classes
SessionManager and auth/session routing

Do not assume the names in this guide are exact when the repository shows otherwise.

Phase 1 — Local Data Inventory

Before migration, record a local baseline.

At minimum record:

MongoDB database name

MongoDB collection names

artifact count

student count

admin/user count if applicable

guest/session collections if migration should preserve them

museum-information record count

announcement/news count where relevant

Qdrant collection name

Qdrant vector count

vector dimension

representative vector point IDs if practical

uploads directory path

image file count

total uploads size

model/GLB file count

total model size

OpenCLIP model name/pretrained profile

current backend health result

current AI health result

The migration is not verified until destination counts and representative records/files are compared with this baseline.

Phase 2 — Create Fresh Migration Backup

Do not rely only on an old backup if current local state changed.

Inspect existing backup scripts first.

Create a fresh migration snapshot using the repository's supported mechanism where possible.

Preferred MongoDB method:

mongodump
-> transferable dump directory/archive
-> mongorestore on destination

Do not copy a live MongoDB data directory while MongoDB is running unless the repository already implements a validated snapshot procedure.

For Qdrant:

inspect current Qdrant version

inspect existing migration/backup format

prefer a Qdrant snapshot if supported by the existing deployment/version

otherwise use the existing validated Docker-volume backup only after confirming compatibility

record vector count before backup

For uploads:

preserve full relative directory structure

include artifact images

include published model files

include any thumbnails/media relied on by stored database paths

calculate file counts and preferably checksums for representative or all critical files

Do not migrate virtual environments, temporary build products, caches, Gradle caches, or experimental files unless required.

Phase 3 — VPS Preflight

Connect using the verified SSH key.

From Windows CMD, the verified form is:

ssh -i "%USERPROFILE%\.ssh\id_ed25519_hostinger_vps" root@72.61.120.104

On the VPS verify:

cat /etc/os-release
uname -m
free -h
df -h
docker --version
docker compose version
docker ps -a

The VPS previously reported a pending system restart.

Before production services exist:

inspect package updates

apply appropriate Ubuntu security/normal package updates

reboot once if required

reconnect

verify Docker after reboot

Do not perform repeated unnecessary reboots.

Phase 4 — Swap

The initial VPS had no swap.

For an approximately 8 GiB RAM server running MongoDB, Qdrant, FastAPI, and CPU PyTorch/OpenCLIP, create approximately 4 GiB of persistent swap unless current server evidence suggests otherwise.

Use a secure root-owned swap file with mode 600.

Persist it in /etc/fstab.

Verify:

free -h
swapon --show

Swap is emergency memory headroom, not a reason to ignore memory usage.

Phase 5 — Production Directory

Use a predictable server location, for example:

/opt/museum-app

Keep source/configuration separate from persistent application data where practical.

Example conceptual layout:

/opt/museum-app/
    source/
    deploy/
    backups/

Do not force this exact layout if the repository's existing architecture has a cleaner convention.

Phase 6 — Source Deployment

Prefer secure Git deployment if repository authentication is already available.

Do not make a private repository public just to clone it.

If Git authentication is not available, use scp, rsync, or another safe transfer method.

Do not transfer unnecessary local directories such as:

.venv

Gradle caches

.idea

local temporary output

model experiment directories

generated APKs except when intentionally archived

The server should build from source/configuration, not from the Windows development environment.

Phase 7 — Production Compose

Preserve the existing local compose file.

Prefer a production-specific file such as:

compose.prod.yaml

The production deployment should contain only the services required for normal hosted operation.

Expected core services:

caddy
backend
mongodb
qdrant

OpenCLIP may run in the backend process if that matches the current working architecture.

Do not keep a one-off AI tools/profile container running continuously unless the repository proves it is required.

Use restart policies appropriate for long-running services.

Phase 8 — Docker Networking

Use an internal Docker network for service-to-service traffic.

Inside Docker, containers must use service names, not localhost, for other services.

Conceptually:

mongodb://mongodb:27017
http://qdrant:6333

Use the repository's actual environment-variable names.

FastAPI should listen inside the container on its expected application port, normally 8000.

Caddy should reverse proxy to the backend over the Docker network.

Phase 9 — Public Port Policy

Internet-facing services should be limited to:

22/tcp  SSH
80/tcp  HTTP / ACME / redirect
443/tcp HTTPS

Do not publicly expose:

27017 MongoDB
27018 alternate local Mongo host port
6333 Qdrant HTTP
6334 Qdrant gRPC
8000 FastAPI

The production Compose file should not publish MongoDB/Qdrant ports to 0.0.0.0.

If FastAPI requires a host-side binding for diagnostics, bind only to loopback and justify it; otherwise keep it Docker-internal behind Caddy.

Phase 10 — Firewall

Inspect current Hostinger firewall and UFW state before changing anything.

Before enabling any restrictive firewall:

confirm the active SSH connection

explicitly allow port 22

allow ports 80 and 443

only then enable/apply restrictions

Never lock out SSH.

Do not create public rules for MongoDB, Qdrant, or FastAPI.

After configuration, verify listening sockets using tools such as:

ss -tulpn

Phase 11 — Production Environment and Secrets

Create the production .env only on the VPS or through another untracked secure mechanism.

Do not commit production secrets.

Generate a fresh JWT signing secret, for example:

openssl rand -hex 32

Do not print secret values in final reports.

Do not reuse test secrets.

Do not hardcode admin credentials into Android.

Configure:

production MongoDB URL

production database name

Qdrant URL

Qdrant collection

OpenCLIP model/pretrained configuration

OpenCLIP CPU device

upload directory

JWT secret

admin/bootstrap configuration only if the existing backend uses it

production CORS behavior based on actual API clients

Do not invent environment variables. Inspect Settings/config first.

Phase 12 — Caddy and HTTPS

Use Caddy unless the repository already contains a working supported reverse proxy that should be preserved.

Conceptual Caddyfile:

api.museuma7k9x3.tech {
    reverse_proxy backend:8000
}

When Caddy itself runs in Docker, proxy to the Docker service name, not 127.0.0.1, unless network topology proves otherwise.

Persist Caddy data/config volumes so certificates survive container recreation.

Before expecting certificate issuance, verify DNS:

api.museuma7k9x3.tech -> 72.61.120.104

Use dig, nslookup, or authoritative/public resolvers.

Temporary DNS NXDOMAIN during propagation is not an application failure.

Do not:

use a self-signed certificate for the final client API

disable Android certificate verification

use curl -k as final proof of TLS correctness

Final required endpoint:

https://api.museuma7k9x3.tech/

Phase 13 — Start Empty Production Services First

Before restoring live museum state, start the production stack in a controlled manner.

Verify:

MongoDB container stays healthy

Qdrant container stays healthy

backend starts with expected configuration

Caddy configuration parses

persistent directories/volumes are writable

If backend requires data to start, document the dependency and adjust order minimally.

Do not restore into an unknown existing production database.

Phase 14 — MongoDB Restore

Restore from the fresh migration backup.

Before restore:

verify destination database is new/empty or explicitly approved for replacement

record destination state

preserve the migration backup

Use the safest compatible mongorestore workflow.

After restore verify:

artifact count

student count

admin/user count

museum information

announcements/news if applicable

representative artifact fields

student account status fields

no password hashes are exposed through APIs

Do not assume restore succeeded because the command returned exit code 0; compare data.

Phase 15 — Qdrant Restore

Restore using the backup mechanism that matches the existing Qdrant version and data format.

Prefer a supported Qdrant snapshot when available.

Verify after restore:

expected collection exists

vector dimension matches OpenCLIP output

vector count matches baseline or is explicitly explained

representative points have expected payloads

AI health recognizes the collection

Do not rebuild/re-index all artifact vectors unless restore cannot be performed safely or a compatibility problem is proven.

If re-indexing becomes necessary, stop and report the reason before doing it if it could change production data materially.

Phase 16 — Uploads and Existing 3D Files

Transfer the complete required uploads hierarchy while preserving relative paths.

Verify:

representative artifact image URLs resolve

database paths map to actual files

published GLB files exist

GLB files can be served/downloaded by the backend

no path assumes a Windows drive letter

Linux file permissions allow the backend to read files and write new uploads

Compare destination file count and total size with the source baseline.

Use checksums for important/representative files.

Phase 17 — OpenCLIP on VPS

Use the current working OpenCLIP model configuration.

Expected current profile should be verified from repository configuration, likely:

model: ViT-B-32
pretrained: laion2b_s34b_b79k
device: cpu

Do not invent or change the model during migration.

Allow the model to download on the VPS if that is simpler and safer than transferring a cache, provided model provenance/configuration matches the working system.

Verify:

model initializes

backend AI health is ready

Qdrant collection is reachable

embedding dimension matches the existing collection

one real recognition request succeeds

Monitor memory during first model load.

Do not interpret a slow first initialization as failure without checking logs/resources.

Phase 18 — Resource Safety

The VPS is approximately 8 GiB RAM with 4 GiB planned swap.

Monitor:

free -h
docker stats

Avoid running unnecessary memory-heavy tasks concurrently with first OpenCLIP initialization.

Do not deploy new 3D generation workloads as part of initial readiness unless required.

If OOM behavior appears:

identify the process consuming memory

inspect container limits/current workload

fix the actual resource issue

do not disable health checks or randomly reduce functionality

Phase 19 — Health Checks Before Public Android Testing

Verify from the VPS/container network first.

Test the existing backend health endpoint.

Expected checks include:

API responds

MongoDB connected

uploads writable/readable

Qdrant connected

OpenCLIP ready

Then verify through Caddy/HTTPS:

https://api.museuma7k9x3.tech/<actual-health-route>

Use the repository's real route; do not invent one.

Phase 20 — Hosted Android Networking

Inspect the actual Android networking implementation before editing.

The application previously supported local/LAN backend discovery.

Do not remove local mode.

Required behavior:

Hosted/client mode:
https://api.museuma7k9x3.tech/

Local/offline museum mode:
existing LAN discovery / configured local server

Use one source of truth for the hosted base URL.

Do not scatter the production URL across Compose screens or repositories.

If an existing interceptor rewrites scheme/host/port, ensure it does not rewrite the HTTPS production endpoint into HTTP or force port 8000.

Prefer a clear endpoint representation such as a complete base URL when it reduces ambiguity.

Preserve existing repository/API abstractions.

Do not bypass Retrofit/auth/session handling.

Phase 21 — Android Security

Production-hosted requests must use valid HTTPS.

Do not:

globally permit cleartext traffic for production traffic

disable hostname verification

install an unsafe trust manager

ignore TLS errors

A debug/local network security override may remain for local LAN development if already required, but it must not weaken the production hosted path.

Do not compile administrator passwords into the APK.

Phase 22 — Android Functional Verification Against VPS

Test while proving the app is using the VPS and not the developer PC.

Where practical, stop the local backend during the hosted test.

Verify at minimum:

application launches

visitor flow

guest session

student registration -> pending

pending student login denied correctly

admin login

admin Accounts screen

admin activation

activated student login

persistent student session

artifact catalogue

artifact images

artifact details

OpenCLIP recognition

existing published 3D model download/view

logout/session behavior

Do not declare success only from unit tests.

Phase 23 — Backend Tests

Run the relevant backend automated test suite after migration-related backend/config changes.

Use the repository's existing Python environment/command.

Expected current regression suite is approximately 200+ tests, but use the actual repository result.

Do not make unit tests execute full OpenCLIP inference unnecessarily.

Use explicit VPS smoke tests for real AI/network verification.

Phase 24 — Android Tests and Build

Run:

.\gradlew.bat :android:app:testDebugUnitTest
.\gradlew.bat :android:app:assembleDebug

Both must pass before packaging the test APK.

The APK must then be tested on an Android Studio emulator or physical Android device.

Phase 25 — Root Test APK Requirement

After the hosted endpoint is configured and the Android build succeeds, create a NEW test APK in the repository root.

Do not assume the Gradle output path; locate the actual generated debug APK after assembleDebug.

Copy it to the repository root with this exact filename unless a collision requires explicit reporting:

Museum_App_VPS_Test.apk

Requirements:

it must be a fresh APK from the successful current build

it must use the new hosted/VPS-capable networking configuration

it must not contain hardcoded administrator credentials

it must not contain production server secrets

it must not be copied before build success

report the source APK path and root destination path

report file size and preferably SHA-256

If Museum_App_VPS_Test.apk already exists in the root, replace it only after a new build succeeds, preserving the old file until the new APK is ready.

Do not commit the APK automatically unless repository policy explicitly allows binary releases.

Phase 26 — Install and Test Root APK

If an emulator/device is available:

adb devices
adb install -r <root>/Museum_App_VPS_Test.apk

Launch the real launcher activity.

Verify no fatal startup exception.

Then verify representative hosted flows.

The build is not considered validated merely because installation succeeds.

Phase 27 — Restart Survival

Production services should recover automatically.

Verify:

docker compose -f <production-compose> restart

Then confirm:

MongoDB data remains

Qdrant collection remains

uploads remain

backend returns healthy

Caddy serves valid HTTPS

OpenCLIP returns to ready state

A full VPS reboot test is recommended after the initial deployment is stable.

Before reboot:

ensure configuration is persisted

ensure restart policies are correct

ensure SSH remains allowed

After reboot verify the full stack again.

Phase 28 — External Exposure Validation

From outside the VPS verify:

Expected reachable:

22
80
443

Expected not publicly reachable:

27017
27018
6333
6334
8000

Do not publish database dashboards publicly merely for convenience.

Phase 29 — Rollback Strategy

Every major migration stage must have a rollback path.

Before replacing anything, know how to restore:

previous source revision

previous .env

MongoDB backup

Qdrant backup/snapshot

uploads backup

Caddy configuration

Android previous working endpoint/build

Do not delete migration backups immediately after successful deployment.

Keep at least one known-good migration snapshot until client testing confirms stability.

Phase 30 — Production Backup Plan

After migration, create a simple repeatable backup procedure for the VPS.

At minimum cover:

MongoDB logical backup

Qdrant snapshot/backup

uploads/models

important production configuration excluding secret disclosure

Backups must be restorable, not merely copied.

Document destination, retention, and restore command/steps.

Do not rely solely on the VPS provider snapshot as the only application-level backup.

Migration Acceptance Test

Migration is complete only when all critical checks pass.

Infrastructure

VPS SSH                  PASS / FAIL
Docker                   PASS / FAIL
Compose                  PASS / FAIL
Swap                     PASS / FAIL
Firewall                 PASS / FAIL
DNS                      PASS / FAIL
HTTPS certificate        PASS / FAIL

Backend

FastAPI                  PASS / FAIL
MongoDB                  PASS / FAIL
Qdrant                   PASS / FAIL
OpenCLIP                 PASS / FAIL
Uploads                  PASS / FAIL
Published 3D files       PASS / FAIL

Data

Mongo artifact count     PASS / FAIL
Student/account data     PASS / FAIL
Qdrant vector count      PASS / FAIL
Artifact images          PASS / FAIL
Representative GLB       PASS / FAIL

Android

Launch                    PASS / FAIL
Visitor flow              PASS / FAIL
Student registration      PASS / FAIL
Admin approval            PASS / FAIL
Student login             PASS / FAIL
Catalogue                 PASS / FAIL
Images                    PASS / FAIL
AI scan                   PASS / FAIL
3D view                   PASS / FAIL
Session persistence       PASS / FAIL

Automated Verification

Backend pytest            PASS / FAIL
Android unit tests        PASS / FAIL
assembleDebug             PASS / FAIL
Root APK created          PASS / FAIL
Root APK installed        PASS / FAIL

Do not report migration complete while a critical item is unverified.

Final Reporting Format

Report evidence, not effort.

VPS

OS

architecture

RAM

swap

disk

Docker version

Compose version

Network

VPS IP

domain

API URL

DNS result

TLS result

public ports

Production Stack

compose file

running services

restart policies

persistent volumes

Data Migration

source backup timestamp

MongoDB restore result

artifact count source/destination

student/account count source/destination

Qdrant collection

vector count source/destination

uploads source/destination count/size

GLB count/source/destination

AI

OpenCLIP model

pretrained profile

device

readiness

real recognition smoke test

Android

hosted API configuration mechanism

confirmation local/LAN mode remains

unit-test result

build result

emulator/device result

root APK path

APK size

APK SHA-256

Security

Explicitly confirm:

MongoDB is not publicly exposed

Qdrant is not publicly exposed

FastAPI port 8000 is not directly public

API uses valid HTTPS

production secrets are not committed

admin credentials are not embedded in the APK

WordPress/unrelated DNS records were not modified

Remaining Limitations

List only real remaining limitations, including new 3D generation on VPS if it is intentionally deferred.

Final Decision Rules

KEEP a migration change when it clearly improves one or more of:

production safety

reproducibility

remote availability

data durability

network security

Android hosted connectivity

operational simplicity

REVERT or redesign a migration change when:

it exposes an internal datastore publicly

it removes working local/LAN capability without requirement

it requires hardcoded credentials

it loses or silently changes museum data

it introduces unnecessary architecture

it weakens HTTPS

it breaks existing Android flows

it makes rollback unclear

The priority is a stable, secure, recoverable deployment of the existing Museum App, not a rewrite.