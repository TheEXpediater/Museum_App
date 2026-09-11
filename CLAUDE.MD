Museum App — Engineering Guide

Mission

Maintain and extend the Museum Guide System with the smallest correct changes.

The system is an Android museum guide backed by a local Windows laptop. Existing MongoDB data, Qdrant vectors, uploaded images, authentication, OpenCLIP recognition, published GLBs, Admin flows, Visitor flows, and working UI are valuable state and must be preserved.

Current priorities:

Keep the existing backend and Android application stable.

Make Quick AI 3D Preview the normal, practical 3D creation path.

Use a free local pretrained image-to-3D model before paid cloud providers.

Keep COLMAP as an optional advanced photogrammetry path.

Reuse the same GLB validation, review, publication, cache, and SceneView pipeline for every generation method.

Move 3D management into a dedicated Admin 3D area with immediate progress feedback and automatic status polling.

Clearly disclose that AI-generated geometry can contain inferred surfaces.

The selected free local model is TripoSR.

TripoSR is used for inference only. Do not train or fine-tune it for this capstone.

Engineering Method

Work from evidence.

Before editing:

Reproduce the requirement or failure.

Find the narrowest component that owns it.

Read only the relevant files.

Form one concrete hypothesis.

Make the smallest change that can prove it.

Run the nearest executable verification immediately.

Preserve working code outside the failure boundary.

Prefer:

simple code

existing abstractions

explicit state

small diffs

targeted tests

real integration checks

measured runtime behavior

Avoid:

repository-wide rewrites

speculative refactors

duplicate networking/repository/ViewModel stacks

hidden background behavior

invented API contracts

invented confidence scores

destructive troubleshooting

dependency upgrades without evidence

If a command fails, investigate the first meaningful error.

Do not claim success because code compiles.

Existing Architecture

Android

Kotlin

Jetpack Compose

Material 3

minSdk 26

targetSdk 35

Java/Kotlin target 17

SceneView

Retrofit / OkHttp / Moshi

existing repositories and ViewModels

runtime backend discovery

Important root:

android/app/src/main/java/com/example/museumapp/

Important areas:

data/api/

data/model/

data/network/

data/repository/

data/session/

model3d/

ui/admin/

ui/visitor/

Do not create another networking stack.
Do not hardcode a laptop IP.

Backend

FastAPI

Python

MongoDB

Qdrant

OpenCLIP

local uploads

COLMAP

reconstruction jobs

GLB validation/versioning/publication

Canonical startup:

run.bat

which invokes:

start_backend.py

The Windows-host FastAPI process remains the authoritative API process.

Protection Rules

The following must continue to work:

Admin login

Guest flow

Student flow

artifact CRUD

categories

artifact gallery

Admin artifact details

Visitor artifact details

backend discovery

OpenCLIP recognition

Qdrant recognition

Visitor scanning

existing uploads

existing reconstruction jobs

existing accepted/published GLBs

Admin 3D Preview

Visitor View 3D

model cache

SHA verification

narration

Never delete as a shortcut:

MongoDB volumes

Qdrant volumes

existing artifact images

published GLBs

valid model history

A failed new generation must leave the currently published model unchanged.

Do not rebuild Qdrant unless corruption is proven.

OpenCLIP

OpenCLIP is a backend service, not a per-device Android model.

When recognition AI is enabled, backend startup must warm OpenCLIP automatically.

A new Android device must not require an Admin to open Recognition first.

Required:

local startup warmup enabled

cached weights reused

/api/v1/ai/health exposes loading / ready / failed

Qdrant remains independent of Android device state

no re-index as a model-loading workaround

OpenCLIP recognition and AI 3D generation are separate subsystems.

3D Product Definition

There are two creation modes.

Quick AI 3D Preview — default

Purpose:

Create a recognizable, approximate 3D visualization quickly from a good photograph.

Use for normal museum Admin workflow.

Missing or unseen surfaces can be inferred by AI.

This is not metrically exact archival reconstruction.

Photogrammetry — advanced

Engine:

COLMAP

Purpose:

Create geometry from matched overlapping photographs.

Use when source images have enough overlap, texture, and viewpoint diversity.

COLMAP failure must not block Quick AI Preview or ordinary museum use.

Free Local Model

TripoSR

Use the official TripoSR implementation as the default free local image-to-3D provider.

Reasons:

pretrained

MIT license

no paid API

no project-specific training

local inference

CPU fallback

evaluation chunking to reduce VRAM

GLB output support

optional texture baking

Important:

TripoSR is a single-image generator.

Its CLI can receive more than one image path, but those images are processed as separate generation inputs. It does not fuse arbitrary front/left/back/right photographs into one model.

Never represent TripoSR as true multi-view fusion.

Optional cloud or future local multi-view providers may continue to exist behind the provider abstraction.

Do not make a much heavier model the default when it cannot realistically run on the deployment machine.

Runtime Isolation

Do not install TripoSR's PyTorch/runtime dependencies into the main FastAPI environment unless compatibility is proven.

Use a separate local runtime, for example:

tools/triposr/

with its own environment and model cache.

Architecture:

FastAPI
   |
   | creates local 3D job
   v
TripoSR subprocess
   |
   v
staged candidate GLB
   |
   v
existing GLB validator
   |
   v
Admin review

Reasons:

avoid OpenCLIP/PyTorch dependency conflicts

isolate OOM/crashes

allow CPU fallback

release model memory after generation

keep normal backend startup lightweight

One local generation at a time is acceptable.

Do not introduce Redis or Celery.

Provider Boundary

Keep process execution outside FastAPI routes.

Use the existing model3d service architecture.

Conceptual interface:

LocalAi3DProvider
    detect()
    health()
    generate()

Implementation:

TripoSrProvider

Detection should verify:

runtime folder

Python executable

imports

model/weights availability

output folder

usable device configuration

If TripoSR is unavailable:

backend still starts

MongoDB still works

OpenCLIP still works

Qdrant still works

COLMAP still works

existing GLBs remain viewable

Only new local AI generation is disabled.

Configuration

Keep machine configuration in backend/.env.

Suggested keys:

LOCAL_AI_3D_ENABLED=true
LOCAL_AI_3D_PROVIDER=triposr
TRIPOSR_ROOT=
TRIPOSR_PYTHON=
TRIPOSR_MODEL_PATH=
TRIPOSR_DEVICE=auto
TRIPOSR_CHUNK_SIZE=
TRIPOSR_MC_RESOLUTION=
TRIPOSR_TEXTURE_RESOLUTION=
TRIPOSR_BAKE_TEXTURE=true
TRIPOSR_CPU_FALLBACK=true

Update .env.example with safe placeholders.

Do not commit local paths or secrets.

Low-Resource Policy

The current target may not have enough VRAM for TripoSR defaults.

Do not assume the GPU will work.

TripoSR's default single-image inference is documented at about 6 GB VRAM, so a 4 GB GPU must be treated as constrained.

Policy:

detect CUDA

use configured conservative settings

if GPU inference fails with OOM or unsupported CUDA behavior, terminate the worker

retry once on CPU when fallback is enabled

record which device actually generated the model

never crash FastAPI because a 3D worker OOMs

Smaller TripoSR evaluation chunks reduce VRAM but increase runtime.

Do not invent one magic chunk size.

Benchmark the actual machine and persist the working profile.

Admin may show:

Generating locally on CPU. This can take several minutes.

That is acceptable.

Quick AI Input

Do not require 30–200 photos.

For TripoSR choose one primary generation image.

Recommended primary image:

entire artifact visible

sharp

strong foreground/background separation

minimal occlusion

soft even light

little glare

no motion blur

preferably a three-quarter angle that reveals depth

Admin UI:

Quick AI 3D Preview

Primary image
[ thumbnail ]

[ Change Primary Image ]

Reference photos: 27

Generator:
Local AI

[ Generate Preview ]

Other artifact photos can remain reference material for Admin review.

Do not claim those extra images were used by TripoSR if they were not.

Estimated Photo Support vs AI Inference

The product may show a percentage, but it must be honest.

Do not claim:

exact model confidence

exact percentage of hallucinated polygons

scientific coverage

Use:

Estimated photo-supported coverage

Estimated AI-inferred coverage

Always show Estimated.

Canonical major view regions:

Front

Right

Back

Left

Top

Bottom

For the image(s) actually consumed by the generator, record which major regions are visibly represented.

Example:

Visible in generation input

[x] Front
[x] Right
[x] Top
[ ] Back
[ ] Left
[ ] Bottom

Coverage:

supported regions / 6

Inference:

100 - supported

Example:

3 of 6 regions represented:

Estimated photo-supported coverage: 50%

Estimated AI-inferred coverage: 50%

Round to whole percentages.

Do not create false precision.

If the visible regions cannot be determined, show:

Coverage estimate unavailable

For TripoSR, only its primary input image counts as generation evidence.

Reference photos not sent to the model do not reduce the inferred percentage.

A future true multi-view provider may count every view it actually consumes.

AI Disclosure

Every AI candidate must show:

AI-generated 3D preview

and:

Areas not visible in the generation input may have been estimated by AI and may not exactly match the physical artifact.

Before Accept, display:

generation method

primary source image

estimated photo-supported coverage

estimated AI-inferred coverage

model validation

model size

Preview

Accept

Reject

Never present AI Preview as archival measurement data.

Generation Metadata

Persist typed generation method:

colmap
triposr
ai_multiview

Admin labels:

Photogrammetry

Local AI Preview

AI Multi-View Preview

Persist generation method on draft and published model.

Do not expose provider internals unnecessarily to Visitor DTOs.

One Shared GLB Pipeline

Every generator must converge here:

generator
    |
    v
staged GLB
    |
    v
validate
    |
    v
SHA-256
    |
    v
Admin Preview
    |
    +-- Reject -> keep current published model
    |
    +-- Accept
          |
          v
      publish version
          |
          v
      Visitor API
          |
          v
      Android cache
          |
          v
       SceneView

Never build a provider-specific Android renderer.

Android consumes validated GLB.

Staging / Validation

Never overwrite a published model during generation.

Use staging.

Example:

backend/uploads/reconstruction/<artifact_id>/
    source/
    workspace/
    ai_staging/
    output/

backend/uploads/models3d/<artifact_id>/
    model-v1.glb
    model-v2.glb

Validate:

file exists

size > 0

within configured limit

valid GLB

geometry exists

finite bounds

vertices > 0

faces > 0 where applicable

materials/textures inspected

SHA-256 generated

Do not re-export a valid textured AI model unnecessarily.

Only repair missing normals/materials when they are genuinely absent.

Admin 3D UX

Move 3D management into a dedicated Admin area.

Prefer:

Artifact Details

[ Details ] [ Images ] [ 3D Model ]

or equivalent Material 3 navigation.

Do not cram full reconstruction controls into the normal Details content.

3D Model tab

Published Model

Status: Ready
Generation: Local AI Preview
Version: 5

[ Preview ]

Create New Preview

(*) Quick AI Preview
( ) Photogrammetry

[ Continue ]

Quick AI is default.

COLMAP is advanced.

Quick AI Flow

3D Model tab
   |
   v
Quick AI Preview
   |
   v
select primary image
   |
   v
mark visible regions
   |
   v
Generate
   |
   v
background job
   |
   v
TripoSR
   |
   v
validate
   |
   v
pending_review
   |
   v
Preview
   |
   +-- Reject
   |
   +-- Accept

Do not require COLMAP preflight before Quick AI generation.

Photogrammetry Flow

COLMAP remains available.

For source selection:

all eligible images selected by default

Select All

Clear

Selected X / Y

Show readable counts:

Source photos

Selected

Usable

Registered

Registration %

Flow:

select photos
   ->
Run Check
   ->
feature extraction
   ->
matching
   ->
sparse reconstruction
   ->
quality result
   ->
build if suitable

If quality is insufficient:

Try Quick AI Preview

Do not force hundreds of photos.

Progress UX

Any long operation must produce immediate feedback.

Never let a button appear to do nothing.

AI example:

Generating 3D Preview

Preparing image...
Removing background...
Loading local model...
Generating geometry...
Baking texture...
Validating GLB...

This may take several minutes.

[ progress ]

COLMAP example:

Checking Photos

Extracting features...
Matching photographs...
Building sparse reconstruction...
Assessing quality...

Android automatically polls active jobs.

No manual refresh.
No leave-and-return requirement.
No duplicate job from Compose recomposition.

Polling stops on terminal state.

Job States

Use MongoDB-backed job state and existing worker patterns.

Possible local-AI states:

ai_queued
ai_preparing
ai_loading_model
ai_generating
ai_texturing
ai_validating
pending_review
failed
interrupted

Map them to human UI.

Keep technical stack traces in logs.

Do not hold one HTTP request open through full inference.

Image Selection Rules

COLMAP

All eligible images checked by default.

Buttons:

Select All
Clear

Show selected count.

Do not preselect backend-marked unreadable files.

TripoSR

One primary generation image.

Allow replacing the primary.

Other images are references.

Do not pretend they are fused.

SceneView

The existing renderer remains the shared viewer.

Do not rewrite it while integrating TripoSR unless an actual regression proves it is involved.

It must:

download published GLB

verify SHA

atomically persist cache

load Filament/SceneView on correct thread

frame actual bounds

provide usable lighting

show the object

support orbit/zoom

show actionable errors instead of blank black/gray canvas

Admin Preview and Visitor use the same fundamental renderer.

Publication

No generated model is automatically public.

Required:

generated
  ->
validated
  ->
Admin review
  ->
Accept / Reject

Accept promotes version/path/SHA/size/generation method/coverage metadata.

Reject preserves the current published model.

Failure preserves the current published model.

Offline Behavior

Normal museum operation remains local after setup.

OpenCLIP, Qdrant, MongoDB, backend, existing models, cached GLBs, and SceneView must not require a cloud API.

TripoSR should work offline after its runtime and weights are installed.

First-time dependency/model download may require internet unless bundled.

Setup / Handoff

Do not immediately modify the main handoff installer.

First prove TripoSR separately:

isolated environment installs

official test image generates GLB

real museum artifact generates GLB

GLB validates

SceneView renders it

measure runtime

measure memory where practical

establish a safe GPU/CPU profile

Then make optional installer support.

A failed TripoSR install must not break normal museum use.

Real Acceptance Gate

Before claiming local AI 3D is complete:

use a real museum photo

run actual TripoSR, not a mock

produce GLB

texture/appearance is generated when enabled

validate

compute SHA

preview in Admin

object is recognizable

show Estimated coverage

show AI disclosure

Reject preserves current model

Accept a usable model

Visitor downloads it

Visitor visibly renders it

reopen uses cache

Compilation and unit tests are not enough.

Testing

Backend

Keep existing tests.

Add targeted tests for:

provider disabled

provider unavailable

detection

command construction

timeout

subprocess failure

CUDA OOM

configured CPU fallback

missing result

invalid GLB

valid GLB

staging

SHA

published-model preservation

generation-method persistence

coverage calculation

coverage unavailable

job transitions

interrupted job

Accept

Reject

Visitor hidden before Accept

Visitor exposed after Accept

Mock heavy inference in normal tests.

Create a separate explicit integration test for real TripoSR inference.

Android

Test:

dedicated 3D navigation

Quick AI default

COLMAP advanced option

primary-image selection

region selection

Estimated coverage labels

AI disclosure

immediate progress

polling active/terminal behavior

no duplicate job on recomposition

failure

pending review

Preview

Accept

Reject

existing published Preview remains available

Run:

backend pytest
.\\gradlew.bat :android:app:testDebugUnitTest
.\\gradlew.bat :android:app:assembleDebug

Git Discipline

Before substantial work:

git status
git branch --show-current
git log -5 --oneline

Do not destroy uncommitted work.
Do not push unless explicitly requested.
Do not commit backend/.env, local.properties, machine paths, secrets, or large model weights casually.

Final Verification

Prove:

backend starts

MongoDB healthy

OpenCLIP ready without Admin priming

Qdrant intact

artifacts intact

existing GLBs intact

dedicated Admin 3D screen works

Quick AI does not depend on COLMAP

TripoSR real inference works on the real machine, or an exact resource/runtime limitation is proven

candidate GLB validates

Admin visibly previews it

Estimated coverage is displayed honestly

AI disclosure is present

Reject preserves old model

Accept publishes candidate

Visitor renders published model

second open uses cache

COLMAP still works as advanced mode

Android unit tests pass

APK builds

Reporting

Report evidence only.

Local AI

detected runtime

model

license

execution device

runtime

measured memory if available

actual settings

Quick AI

primary source image

represented regions

Estimated coverage

job states

output

GLB

size

vertices

faces

geometry

materials/textures

bounds

SHA

validation

Admin

3D navigation

progress UX

visible Preview

Accept/Reject

Visitor

published version

visible model

cache reuse

COLMAP

advanced path remains functional

Verification

backend tests

Android tests

assembleDebug

real TripoSR integration test

Use PASS/FAIL.

Do not report speculative issues.
Do not say the feature works when only mocks passed.