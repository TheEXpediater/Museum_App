from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


class PublicArtifactCustomField(BaseModel):
    label: str
    value: str
    unit: str | None = None
    type: str


class PublicArtifactMetadataField(BaseModel):
    label: str
    value: str
    unit: str | None = None
    type: str = "text"


class PublicArtifactMetadataSection(BaseModel):
    title: str
    fields: list[PublicArtifactMetadataField] = Field(default_factory=list)


class PublicArtifactResponse(BaseModel):
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
    custom_fields: list[PublicArtifactCustomField] = Field(default_factory=list)
    metadata_sections: list[PublicArtifactMetadataSection] = Field(default_factory=list)
    image_urls: list[str] = Field(default_factory=list)
    primary_image_url: str | None = None


class PublicArtifactListResponse(BaseModel):
    items: list[PublicArtifactResponse]
    page: int
    page_size: int
    total_items: int
    total_pages: int


class NewsResponse(BaseModel):
    id: str
    title: str
    summary: str
    body: str
    cover_image_url: str | None = None
    published_at: datetime | None = None


class AnnouncementResponse(BaseModel):
    id: str
    title: str
    message: str
    priority: str = "normal"
    starts_at: datetime | None = None
    expires_at: datetime | None = None


class ArticleResponse(BaseModel):
    id: str
    title: str
    summary: str
    body: str
    cover_image_url: str | None = None
    category: str | None = None
    published_at: datetime | None = None


class MuseumInformationResponse(BaseModel):
    museum_name: str = "To be configured."
    description: str = "To be configured."
    campus_location: str = "To be configured."
    opening_hours: str = "To be configured."
    contact_email: str = "To be configured."
    contact_phone: str = "To be configured."
    visitor_guidelines: str = "To be configured."
    accessibility_information: str = "To be configured."
    latitude: float | None = None
    longitude: float | None = None
    updated_at: datetime | None = None


class ProgramResponse(BaseModel):
    id: str
    name: str


class PublicHomeResponse(BaseModel):
    latest_news: list[NewsResponse] = Field(default_factory=list)
    announcements: list[AnnouncementResponse] = Field(default_factory=list)
    featured_artifacts: list[PublicArtifactResponse] = Field(default_factory=list)
    museum_information: MuseumInformationResponse = Field(default_factory=MuseumInformationResponse)
