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
    # Draft (pending_review) model awaiting admin Accept/Reject - never visitor-visible.
    draft_version: int | None = None
    draft_sha256: str | None = None
    draft_size_bytes: int | None = None
    draft_created_at: str | None = None
    draft_model_url: str | None = None
    source_image_count: int = 0
    registered_image_count: int | None = None
    registered_image_ratio: float | None = None
    sparse_point_count: int | None = None
    mean_reprojection_error: float | None = None
    failure_message: str | None = None
    guidance: list[str] = Field(default_factory=list)
    images: list[ReconstructionImageResponse] = Field(default_factory=list)
    active_job_id: str | None = None
    colmap_available: bool | None = None
    model_url: str | None = None


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


class PublicModel3D(BaseModel):
    model_3d_available: bool = False
    model_3d_url: str | None = None
    model_3d_version: int | None = None
    model_3d_sha256: str | None = None
    model_3d_size_bytes: int | None = None
