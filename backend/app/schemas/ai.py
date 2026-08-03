from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


AI_INDEX_STATUSES = {"not_indexed", "pending", "indexed", "partial", "failed"}
MATCH_LEVELS = {"strong", "possible", "weak", "no_match"}


class AiIndexResultResponse(BaseModel):
    artifact_id: str | None = None
    ai_index_status: str = Field(pattern="^(not_indexed|pending|indexed|partial|failed)$")
    total_images: int = 0
    indexed_images: int = 0
    failed_images: int = 0
    skipped_images: int = 0
    messages: list[str] = Field(default_factory=list)
    errors: list[str] = Field(default_factory=list)


class AiIndexAllResponse(BaseModel):
    total_artifacts: int
    total_images: int
    indexed_images: int
    failed_images: int
    skipped_images: int
    duration: float
    errors: list[str] = Field(default_factory=list)


class AiIndexStatusResponse(BaseModel):
    total_artifacts: int
    total_images: int
    indexed_artifacts: int
    pending_artifacts: int
    failed_artifacts: int
    partial_artifacts: int
    not_indexed_artifacts: int
    indexed_vectors: int
    ai_enabled: bool
    openclip: str
    qdrant: str
    collection: str
    collection_status: str
    collection_vector_size: int | None = None
    collection_distance: str | None = None
    message: str | None = None


class AiWarmupResponse(BaseModel):
    state: str = Field(pattern="^(idle|loading|loaded|failed)$")
    message: str
    model_name: str
    pretrained: str
    device: str | None = None
    embedding_dimension: int | None = None
    started_at: datetime | None = None
    completed_at: datetime | None = None
    duration_seconds: float | None = None
    error: str | None = None


class RecognizedArtifact(BaseModel):
    id: str
    artifact_code: str
    name: str
    description: str
    category: str
    origin: str | None = None
    historical_period: str | None = None
    material: str | None = None
    dimensions: str | None = None
    condition: str | None = None
    primary_image_url: str | None = None


class ArtifactMatchResponse(BaseModel):
    artifact: RecognizedArtifact
    similarity_score: float
    matched_image_path: str | None = None
    supporting_image_hits: int = 1


class RecognitionResponse(BaseModel):
    matched: bool
    match_level: str = Field(pattern="^(strong|possible|weak|no_match)$")
    best_match: ArtifactMatchResponse | None
    other_matches: list[ArtifactMatchResponse] = Field(default_factory=list)
    message: str
