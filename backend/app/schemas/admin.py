from __future__ import annotations

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
    indexed_artifacts: int
    pending_artifacts: int
    failed_artifacts: int
    indexed_vectors: int
    ai_status: str
    database_status: str
    uploads_status: str
    recent_artifacts: list[DashboardRecentArtifact] = Field(default_factory=list)
