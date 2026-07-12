from __future__ import annotations

import math
from datetime import datetime
from typing import Annotated

from bson import ObjectId
from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile, status
from pymongo.errors import DuplicateKeyError, PyMongoError

from app.auth.dependencies import require_admin
from app.repositories import artifact_repository
from app.schemas.artifact import ArtifactListResponse, ArtifactResponse, DeleteResponse, PrimaryImageRequest
from app.services.artifact_validation import (
    clean_artifact_fields,
    parse_remove_image_paths,
    select_paths_by_name_or_path,
    validate_image_count,
)
from app.services.image_storage import cleanup_images, image_url_for_path, safe_delete_image, save_uploads
from app.utils import to_object_id


router = APIRouter(prefix="/artifacts", tags=["Artifacts"], dependencies=[Depends(require_admin)])


def serialize_datetime(value) -> str:
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)


def serialize_artifact(document: dict, request: Request) -> ArtifactResponse:
    base_url = str(request.base_url)
    image_paths = document.get("image_paths", [])
    primary_image_path = document.get("primary_image_path")
    return ArtifactResponse(
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
        image_paths=image_paths,
        image_urls=[image_url_for_path(base_url, path) for path in image_paths],
        primary_image_path=primary_image_path,
        primary_image_url=image_url_for_path(base_url, primary_image_path),
        created_by=str(document.get("created_by", "")),
        created_at=serialize_datetime(document.get("created_at")),
        updated_at=serialize_datetime(document.get("updated_at")),
    )


def get_existing_artifact_or_404(database, artifact_id: str) -> tuple[ObjectId, dict]:
    object_id = to_object_id(artifact_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    artifact = artifact_repository.get_artifact(database, object_id)
    if artifact is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    return object_id, artifact


@router.get("", response_model=ArtifactListResponse)
def list_artifacts(
    request: Request,
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(ge=1, le=100)] = 20,
    search: str | None = None,
    category: str | None = None,
    sort: str = Query(default="newest", pattern="^(newest|oldest|name_asc|name_desc)$"),
) -> ArtifactListResponse:
    items, total = artifact_repository.list_artifacts(
        request.app.state.database,
        page=page,
        page_size=page_size,
        search=search.strip() if search else None,
        category=category.strip() if category else None,
        sort=sort,
    )
    return ArtifactListResponse(
        items=[serialize_artifact(item, request) for item in items],
        page=page,
        page_size=page_size,
        total_items=total,
        total_pages=math.ceil(total / page_size) if total else 0,
    )


@router.get("/{artifact_id}", response_model=ArtifactResponse)
def get_artifact(artifact_id: str, request: Request) -> ArtifactResponse:
    _, artifact = get_existing_artifact_or_404(request.app.state.database, artifact_id)
    return serialize_artifact(artifact, request)


@router.post("", response_model=ArtifactResponse, status_code=status.HTTP_201_CREATED)
async def create_artifact(
    request: Request,
    artifact_code: Annotated[str, Form()],
    name: Annotated[str, Form()],
    description: Annotated[str, Form()],
    category: Annotated[str, Form()],
    origin: Annotated[str | None, Form()] = None,
    historical_period: Annotated[str | None, Form()] = None,
    material: Annotated[str | None, Form()] = None,
    dimensions: Annotated[str | None, Form()] = None,
    condition: Annotated[str | None, Form()] = None,
    images: Annotated[list[UploadFile] | None, File()] = None,
    current_admin: dict = Depends(require_admin),
) -> ArtifactResponse:
    settings = request.app.state.settings
    fields = clean_artifact_fields(
        {
            "artifact_code": artifact_code,
            "name": name,
            "description": description,
            "category": category,
            "origin": origin,
            "historical_period": historical_period,
            "material": material,
            "dimensions": dimensions,
            "condition": condition,
        },
        partial=False,
    )

    validate_image_count(len(images or []))
    stored_images = await save_uploads(images, settings)
    image_paths = [image.image_path for image in stored_images]
    try:
        artifact = artifact_repository.create_artifact(
            request.app.state.database,
            {
                **fields,
                "image_paths": image_paths,
                "primary_image_path": image_paths[0] if image_paths else None,
                "created_by": current_admin["id"],
            },
        )
    except DuplicateKeyError as exc:
        cleanup_images(image_paths, settings)
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Artifact code already exists.") from exc
    except PyMongoError as exc:
        cleanup_images(image_paths, settings)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not create artifact.") from exc

    return serialize_artifact(artifact, request)


@router.patch("/{artifact_id}", response_model=ArtifactResponse)
async def update_artifact(
    artifact_id: str,
    request: Request,
    artifact_code: Annotated[str | None, Form()] = None,
    name: Annotated[str | None, Form()] = None,
    description: Annotated[str | None, Form()] = None,
    category: Annotated[str | None, Form()] = None,
    origin: Annotated[str | None, Form()] = None,
    historical_period: Annotated[str | None, Form()] = None,
    material: Annotated[str | None, Form()] = None,
    dimensions: Annotated[str | None, Form()] = None,
    condition: Annotated[str | None, Form()] = None,
    remove_image_paths: Annotated[list[str] | None, Form()] = None,
    replace_images: Annotated[bool, Form()] = False,
    primary_image_path: Annotated[str | None, Form()] = None,
    images: Annotated[list[UploadFile] | None, File()] = None,
) -> ArtifactResponse:
    settings = request.app.state.settings
    database = request.app.state.database
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)

    fields = clean_artifact_fields(
        {
            "artifact_code": artifact_code,
            "name": name,
            "description": description,
            "category": category,
            "origin": origin,
            "historical_period": historical_period,
            "material": material,
            "dimensions": dimensions,
            "condition": condition,
        },
        partial=True,
    )

    existing_paths = list(existing.get("image_paths", []))
    requested_removals = parse_remove_image_paths(remove_image_paths)
    removed_paths = existing_paths if replace_images else select_paths_by_name_or_path(existing_paths, requested_removals)
    remaining_paths = [path for path in existing_paths if path not in set(removed_paths)]

    validate_image_count(len(remaining_paths) + len(images or []))
    stored_images = await save_uploads(images, settings)
    new_paths = [image.image_path for image in stored_images]
    image_paths = remaining_paths + new_paths

    selected_primary = primary_image_path.strip() if primary_image_path else existing.get("primary_image_path")
    if selected_primary and selected_primary not in image_paths:
        if selected_primary.rsplit("/", 1)[-1] in {path.rsplit("/", 1)[-1] for path in image_paths}:
            selected_primary = next(path for path in image_paths if path.rsplit("/", 1)[-1] == selected_primary.rsplit("/", 1)[-1])
        else:
            selected_primary = None
    if selected_primary is None and image_paths:
        selected_primary = image_paths[0]

    updates = {
        **fields,
        "image_paths": image_paths,
        "primary_image_path": selected_primary,
    }
    try:
        updated = artifact_repository.update_artifact(database, object_id, updates)
    except DuplicateKeyError as exc:
        cleanup_images(new_paths, settings)
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Artifact code already exists.") from exc
    except PyMongoError as exc:
        cleanup_images(new_paths, settings)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not update artifact.") from exc

    for removed_path in removed_paths:
        safe_delete_image(removed_path, settings)
    return serialize_artifact(updated, request)


@router.delete("/{artifact_id}", response_model=DeleteResponse)
def delete_artifact(artifact_id: str, request: Request) -> DeleteResponse:
    database = request.app.state.database
    object_id, _ = get_existing_artifact_or_404(database, artifact_id)
    deleted = artifact_repository.delete_artifact(database, object_id)
    if deleted is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    cleanup_images(list(deleted.get("image_paths", [])), request.app.state.settings)
    return DeleteResponse(message="Artifact deleted successfully.")


@router.post("/{artifact_id}/images", response_model=ArtifactResponse)
async def add_artifact_images(
    artifact_id: str,
    request: Request,
    images: Annotated[list[UploadFile], File()],
) -> ArtifactResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)
    existing_paths = list(existing.get("image_paths", []))
    validate_image_count(len(existing_paths) + len(images or []))
    stored_images = await save_uploads(images, settings)
    new_paths = [image.image_path for image in stored_images]
    image_paths = existing_paths + new_paths
    primary = existing.get("primary_image_path") or (image_paths[0] if image_paths else None)
    try:
        updated = artifact_repository.update_artifact(
            database,
            object_id,
            {"image_paths": image_paths, "primary_image_path": primary},
        )
    except PyMongoError as exc:
        cleanup_images(new_paths, settings)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not add images.") from exc
    return serialize_artifact(updated, request)


@router.delete("/{artifact_id}/images/{image_name}", response_model=ArtifactResponse)
def remove_artifact_image(artifact_id: str, image_name: str, request: Request) -> ArtifactResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)
    existing_paths = list(existing.get("image_paths", []))
    removed_paths = select_paths_by_name_or_path(existing_paths, [image_name])
    image_paths = [path for path in existing_paths if path not in set(removed_paths)]
    primary = existing.get("primary_image_path")
    if primary in removed_paths:
        primary = image_paths[0] if image_paths else None
    updated = artifact_repository.update_artifact(
        database,
        object_id,
        {"image_paths": image_paths, "primary_image_path": primary},
    )
    for removed_path in removed_paths:
        safe_delete_image(removed_path, settings)
    return serialize_artifact(updated, request)


@router.patch("/{artifact_id}/primary-image", response_model=ArtifactResponse)
def set_primary_image(artifact_id: str, payload: PrimaryImageRequest, request: Request) -> ArtifactResponse:
    database = request.app.state.database
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)
    image_paths = list(existing.get("image_paths", []))
    selected = select_paths_by_name_or_path(image_paths, [payload.image_path])[0]
    updated = artifact_repository.update_artifact(database, object_id, {"primary_image_path": selected})
    return serialize_artifact(updated, request)
