from pydantic import BaseModel, Field


class ArtifactResponse(BaseModel):
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
    image_paths: list[str] = Field(default_factory=list)
    image_urls: list[str] = Field(default_factory=list)
    primary_image_path: str | None = None
    primary_image_url: str | None = None
    created_by: str
    created_at: str
    updated_at: str


class ArtifactListResponse(BaseModel):
    items: list[ArtifactResponse]
    page: int
    page_size: int
    total_items: int
    total_pages: int


class DeleteResponse(BaseModel):
    message: str


class PrimaryImageRequest(BaseModel):
    image_path: str = Field(min_length=1)
