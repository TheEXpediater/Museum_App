from __future__ import annotations

import math
from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status

from app.auth.dependencies import require_visitor
from app.repositories import artifact_repository, public_content_repository
from app.schemas.public_content import (
    AnnouncementResponse,
    ArticleResponse,
    MuseumInformationResponse,
    NewsResponse,
    ProgramResponse,
    PublicArtifactListResponse,
    PublicArtifactResponse,
    PublicHomeResponse,
)
from app.services.image_storage import image_url_for_path
from app.utils import to_object_id


public_router = APIRouter(prefix="/public", tags=["Public Content"])
visitor_artifact_router = APIRouter(
    prefix="/visitor/artifacts",
    tags=["Visitor Artifacts"],
    dependencies=[Depends(require_visitor)],
)


def serialize_artifact(document: dict, request: Request) -> PublicArtifactResponse:
    base_url = str(request.base_url)
    image_paths = document.get("image_paths", [])
    primary_image_path = document.get("primary_image_path")
    return PublicArtifactResponse(
        id=str(document["_id"]),
        artifact_code=document["artifact_code"],
        name=document["name"],
        description=document["description"],
        category=document["category"],
        origin=document.get("origin"),
        historical_period=document.get("historical_period"),
        material=document.get("material"),
        dimensions=document.get("dimensions"),
        condition=document.get("condition"),
        image_urls=[image_url_for_path(base_url, path) for path in image_paths],
        primary_image_url=image_url_for_path(base_url, primary_image_path),
    )


def serialize_news(document: dict) -> NewsResponse:
    return NewsResponse(
        id=str(document["_id"]),
        title=document.get("title", ""),
        summary=document.get("summary", ""),
        body=document.get("body", ""),
        cover_image_url=document.get("cover_image_url"),
        published_at=document.get("published_at"),
    )


def serialize_announcement(document: dict) -> AnnouncementResponse:
    return AnnouncementResponse(
        id=str(document["_id"]),
        title=document.get("title", ""),
        message=document.get("message", ""),
        priority=document.get("priority", "normal"),
        starts_at=document.get("starts_at"),
        expires_at=document.get("expires_at"),
    )


def serialize_article(document: dict) -> ArticleResponse:
    return ArticleResponse(
        id=str(document["_id"]),
        title=document.get("title", ""),
        summary=document.get("summary", ""),
        body=document.get("body", ""),
        cover_image_url=document.get("cover_image_url"),
        category=document.get("category"),
        published_at=document.get("published_at"),
    )


def serialize_museum_information(document: dict | None) -> MuseumInformationResponse:
    if document is None:
        return MuseumInformationResponse()
    return MuseumInformationResponse(
        museum_name=document.get("museum_name") or "To be configured.",
        description=document.get("description") or "To be configured.",
        campus_location=document.get("campus_location") or "To be configured.",
        opening_hours=document.get("opening_hours") or "To be configured.",
        contact_email=document.get("contact_email") or "To be configured.",
        contact_phone=document.get("contact_phone") or "To be configured.",
        visitor_guidelines=document.get("visitor_guidelines") or "To be configured.",
        accessibility_information=document.get("accessibility_information") or "To be configured.",
        latitude=document.get("latitude"),
        longitude=document.get("longitude"),
        updated_at=document.get("updated_at") if isinstance(document.get("updated_at"), datetime) else None,
    )


@public_router.get("/home", response_model=PublicHomeResponse)
def home(request: Request) -> PublicHomeResponse:
    database = request.app.state.database
    return PublicHomeResponse(
        latest_news=[serialize_news(item) for item in public_content_repository.list_news(database, limit=3)],
        announcements=[serialize_announcement(item) for item in public_content_repository.list_announcements(database, limit=3)],
        featured_artifacts=[
            serialize_artifact(item, request)
            for item in public_content_repository.list_featured_artifacts(database, limit=5)
        ],
        museum_information=serialize_museum_information(public_content_repository.get_museum_information(database)),
    )


@public_router.get("/news", response_model=list[NewsResponse])
def news(request: Request) -> list[NewsResponse]:
    return [serialize_news(item) for item in public_content_repository.list_news(request.app.state.database)]


@public_router.get("/news/{news_id}", response_model=NewsResponse)
def news_details(news_id: str, request: Request) -> NewsResponse:
    object_id = to_object_id(news_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="News item was not found.")
    item = public_content_repository.get_published_news(request.app.state.database, object_id)
    if item is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="News item was not found.")
    return serialize_news(item)


@public_router.get("/announcements", response_model=list[AnnouncementResponse])
def announcements(request: Request) -> list[AnnouncementResponse]:
    return [serialize_announcement(item) for item in public_content_repository.list_announcements(request.app.state.database)]


@public_router.get("/articles", response_model=list[ArticleResponse])
def articles(
    request: Request,
    search: str | None = None,
    category: str | None = None,
) -> list[ArticleResponse]:
    return [
        serialize_article(item)
        for item in public_content_repository.list_articles(
            request.app.state.database,
            search=search.strip() if search else None,
            category=category.strip() if category else None,
        )
    ]


@public_router.get("/articles/{article_id}", response_model=ArticleResponse)
def article_details(article_id: str, request: Request) -> ArticleResponse:
    object_id = to_object_id(article_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Article was not found.")
    item = public_content_repository.get_published_article(request.app.state.database, object_id)
    if item is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Article was not found.")
    return serialize_article(item)


@public_router.get("/museum-info", response_model=MuseumInformationResponse)
def museum_information(request: Request) -> MuseumInformationResponse:
    return serialize_museum_information(public_content_repository.get_museum_information(request.app.state.database))


@public_router.get("/programs", response_model=list[ProgramResponse])
def programs(request: Request) -> list[ProgramResponse]:
    return [
        ProgramResponse(id=str(item["_id"]), name=item.get("name", ""))
        for item in public_content_repository.list_programs(request.app.state.database)
    ]


@visitor_artifact_router.get("", response_model=PublicArtifactListResponse)
def visitor_artifacts(
    request: Request,
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(ge=1, le=100)] = 20,
    search: str | None = None,
    category: str | None = None,
    sort: str = Query(default="newest", pattern="^(newest|oldest|name_asc|name_desc)$"),
) -> PublicArtifactListResponse:
    items, total = artifact_repository.list_artifacts(
        request.app.state.database,
        page=page,
        page_size=page_size,
        search=search.strip() if search else None,
        category=category.strip() if category else None,
        sort=sort,
    )
    return PublicArtifactListResponse(
        items=[serialize_artifact(item, request) for item in items],
        page=page,
        page_size=page_size,
        total_items=total,
        total_pages=math.ceil(total / page_size) if total else 0,
    )


@visitor_artifact_router.get("/{artifact_id}", response_model=PublicArtifactResponse)
def visitor_artifact_details(artifact_id: str, request: Request) -> PublicArtifactResponse:
    object_id = to_object_id(artifact_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    artifact = artifact_repository.get_artifact(request.app.state.database, object_id)
    if artifact is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    return serialize_artifact(artifact, request)
