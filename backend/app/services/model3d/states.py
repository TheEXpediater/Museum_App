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
