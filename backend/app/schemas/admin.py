from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class DashboardRecentArtifact(BaseModel):
    id: str
    artifact_code: str
    name: str
    category: str
    status: str = "published"
    primary_image_url: str | None = None
    ai_index_status: str | None = None
    created_at: str


class DashboardSummaryResponse(BaseModel):
    total_artifacts: int
    total_images: int
    total_categories: int
    published_artifacts: int = 0
    draft_artifacts: int = 0
    ai_library_ready_artifacts: int = 0
    ai_library_pending_artifacts: int = 0
    ai_library_stale_artifacts: int = 0
    indexed_artifacts: int
    pending_artifacts: int
    failed_artifacts: int
    indexed_vectors: int
    ai_status: str
    database_status: str
    uploads_status: str
    model_3d_enabled: bool = False
    colmap_available: bool = False
    colmap_version: str | None = None
    recent_artifacts: list[DashboardRecentArtifact] = Field(default_factory=list)


class StudentAccountListItem(BaseModel):
    id: str
    student_id: str
    display_name: str
    email: str
    account_status: str
    created_at: str


class StudentAccountDetail(BaseModel):
    id: str
    student_id: str
    first_name: str
    middle_initial: str | None = None
    last_name: str
    display_name: str
    email: str
    course: str
    year_level: str
    account_status: str
    created_at: str
    updated_at: str
    approved_at: str | None = None
    last_login_at: str | None = None


class StudentStatusUpdateRequest(BaseModel):
    account_status: Literal["active", "inactive"]
