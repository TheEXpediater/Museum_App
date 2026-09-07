# Museum App — Claude Code Engineering Guide

## Mission

Maintain and extend the existing Museum Guide System without destabilizing working functionality.

Current priorities, in strict order:

1. Recover the current development backend if it does not start.
2. Preserve all existing artifact CRUD, authentication, visitor flows, OpenCLIP recognition, MongoDB data, Qdrant vectors, and uploaded images.
3. Add local/offline 3D artifact reconstruction using COLMAP on the Windows laptop.
4. Produce mobile-ready GLB models.
5. Add Android 3D viewing with persistent local model caching.
6. Add offline-capable artifact narration using Android TextToSpeech.
7. Verify the complete application before declaring the task complete.

This is a development task.

Do not redesign the handoff package or migration workflow unless a current development failure directly originates from code shared with those systems.

---

# Working Style

Use a narrow, hypothesis-driven workflow.

Do not perform a repository-wide audit before starting.

Do not repeatedly rediscover architecture that is already obvious from the repository.

Before reading a file, know why it is relevant.

Before editing a file, identify the concrete failure or requirement it addresses.

Prefer:

* small diffs
* existing abstractions
* existing repositories and ViewModels
* existing Retrofit/Moshi patterns
* existing FastAPI route/service/repository patterns
* targeted tests
* executable verification

Avoid:

* speculative refactors
* replacing working subsystems
* creating duplicate architecture
* unnecessary dependencies
* renaming unrelated classes
* formatting unrelated files
* broad rewrites
* changing migration backups
* deleting Docker volumes
* rebuilding Qdrant as a troubleshooting shortcut

If a command fails, investigate the first meaningful error rather than changing multiple unrelated components.

---

# Existing Architecture

## Android

Language:

* Kotlin

UI:

* Jetpack Compose
* Material 3

Important package:
`android/app/src/main/java/com/example/museumapp/`

Important areas:

* `data/api/`
* `data/model/`
* `data/network/`
* `data/repository/`
* `data/session/`
* `ui/admin/`
* `ui/visitor/`

Visitor artifact details currently live in:

`ui/visitor/artifacts/VisitorArtifactDetailsScreen.kt`

and:

`ui/visitor/artifacts/VisitorArtifactDetailsViewModel.kt`

Admin artifact details currently live in:

`ui/admin/artifactdetails/`

Do not create parallel networking or repository stacks.

Use the existing Retrofit client, repository result model, ViewModels, navigation, Material theme, and backend connection system.

Current Android:

* minSdk 26
* targetSdk 35
* Java/Kotlin target 17

---

## Backend

Framework:

* FastAPI
* Python

Database:

* MongoDB

AI recognition:

* OpenCLIP
* Qdrant

Existing images:

* managed by backend image storage
* ordinary artifact images participate in recognition/indexing

Important paths:

`backend/main.py`

`backend/app/config.py`

`backend/app/routes/artifacts.py`

`backend/app/routes/public.py`

`backend/app/routes/ai.py`

`backend/app/repositories/artifact_repository.py`

`backend/app/services/artifact_indexing_service.py`

`backend/app/services/image_storage.py`

`backend/app/schemas/artifact.py`

`backend/app/schemas/public_content.py`

`start_backend.py`

`run.bat`

`compose.yaml`

The canonical development startup is:

`run.bat`

which invokes:

`start_backend.py`

The development FastAPI process should remain the authoritative API process.

MongoDB and Qdrant may run through Docker.

Do not casually start another backend instance on port 8000.

---

# PHASE 0 — Recover Current Backend First

Do not implement 3D or narration while the current backend is broken.

Reproduce the current failure.

Run from repository root:

```powershell
py start_backend.py --status
py start_backend.py --check
docker compose -f compose.yaml ps
netstat -ano | findstr :8000
py start_backend.py
```

If `py` is unavailable, use the working Python command detected by the repository.

Then verify:

```text
GET http://localhost:8000/api/v1/health
GET http://localhost:8000/api/v1/ai/health
```

Investigate only the files relevant to the observed error.

Likely startup scope:

* `run.bat`
* `start_backend.py`
* `backend/.env`
* `backend/.env.example`
* `backend/main.py`
* `backend/app/config.py`
* `backend/requirements.txt`
* `backend/requirements-ai.txt`
* `compose.yaml`

Do not touch migration backups merely because startup is failing.

Do not delete MongoDB or Qdrant volumes.

Do not reset the database.

Do not rebuild Qdrant unless corruption is actually proven.

Success gate:

* FastAPI starts.
* `/api/v1/health` returns healthy.
* MongoDB connects.
* existing artifact API works.
* existing authentication works.
* existing artifact data remains present.

Only after this gate passes may feature work begin.

---

# 3D ARCHITECTURE

## Core Principle

COLMAP runs on the Windows backend/laptop.

COLMAP must never execute on Android.

Android receives only the final mobile model.

Final distribution format:

`.glb`

The 3D pipeline is:

```text
reconstruction photos
    ->
quality/preflight
    ->
COLMAP feature extraction
    ->
matching
    ->
sparse reconstruction
    ->
sparse quality gate
    ->
dense reconstruction
    ->
fusion
    ->
meshing
    ->
mesh simplification
    ->
texture atlas
    ->
textured PLY
    ->
GLB conversion
    ->
mobile validation
    ->
published model
```

---

# Reconstruction Images Must Be Separate

DO NOT store all reconstruction photographs in normal artifact `image_paths`.

Ordinary artifact images already participate in OpenCLIP/Qdrant indexing.

Create a separate reconstruction source system.

Suggested storage:

```text
backend/uploads/reconstruction/
    <artifact_id>/
        source/
        workspace/
        output/
```

Published models:

```text
backend/uploads/models3d/
    <artifact_id>/
        model-v1.glb
        model-v2.glb
```

The artifact may reference reconstruction images without exposing them in the visitor gallery.

Existing artifact photos may be copied or referenced into the reconstruction source set when appropriate.

Additional photogrammetry photos must not automatically become visitor-gallery images or OpenCLIP recognition vectors.

---

# Suggested 3D State

Persist 3D state with the artifact or an associated reconstruction-job record.

Suggested states:

```text
none
needs_images
ready_for_build
queued
sparse_reconstruction
dense_reconstruction
meshing
texturing
converting
ready
failed
interrupted
```

Suggested stored information:

```text
model_3d_status
model_3d_path
model_3d_version
model_3d_sha256
model_3d_size_bytes
reconstruction_source_count
registered_image_count
registered_image_ratio
sparse_point_count
mean_reprojection_error
reconstruction_error
reconstruction_guidance
model_3d_created_at
```

Use typed Pydantic/Kotlin models rather than unstructured maps where practical.

---

# Admin 3D Workflow

On an existing artifact, provide:

`Create 3D Model`

Flow:

1. Show currently selected reconstruction images.
2. Allow eligible existing artifact images to be selected.
3. Allow many additional reconstruction-only photographs to be uploaded.
4. Run preflight.
5. If inadequate, do NOT run the expensive dense pipeline.
6. Return actionable guidance.
7. Allow the admin to add more images.
8. Re-run preflight.
9. When suitable, enable `Build 3D Model`.
10. Show reconstruction progress.
11. When complete, show model status and preview.
12. Allow deliberate rebuild/replacement.

Never silently replace a working model after a failed rebuild.

Build a new version first.

Switch the published model reference only after validation succeeds.

---

# Image Quality Gate

Do not use only a fixed image-count rule.

Image count is only an initial heuristic.

Recommended implementation defaults may be configurable, but they are not universal photogrammetry laws.

Perform inexpensive preflight checks such as:

* valid image format
* readable image
* sufficient resolution
* duplicates / near duplicates
* obviously unusable images
* source count

Then run COLMAP sparse reconstruction as the important quality gate.

Collect at least:

* source image count
* registered image count
* registered-image ratio
* sparse 3D point count
* reprojection statistics when available

If sparse registration is poor, return:

`needs_images`

with useful guidance such as:

* add more overlapping photographs
* photograph additional sides/angles
* avoid large jumps between viewpoints
* reduce blur
* keep lighting consistent
* avoid reflections/glare where possible

Do not claim a specific missing physical angle unless reconstruction data actually supports that conclusion.

---

# COLMAP Integration

Do not hardcode a Program Files location.

Resolve COLMAP in this order:

1. configured `COLMAP_BIN`
2. PATH
3. known installation candidates only when necessary

Expose COLMAP availability in system status.

Before assuming command-line flags, use the installed version's help output when necessary.

Support CPU operation.

GPU acceleration is optional.

The system must not require NVIDIA/CUDA.

For CPU systems, configure feature extraction and matching appropriately rather than failing because CUDA is unavailable.

Keep COLMAP execution outside the FastAPI request thread.

---

# 3D Job Execution

3D reconstruction may take a long time.

Do not block a FastAPI HTTP request until the entire dense reconstruction finishes.

Create a job.

Recommended architecture for this local system:

* MongoDB stores job state.
* FastAPI creates/controls jobs.
* a dedicated Python worker/subprocess performs COLMAP.
* worker logs are written per job.
* worker updates job status/progress.
* Android polls a status endpoint.

Do not introduce Redis/Celery unless there is a demonstrated requirement.

One reconstruction worker at a time is acceptable for this capstone/local-lab system.

On application restart, detect abandoned `running` jobs and mark them `interrupted` rather than pretending they are still running.

---

# GLB Conversion

COLMAP's textured output is not the final Android delivery asset.

Convert the textured mesh and texture atlas to GLB.

Preferred lightweight conversion path:

* `trimesh`
* `Pillow`

Validate that UV coordinates and textures survive conversion.

Write a focused converter test.

Final model must be a self-contained GLB suitable for SceneView.

Generate:

* SHA-256
* file size
* model version

Do not publish a zero-byte or unreadable GLB.

---

# Mobile Optimization

A museum artifact model should be optimized before Android delivery.

Use mesh simplification.

Keep the original reconstruction workspace separate from the mobile result.

Use configurable quality settings rather than deleting the highest-quality intermediate result.

Reject or warn about unreasonably large mobile models.

Prefer a practical mobile GLB over maximum polygon count.

---

# Backend 3D API

Follow existing FastAPI route/service/repository architecture.

A reasonable API shape is:

```text
POST   /api/v1/artifacts/{id}/3d/images
GET    /api/v1/artifacts/{id}/3d
POST   /api/v1/artifacts/{id}/3d/preflight
POST   /api/v1/artifacts/{id}/3d/build
GET    /api/v1/artifacts/{id}/3d/status
DELETE /api/v1/artifacts/{id}/3d/images/{image_id}
DELETE /api/v1/artifacts/{id}/3d
```

Admin-only operations must remain admin protected.

Visitor artifact responses should expose only what visitors require, for example:

```text
model_3d_available
model_3d_url
model_3d_version
model_3d_sha256
model_3d_size_bytes
```

Do not expose filesystem paths, COLMAP workspace paths, command lines, or internal reconstruction logs to visitors.

---

# Android 3D Viewer

Use SceneView for the viewer.

Add it through the existing Android Gradle module.

Do not build a custom OpenGL renderer.

Visitor artifact details should show:

`View 3D Model`

only when a valid published model exists.

Flow:

```text
user taps View 3D
    ->
check app-private cache
    ->
matching version + SHA exists?
    | yes
    -> open local GLB
    |
    no
    ->
download from backend
    ->
show progress
    ->
verify SHA-256
    ->
atomically move temp file into cache
    ->
open local GLB
```

Never render directly from a partially downloaded file.

Store models in app-private persistent storage, not temporary cache if offline reuse is required.

Suggested structure:

```text
filesDir/
    models3d/
        <artifact_id>/
            <version>.glb
```

When a new server version is published:

* download new version
* verify it
* switch to it
* remove obsolete version after success

A previously downloaded valid model should remain viewable without internet access.

---

# NARRATION ARCHITECTURE

Use Android native:

`android.speech.tts.TextToSpeech`

Do not create a Python TTS server for the first implementation.

Do not require an online API.

Create a reusable Android narration component/service, e.g.:

`ArtifactNarrationController`

Responsibilities:

* initialize TTS
* discover voices
* prefer the required locale
* prefer a voice where network connection is not required
* expose ready/error state
* speak
* stop
* track utterance/chunk progress
* release TTS resources

---

# Narration Text

Build narration from the visitor-visible artifact information.

Suggested order:

1. artifact name
2. historical description
3. origin
4. historical period
5. material
6. dimensions
7. condition
8. metadata sections
9. visitor-visible custom fields
10. optional visitor narration notes

Do not expose private administrator-only notes through narration.

If the product requires administrator-authored narration text, add a clearly visitor-visible field such as:

`narration_notes`

instead of leaking internal notes.

Skip:

* empty strings
* placeholder text
* internal IDs
* URLs
* image filenames
* AI status fields
* backend diagnostics

Do not attempt OCR on artifact photographs merely because narration exists.

Only narrate image captions if actual caption text exists.

---

# Long TTS Content

Android TTS has an input-length limit.

Do not send an arbitrarily large artifact document as one utterance.

Split narration into semantic chunks or sentences below the platform limit.

Use unique utterance IDs and `UtteranceProgressListener`.

Implement:

* Play
* Pause-like behavior using stop + remembered chunk
* Resume
* Stop
* restart from beginning

Clean up TextToSpeech resources correctly.

If no suitable offline voice exists, display a useful message instead of silently switching to a network-required voice.

---

# UI Placement

Visitor artifact details:

```text
[ Listen ]
[ View 3D Model ]
```

Buttons must respect feature availability.

Listen:

* enabled when narratable visitor text exists and TTS is ready.

View 3D:

* visible/enabled only when the model is available or cached.

Use existing Material 3 styling.

Do not redesign the entire artifact details page.

---

# Testing Requirements

## Backend recovery

Verify:

* startup
* health
* MongoDB
* authentication
* existing artifacts
* existing AI health

## 3D backend

Add tests for:

* reconstruction upload separation
* unauthorized access
* nonexistent artifact
* preflight state transitions
* job creation
* failure persistence
* safe paths
* GLB publication
* model versioning
* SHA generation
* visitor exposure only after ready

Mock external COLMAP execution in normal unit tests.

Do not make the main backend test suite perform a full photogrammetry reconstruction.

Add a separate optional integration test for a tiny controlled COLMAP dataset.

## Android

Add tests for:

* new API DTO fields
* model cache decision logic
* checksum verification
* download failure
* cached model reuse
* narration text construction
* empty fields
* metadata ordering
* TTS state logic where testable

Run:

```text
backend pytest
Android unit tests
Gradle assembleDebug
```

Do not claim success merely because code compiles.

---

# Existing-System Protection

The following must continue to work:

* admin login
* guest flow
* student flow
* artifact CRUD
* artifact gallery
* artifact categories
* OpenCLIP indexing
* Qdrant recognition
* visitor camera recognition
* visitor artifact details
* backend auto-discovery
* existing uploaded artifact images

Do not feed reconstruction-only photos into Qdrant.

Do not make COLMAP availability a prerequisite for ordinary museum operation.

If COLMAP is unavailable:

* existing museum system must still start
* artifact CRUD must still work
* recognition must still work
* narration must still work
* existing ready GLB models must still be viewable
* only creation/rebuilding of 3D models should be unavailable

---

# Final Verification Gate

Before completion, prove:

1. current backend starts normally
2. health endpoint works
3. existing artifact is retrievable
4. existing recognition functionality is not broken
5. admin can create/manage a reconstruction image set
6. insufficient reconstruction reports useful guidance
7. reconstruction job can complete on a valid sample
8. final GLB exists and is valid
9. visitor API exposes the model
10. Android downloads the GLB
11. checksum validation succeeds
12. SceneView renders the local GLB
13. subsequent view uses cached model
14. narration reads visitor-visible artifact information
15. offline-capable TTS voice is preferred
16. APK builds successfully

---

# Reporting

When finished, report only:

## Backend failure

* root cause
* exact fix
* proof it now starts

## 3D implementation

* files changed
* pipeline implemented
* COLMAP detection/status
* API endpoints
* storage locations
* GLB conversion

## Android 3D

* SceneView integration
* download/cache behavior
* viewer behavior

## Narration

* narrated fields
* offline voice behavior
* controls

## Verification

* commands/tests run
* pass/fail results
* remaining limitations

Do not provide a giant repository audit.

Do not report speculative issues that were not encountered.
