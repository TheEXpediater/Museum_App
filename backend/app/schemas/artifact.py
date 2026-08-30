from pydantic import BaseModel, Field


class ArtifactCustomField(BaseModel):
    id: str
    label: str
    value: str
    unit: str | None = None
    type: str


class ArtifactResponse(BaseModel):
    id: str
    artifact_code: str
    name: str
    description: str = ""
    category: str = "Uncategorized"
    status: str = "published"
    origin: str | None = None
    historical_period: str | None = None
    material: str | None = None
    dimensions: str | None = None
    condition: str | None = None
    custom_fields: list[ArtifactCustomField] = Field(default_factory=list)
    image_paths: list[str] = Field(default_factory=list)
    image_urls: list[str] = Field(default_factory=list)
    primary_image_path: str | None = None
    primary_image_url: str | None = None
    primary_image_needs_review: bool = False
    ai_index_status: str | None = None
    ai_indexed_image_count: int | None = None
    ai_indexed_at: str | None = None
    ai_index_error: str | None = None
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


class ArtifactCategorySuggestedField(BaseModel):
    label: str
    type: str = "text"
    unit: str | None = None


class ArtifactCategoryResponse(BaseModel):
    id: str
    name: str
    normalized_name: str
    is_active: bool = True
    suggested_fields: list[ArtifactCategorySuggestedField] = Field(default_factory=list)
    created_at: str
    updated_at: str


class ArtifactCategoryCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    suggested_fields: list[ArtifactCategorySuggestedField] = Field(default_factory=list)


class ArtifactCategoryUpdateRequest(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=100)
    is_active: bool | None = None
    suggested_fields: list[ArtifactCategorySuggestedField] | None = None
