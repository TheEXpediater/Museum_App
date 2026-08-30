from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pymongo.errors import DuplicateKeyError, PyMongoError

from app.auth.dependencies import require_admin
from app.repositories import category_repository
from app.schemas.artifact import (
    ArtifactCategoryCreateRequest,
    ArtifactCategoryResponse,
    ArtifactCategoryUpdateRequest,
)
from app.utils import to_object_id


router = APIRouter(prefix="/artifact-categories", tags=["Artifact Categories"], dependencies=[Depends(require_admin)])


def serialize_datetime(value) -> str:
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)


def serialize_category(document: dict) -> ArtifactCategoryResponse:
    return ArtifactCategoryResponse(
        id=str(document["_id"]),
        name=document.get("name", ""),
        normalized_name=document.get("normalized_name", ""),
        is_active=bool(document.get("is_active", True)),
        suggested_fields=document.get("suggested_fields", []),
        created_at=serialize_datetime(document.get("created_at")),
        updated_at=serialize_datetime(document.get("updated_at")),
    )


@router.get("", response_model=list[ArtifactCategoryResponse])
def list_categories(request: Request, include_inactive: bool = False) -> list[ArtifactCategoryResponse]:
    return [
        serialize_category(category)
        for category in category_repository.list_categories(
            request.app.state.database,
            include_inactive=include_inactive,
        )
    ]


@router.post("", response_model=ArtifactCategoryResponse, status_code=status.HTTP_201_CREATED)
def create_category(payload: ArtifactCategoryCreateRequest, request: Request) -> ArtifactCategoryResponse:
    try:
        category = category_repository.create_category(
            request.app.state.database,
            name=payload.name,
            suggested_fields=[field.model_dump() for field in payload.suggested_fields],
        )
    except DuplicateKeyError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Category already exists.") from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except PyMongoError as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not create category.") from exc
    return serialize_category(category)


@router.patch("/{category_id}", response_model=ArtifactCategoryResponse)
def update_category(
    category_id: str,
    payload: ArtifactCategoryUpdateRequest,
    request: Request,
) -> ArtifactCategoryResponse:
    object_id = to_object_id(category_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category was not found.")
    try:
        category = category_repository.update_category(
            request.app.state.database,
            object_id,
            name=payload.name,
            is_active=payload.is_active,
            suggested_fields=[field.model_dump() for field in payload.suggested_fields]
            if payload.suggested_fields is not None
            else None,
        )
    except DuplicateKeyError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Category already exists.") from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except PyMongoError as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not update category.") from exc
    if category is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category was not found.")
    return serialize_category(category)


@router.delete("/{category_id}", response_model=ArtifactCategoryResponse)
def deactivate_category(category_id: str, request: Request) -> ArtifactCategoryResponse:
    object_id = to_object_id(category_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category was not found.")
    category = category_repository.deactivate_category(request.app.state.database, object_id)
    if category is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category was not found.")
    return serialize_category(category)
