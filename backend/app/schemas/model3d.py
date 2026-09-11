from __future__ import annotations

from pydantic import BaseModel, Field


class ReconstructionImageResponse(BaseModel):
    id: str
    origin: str
    original_filename: str | None = None
    width: int | None = None
    height: int | None = None
    created_at: str | None = None


class Model3DStateResponse(BaseModel):
    status: str
    version: int = 0
    sha256: str | None = None
    size_bytes: int | None = None
    created_at: str | None = None
    # How the published model was produced ("colmap" / "ai_multiview") - Admin-facing only, see
    # Model3DGenerationMethod on the Android side for the friendly label mapping.
    generation_method: str | None = None
    # Draft (pending_review) model awaiting admin Accept/Reject - never visitor-visible.
    draft_version: int | None = None
    draft_sha256: str | None = None
    draft_size_bytes: int | None = None
    draft_created_at: str | None = None
    draft_model_url: str | None = None
    draft_generation_method: str | None = None
    # Estimated coverage (see app/services/model3d/coverage.py) - a heuristic derived from which
    # major regions the admin marked visible in the generation image, never a model confidence
    # score. None means "coverage estimate unavailable", not 0%.
    visible_regions: list[str] = Field(default_factory=list)
    estimated_supported_percent: int | None = None
    estimated_inferred_percent: int | None = None
    draft_visible_regions: list[str] = Field(default_factory=list)
    draft_estimated_supported_percent: int | None = None
    draft_estimated_inferred_percent: int | None = None
    source_image_count: int = 0
    registered_image_count: int | None = None
    registered_image_ratio: float | None = None
    sparse_point_count: int | None = None
    mean_reprojection_error: float | None = None
    # Coarse "good"/"insufficient" verdict from the COLMAP quality gate (quality.py), plus why -
    # null for AI-generated drafts, which are reviewed visually instead (see AI_GUIDANCE_NOTICE).
    quality_assessment: str | None = None
    quality_reasons: list[str] = Field(default_factory=list)
    failure_message: str | None = None
    guidance: list[str] = Field(default_factory=list)
    images: list[ReconstructionImageResponse] = Field(default_factory=list)
    active_job_id: str | None = None
    colmap_available: bool | None = None
    model_url: str | None = None
    # Whether the optional AI 3D fallback is configured on this backend, and the selected
    # provider's per-request image cap - Android uses this to show/hide "Generate AI 3D Preview"
    # and to cap the admin's photo selection without hardcoding a provider-specific number.
    ai_available: bool = False
    ai_max_images: int | None = None


class Model3DJobResponse(BaseModel):
    id: str
    status: str
    stage_message: str | None = None
    target_version: int | None = None
    source_image_count: int | None = None
    registered_image_count: int | None = None
    registered_image_ratio: float | None = None
    sparse_point_count: int | None = None
    mean_reprojection_error: float | None = None
    generation_method: str | None = None
    error: str | None = None
    created_at: str | None = None
    updated_at: str | None = None
    started_at: str | None = None
    finished_at: str | None = None


class Model3DStatusResponse(BaseModel):
    state: Model3DStateResponse
    job: Model3DJobResponse | None = None


class Model3DBuildResponse(BaseModel):
    job_id: str
    status: str


class Model3DAiBuildRequest(BaseModel):
    """Admin's deliberate photo selection for the AI 3D fallback - never a silent default. See
    Ai3DProvider.max_images for the actual per-request cap, exposed to Android as ai_max_images."""

    image_ids: list[str] = Field(min_length=1)
    # Optional: which of the 6 major regions (front/right/back/left/top/bottom) are actually
    # visible in the generation image(s), used only to compute an honest coverage estimate (see
    # app/services/model3d/coverage.py). Omitting this leaves the estimate "unavailable".
    visible_regions: list[str] = Field(default_factory=list)


class PublicModel3D(BaseModel):
    model_3d_available: bool = False
    model_3d_url: str | None = None
    model_3d_version: int | None = None
    model_3d_sha256: str | None = None
    model_3d_size_bytes: int | None = None
