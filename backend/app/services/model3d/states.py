from __future__ import annotations

NONE = "none"
NEEDS_IMAGES = "needs_images"
READY_FOR_BUILD = "ready_for_build"
QUEUED = "queued"
SPARSE_RECONSTRUCTION = "sparse_reconstruction"
DENSE_RECONSTRUCTION = "dense_reconstruction"
MESHING = "meshing"
TEXTURING = "texturing"
CONVERTING = "converting"
PENDING_REVIEW = "pending_review"
READY = "ready"
FAILED = "failed"
INTERRUPTED = "interrupted"

# AI multi-view job stages (see ai_generation_service.py). Kept distinct from the COLMAP stage
# names above so job/status UI copy can tell the two pipelines apart, but they converge on the
# same PENDING_REVIEW / READY / FAILED terminal states as COLMAP so Accept/Reject, versioning,
# and Android polling all keep working unmodified for either generation method.
AI_QUEUED = "ai_queued"
AI_GENERATING = "ai_generating"
AI_DOWNLOADING = "ai_downloading"
AI_VALIDATING = "ai_validating"

ALL_STATES = (
    NONE,
    NEEDS_IMAGES,
    READY_FOR_BUILD,
    QUEUED,
    SPARSE_RECONSTRUCTION,
    DENSE_RECONSTRUCTION,
    MESHING,
    TEXTURING,
    CONVERTING,
    AI_QUEUED,
    AI_GENERATING,
    AI_DOWNLOADING,
    AI_VALIDATING,
    PENDING_REVIEW,
    READY,
    FAILED,
    INTERRUPTED,
)

# Statuses a running job may hold. If the process restarts while a job is in one of these
# states, the job was abandoned mid-flight and must be reconciled to INTERRUPTED rather than
# left looking like it is still progressing.
ACTIVE_JOB_STATES = (
    QUEUED,
    SPARSE_RECONSTRUCTION,
    DENSE_RECONSTRUCTION,
    MESHING,
    TEXTURING,
    CONVERTING,
    AI_QUEUED,
    AI_GENERATING,
    AI_DOWNLOADING,
    AI_VALIDATING,
)

# Artifact-level states from which a new build may be launched (initial build or a deliberate
# rebuild/replacement of a previously published model). PENDING_REVIEW is included so an admin
# can discard an unreviewed draft by simply rebuilding rather than having to reject first.
BUILDABLE_STATES = (
    READY_FOR_BUILD,
    PENDING_REVIEW,
    READY,
    FAILED,
    INTERRUPTED,
)

# How a published/draft model's geometry was produced. Persisted alongside version/path/sha256
# so Admin UI can show "Photogrammetry" vs "AI Preview" without Visitor ever seeing it.
GENERATION_COLMAP = "colmap"
GENERATION_AI_MULTIVIEW = "ai_multiview"
# Single-image local AI generation (TripoSR). Distinct from GENERATION_AI_MULTIVIEW (Meshy):
# Admin UI needs to know whether the draft came from a local single-image model (show "Primary
# source" copy) versus a remote multi-image provider (show the selected photo set).
GENERATION_AI_LOCAL = "ai_local"

# Coarse pass/fail verdict from quality.assess_reconstruction_quality(), stored alongside the
# existing numeric metrics (registered_image_ratio, sparse_point_count, ...) so Admin UI can
# branch on one field instead of re-deriving the threshold logic client-side.
QUALITY_GOOD = "good"
QUALITY_INSUFFICIENT = "insufficient"
