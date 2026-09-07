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
# rebuild/replacement of a previously published model).
BUILDABLE_STATES = (
    READY_FOR_BUILD,
    READY,
    FAILED,
    INTERRUPTED,
)
