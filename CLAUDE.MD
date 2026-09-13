Museum App — 3D Quality Engineering Guide

Mission

Improve the quality of the Museum App's 3D artifact models without destabilizing the working system.

The app already has a functioning end-to-end 3D pipeline. This phase is not about rebuilding architecture. It is about making generated models more recognizable, visually faithful, consistently textured, and efficient for Android.

Current priorities, in order:

Preserve the working Museum App.

Establish a measurable 3D-quality baseline.

Improve source-image capture and selection before changing models or architecture.

Improve TripoSR output quality with controlled, evidence-based parameter experiments.

Keep COLMAP as the advanced photogrammetry path and improve its input guidance rather than brute-forcing image count.

Optimize accepted GLBs for Android without destroying visible quality.

Improve Admin guidance so a non-technical museum user can provide better source images.

Verify every claimed improvement on a real artifact in the actual Admin and Visitor viewer.

Do not switch to another 3D model merely because another repository looks more impressive.

Do not add complexity before proving the current bottleneck.

Engineering Method

Work from evidence.

The loop is:

observe
-> define one hypothesis
-> change one variable
-> measure
-> compare against baseline
-> keep or revert

Before editing:

Reproduce the current output.

Save baseline metrics and screenshots.

Identify the likely quality bottleneck.

Change one factor at a time.

Run the smallest meaningful experiment.

Compare against the same artifact and source image.

Keep only changes that clearly improve the result.

Prefer small diffs, measurable experiments, existing abstractions, real artifact tests, reproducible settings, simple capture guidance, explicit tradeoffs, and visual verification.

Avoid repository-wide audits, speculative refactors, changing several quality variables at once, replacing TripoSR before benchmarking it properly, assuming more photos always means better quality, assuming white background is always better, fake confidence scores, huge models that make Android rendering worse, and quality claims based only on file size or triangle count.

If a result looks better but is slower or much larger, record the tradeoff.

If a result is technically valid but visually worse, it is worse.

Current Working System

The following already work and must remain working:

FastAPI

MongoDB

Qdrant

OpenCLIP startup/readiness

Admin authentication

Visitor flow

artifact CRUD

artifact images

Quick AI 3D Preview

TripoSR local generation

COLMAP advanced photogrammetry

GLB validation

SHA-256

model versioning

Admin 3D Preview

Accept / Reject

Visitor View 3D

Android persistent model cache

SceneView rendering

light neutral viewer background

current working orbit / zoom behavior

Do not rewrite these systems for this phase.

A failed experiment must never replace the current published model.

Current 3D Product Architecture

Quick AI Preview — default

one primary artifact image
-> TripoSR
-> candidate GLB
-> validation
-> Admin Preview
-> Accept / Reject
-> Visitor

Use when the goal is a fast, recognizable 3D representation.

AI may infer unseen surfaces.

It is not an archival or metrically exact reconstruction.

Advanced Photogrammetry

many overlapping artifact photos
-> COLMAP
-> reconstruction quality gate
-> candidate GLB
-> validation
-> Admin Preview
-> Accept / Reject
-> Visitor

Use when the source photo set is strong enough for geometric reconstruction.

COLMAP is not the default Quick AI workflow.

Phase Goal: Better 3D Models

"Better" must be explicit.

A better model should improve one or more of:

recognizability

silhouette accuracy

front/side/top shape consistency

texture/color similarity

visible detail

absence of holes or broken geometry

absence of obvious AI distortions

stable camera framing

practical GLB size

smooth Android rendering

generation reliability

Do not optimize triangle count for its own sake.

Do not optimize texture resolution for its own sake.

The goal is the best useful visitor-facing artifact model for the available hardware.

Baseline First

Before changing TripoSR or capture behavior, establish a baseline using one real artifact.

Prefer an artifact that already generated successfully, such as the accepted woven basket / salikap artifact.

Record:

artifact
primary input image
input dimensions
background type
view angle
lighting description
TripoSR device
chunk size
marching-cubes resolution
texture resolution
texture enabled
generation runtime
GLB size
vertex count
face count
material count
texture count
bounds
Admin screenshot
Visitor screenshot

Also record a simple engineering quality score:

recognizability: 1-5
shape fidelity: 1-5
texture fidelity: 1-5
unseen-side plausibility: 1-5
viewer usability: 1-5

These are comparison scores, not scientific confidence values.

Keep the baseline artifact and screenshots for comparison.

Capture Quality Comes Before Model Complexity

Do not conclude that TripoSR is inadequate until the input image has been optimized.

For Quick AI Preview, the input should usually be:

one artifact only

entire object visible

sharp

no motion blur

soft, even lighting

minimal harsh shadow

minimal reflection/glare

high foreground/background contrast

little background clutter

object not touching frame edges

enough empty margin for background removal

useful three-quarter perspective when possible

A good three-quarter view often provides more 3D information than a straight front photograph.

Example:

camera
   \
    \
     [artifact]

roughly 30-45 degrees off the front

Do not assume pure white is always correct.

Choose a matte background that contrasts with the artifact.

Examples:

light beige artifact -> medium/dark neutral background
dark brown artifact -> light gray/off-white background
white artifact -> gray/blue-gray background

Avoid glossy backgrounds, reflective tables, hard flash, patterned cloth, clutter, cropped edges, and strong backlighting.

Quick AI Capture Experiment

Before touching model architecture, test source-view quality.

For the same artifact, capture or choose:

A. straight front
B. 30-degree three-quarter
C. 45-degree three-quarter
D. slightly elevated three-quarter
E. slightly lower three-quarter

Generate each with the same TripoSR settings.

Only the source image changes.

Compare the outputs side by side.

Do not change texture resolution, chunk size, or marching-cubes resolution during this experiment.

Choose the best view empirically.

Persist capture guidance in the Admin UI only after this test.

Recommended Capture Guidance in Admin

Keep guidance short:

For a better AI 3D preview:

• Show the whole artifact.
• Use a sharp photo.
• Use even lighting.
• Use a plain contrasting background.
• Prefer a three-quarter angle that shows depth.
• Avoid glare, blur, and cropped edges.

Do not overwhelm Quick AI users with photogrammetry terminology.

Background Removal / Object Isolation

TripoSR quality may depend strongly on foreground extraction.

Inspect the actual preprocessing used by the installed TripoSR implementation and current tools/triposr/infer.py.

Do not invent preprocessing flags.

Determine:

whether background removal is enabled

what performs it

whether alpha/mask quality is good

whether artifact edges are being cut

whether shadows become foreground

whether transparent output is handled correctly

Test background removal separately on the baseline artifact.

Save the processed foreground used by inference in an ignored debug/test output directory.

If edge masking damages the artifact, fix preprocessing rather than compensating with more mesh resolution.

TripoSR Quality Experiments

Do not change all parameters at once.

Inspect the installed TripoSR version and actual supported parameters.

Never invent CLI flags.

Experiment 1 — Source View

Keep all model settings fixed.

Change only the source image/view.

Select the best source image.

Experiment 2 — Mesh Resolution

Using the best source image, test a small number of practical supported marching-cubes/mesh resolutions.

For each, record:

runtime

RAM/VRAM

vertices/faces

GLB size

silhouette/detail quality

Android render smoothness

Do not select the highest value automatically.

Choose the lowest setting that preserves visibly useful geometry.

Experiment 3 — Texture Resolution

Using the selected mesh setting, test a small number of practical supported texture resolutions.

Measure:

texture clarity

artifact color fidelity

seams

GLB size

Android loading/render behavior

Choose the smallest texture that still looks good on the target device.

Experiment 4 — Chunk Size

Chunk size is primarily a memory/performance setting.

Do not treat it as a visual-quality setting unless the implementation proves otherwise.

Choose a value that is stable on the generation PC.

Experiment 5 — Device

GPU and CPU output should be compared only if there is evidence they differ.

Prefer the stable execution profile.

Do not force GPU merely because it is faster.

One Variable At A Time

Every experiment records:

baseline
changed variable
old value
new value
result
decision

Example:

Experiment: texture resolution

baseline: 512
candidate: 1024

result:
- texture visibly clearer
- GLB 3.4 MB -> 5.8 MB
- Android still smooth

decision:
KEEP 1024

If the difference is not visibly meaningful:

REVERT.

3D Quality Scorecard

For each candidate:

Artifact: Salikap #4

Input quality:
4/5

Shape:
4/5

Texture:
4/5

Silhouette:
4/5

Unseen-side plausibility:
3/5

GLB size:
3.4 MB

Generation:
2m 14s

Android:
smooth

Decision:
KEEP / REJECT

These are engineering comparison scores, not scientific measurements.

TripoSR Constraints

TripoSR remains a single-image generator.

Do not pretend it fuses multiple arbitrary views.

Additional images may be reference images for Admin comparison, but they are not generation evidence unless the model actually consumes them.

If a future provider supports true multi-view conditioning, add it behind the existing provider abstraction.

Do not replace the current provider in this phase.

AI Disclosure

Continue showing:

AI-generated 3D preview.

Areas not visible in the generation input may have been estimated by AI and may not exactly match the physical artifact.

Continue using:

Estimated photo-supported coverage

Estimated AI-inferred coverage

Do not convert this into a fake confidence metric.

COLMAP Quality Phase

COLMAP remains valuable for higher-fidelity geometry when capture is correct.

Do not judge COLMAP from datasets where only a few photographs registered.

For a controlled COLMAP test, keep the artifact stationary and move the camera around it.

Do not rotate the object against a stationary background unless the pipeline deliberately masks the background.

Capture concept:

high ring:
  camera positions around artifact

middle ring:
  camera positions around artifact

low ring:
  camera positions around artifact

plus top/detail views

Use strong overlap between adjacent views.

Do not prescribe hundreds of images.

A smaller set of high-quality overlapping images is better than many blurred or redundant images.

Record:

source count

usable count

registered count

registered ratio

sparse points

reprojection error

dense/sparse output type

final mesh

texture quality

COLMAP Capture Guidance

Admin guidance for Photogrammetry must differ from Quick AI guidance:

For photogrammetry:

• Keep the artifact still.
• Walk the camera around it.
• Keep neighboring photos highly overlapping.
• Capture low, middle, and high angles.
• Keep lighting consistent.
• Avoid blur and glare.
• Do not make large jumps between viewpoints.

Do not claim any fixed photo count guarantees success.

GLB Optimization

A visually better source model can still be a poor mobile asset.

After selecting a good candidate, measure:

GLB size

vertices

faces

texture dimensions

material count

Android load time

Android render smoothness

Optimize only after the best-looking source model is selected.

Do not optimize a poor model.

Any simplification must preserve:

silhouette

recognizable details

texture readability

normals

UVs

materials

Keep the original candidate before optimization.

Mobile Target

The final asset is for Android SceneView, not desktop rendering.

Prefer:

recognizable
+
clean
+
small enough
+
fast enough

over:

maximum polygons
+
maximum texture size

A model that looks only slightly better but loads much slower may be a worse product.

Measure on the actual Android test device/emulator.

Shared GLB Pipeline

Do not change the working publication path:

TripoSR or COLMAP
-> staged GLB
-> validate
-> SHA-256
-> Admin Preview
-> Accept / Reject
-> publish
-> Visitor API
-> Android persistent cache
-> SceneView

No provider-specific Android renderer.

No direct publication before Admin review.

Viewer

The viewer is already functional.

Do not change viewer interaction during model-quality experiments unless a reproduced viewer problem prevents evaluation.

Preserve:

light neutral background

neutral lighting

camera framing

model centering

orbit

zoom

shared Admin/Visitor renderer

Viewer UX experiments are a separate phase from generation-quality experiments.

This prevents confusing a better camera with a better model.

Experiment Files

Do not commit experiment screenshots, generated GLBs, model weights, caches, or virtual environments by default.

Keep reproducible scripts and settings.

Generated comparison assets belong in ignored development/test directories.

Before any commit:

git status
git diff --stat

Verify no large generated artifacts are accidentally staged.

Experiment Records

Create a small local report for each experiment.

Example ignored path:

tools/triposr/experiments/<artifact>/<run-id>/metrics.json

Record:

timestamp
artifact
source image
source dimensions
source view label
device
TripoSR settings
runtime
GLB metrics
quality scores
decision

Do not introduce a new database subsystem for experiments.

Keep it simple.

Optional Recommended-Photo Heuristic

Only after controlled experiments, consider a lightweight recommendation heuristic.

It may consider:

image sharpness

useful object size in frame

resolution

foreground/background separation

orientation

obvious clipping

Do not introduce another large AI model merely to choose the source image.

Do not claim the heuristic guarantees the best 3D result.

Admin can always choose another image.

Real Acceptance Test

This phase is complete only after a real before/after comparison.

Use the same artifact.

Required:

regenerate or establish the current baseline

save baseline metrics

optimize source capture/view

select best source view

optimize one TripoSR parameter at a time

choose best practical settings

generate final candidate

validate GLB

preview in Admin

compare baseline vs optimized model visually

Accept only if visibly better

open as Visitor

verify cache reuse

verify Android performance remains acceptable

Do not call the phase complete because tests pass.

The optimized model must visibly improve on the baseline.

Regression Protection

After quality changes verify:

backend starts

MongoDB intact

OpenCLIP ready

Qdrant intact

Quick AI works

COLMAP works

existing published GLBs remain valid

Admin Preview works

Accept/Reject works

Visitor model works

cache works

Do not rebuild Qdrant.

Do not re-index artifacts as a shortcut.

Do not delete existing models.

Tests

Keep automated tests focused.

Backend tests cover changed logic only, for example:

preprocessing

parameter selection/configuration

safe output paths

GLB validation

publication protection

Android tests cover UI only if capture guidance or quality metadata changes.

Do not make normal unit tests run full TripoSR inference.

Use one explicit real-inference integration test.

Run:

backend pytest
.\gradlew.bat :android:app:testDebugUnitTest
.\gradlew.bat :android:app:assembleDebug

Final Decision Rules

Keep a change only when at least one is true:

model is visibly more recognizable

texture is visibly more faithful

geometry is visibly cleaner

same quality is achieved with less runtime

same quality is achieved with smaller GLB

generation becomes more reliable

Admin can more reliably provide good inputs

Reject a change when:

complexity increases without visible benefit

only triangle count increases

only file size increases

one angle improves while others degrade

Android becomes noticeably slower

it depends on fragile hardware behavior

it requires replacing working architecture without evidence

Reporting

Report evidence, not effort.

Baseline

artifact

source image

source view

model settings

runtime

GLB metrics

quality score

Experiments

For each:

hypothesis

one variable changed

baseline value

candidate value

measured result

KEEP / REVERT

Best Capture

background

lighting

source angle

distance/framing

reason selected

Best TripoSR Profile

device

chunk size

mesh resolution

texture resolution

texture enabled

runtime

memory observations

Final GLB

size

vertices

faces

materials

textures

bounds

SHA

Before / After

recognizability

shape

texture

unseen-side plausibility

Android performance

Regression

backend

OpenCLIP

Qdrant

COLMAP

Admin Preview

Visitor

cache

Tests

backend pytest

Android unit tests

assembleDebug

real TripoSR integration

Use PASS / FAIL.

Do not claim "better" unless the before/after comparison supports it.