from __future__ import annotations

import math
from datetime import datetime
from typing import Annotated

from bson import ObjectId
from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile, status
from pymongo.errors import DuplicateKeyError, PyMongoError

from app.auth.dependencies import require_admin
from app.repositories import artifact_repository, category_repository
from app.schemas.artifact import ArtifactListResponse, ArtifactResponse, DeleteResponse, PrimaryImageRequest
from app.services.artifact_validation import (
    clean_artifact_fields,
    normalize_artifact_status,
    parse_custom_fields,
    parse_remove_image_paths,
    persisted_status,
    select_paths_by_name_or_path,
    validate_publishable,
)
from app.services.artifact_indexing_service import ArtifactIndexingService
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
        artifact_code=document.get("artifact_code", ""),
        name=document.get("name", ""),
        description=document.get("description") or "",
        category=document.get("category") or "Uncategorized",
        status=persisted_status(document),
        origin=document.get("origin"),
        historical_period=document.get("historical_period"),
        material=document.get("material"),
        dimensions=document.get("dimensions"),
        condition=document.get("condition"),
        custom_fields=document.get("custom_fields") or [],
        image_paths=image_paths,
        image_urls=[image_url_for_path(base_url, path) for path in image_paths],
        primary_image_path=primary_image_path,
        primary_image_url=image_url_for_path(base_url, primary_image_path),
        primary_image_needs_review=bool(document.get("primary_image_needs_review", False)),
        ai_index_status=document.get("ai_index_status"),
        ai_indexed_image_count=document.get("ai_indexed_image_count"),
        ai_indexed_at=serialize_datetime(document.get("ai_indexed_at")) if document.get("ai_indexed_at") else None,
        ai_index_error=document.get("ai_index_error"),
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


def selected_upload_primary(
    stored_images,
    *,
    primary_image_index: int | None,
    primary_image_filename: str | None,
) -> tuple[str | None, bool]:
    if not stored_images:
        return None, False

    if primary_image_index is not None:
        if primary_image_index < 0 or primary_image_index >= len(stored_images):
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Selected primary image index is invalid.")
        return stored_images[primary_image_index].image_path, True

    if primary_image_filename:
        requested = primary_image_filename.strip()
        matches = [
            image.image_path
            for image in stored_images
            if image.source_filename and image.source_filename.rsplit("/", 1)[-1] == requested.rsplit("/", 1)[-1]
        ]
        if len(matches) == 1:
            return matches[0], True
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Selected primary image filename is invalid.")

    if len(stored_images) == 1:
        return stored_images[0].image_path, False

    raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Select a main image before saving.")


def validate_primary_membership(primary_image_path: str | None, image_paths: list[str]) -> None:
    if primary_image_path and primary_image_path not in image_paths:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Primary image must belong to this artifact.")


def apply_category_default(fields: dict) -> None:
    if not fields.get("category"):
        fields["category"] = "Uncategorized"


def ensure_category_if_needed(database, category: str | None) -> None:
    try:
        category_repository.ensure_category(database, category)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc


@router.get("", response_model=ArtifactListResponse)
def list_artifacts(
    request: Request,
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(ge=1, le=100)] = 20,
    search: str | None = None,
    category: str | None = None,
    sort: str = Query(default="newest", pattern="^(newest|oldest|name_asc|name_desc)$"),
    status_filter: str = Query(default="all", alias="status", pattern="^(all|published|draft|drafts)$"),
) -> ArtifactListResponse:
    items, total = artifact_repository.list_artifacts(
        request.app.state.database,
        page=page,
        page_size=page_size,
        search=search.strip() if search else None,
        category=category.strip() if category else None,
        sort=sort,
        status_filter=status_filter,
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
    description: Annotated[str | None, Form()] = None,
    category: Annotated[str | None, Form()] = None,
    origin: Annotated[str | None, Form()] = None,
    historical_period: Annotated[str | None, Form()] = None,
    material: Annotated[str | None, Form()] = None,
    dimensions: Annotated[str | None, Form()] = None,
    condition: Annotated[str | None, Form()] = None,
    artifact_status: Annotated[str | None, Form(alias="status")] = None,
    custom_fields: Annotated[str | None, Form()] = None,
    primary_image_path: Annotated[str | None, Form()] = None,
    primary_image_index: Annotated[int | None, Form()] = None,
    primary_image_filename: Annotated[str | None, Form()] = None,
    images: Annotated[list[UploadFile] | None, File()] = None,
    current_admin: dict = Depends(require_admin),
) -> ArtifactResponse:
    settings = request.app.state.settings
    target_status = normalize_artifact_status(artifact_status, default="draft")
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
    apply_category_default(fields)
    parsed_custom_fields = parse_custom_fields(custom_fields, partial=False) or []

    stored_images = await save_uploads(images, settings)
    image_paths = [image.image_path for image in stored_images]
    selected_primary, primary_selected_explicitly = selected_upload_primary(
        stored_images,
        primary_image_index=primary_image_index,
        primary_image_filename=primary_image_filename,
    )
    if primary_image_path:
        selected_primary = select_paths_by_name_or_path(image_paths, [primary_image_path])[0]
        primary_selected_explicitly = True
    validate_primary_membership(selected_primary, image_paths)

    candidate_document = {
        **fields,
        "image_paths": image_paths,
        "primary_image_path": selected_primary,
        "status": target_status,
    }
    if target_status == "published":
        validate_publishable(candidate_document)
    ensure_category_if_needed(request.app.state.database, fields.get("category"))

    try:
        artifact = artifact_repository.create_artifact(
            request.app.state.database,
            {
                **fields,
                "status": target_status,
                "custom_fields": parsed_custom_fields,
                "image_paths": image_paths,
                "primary_image_path": selected_primary,
                "primary_image_needs_review": False if primary_selected_explicitly or image_paths else False,
                "created_by": current_admin["id"],
            },
        )
    except DuplicateKeyError as exc:
        cleanup_images(image_paths, settings)
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Artifact code already exists.") from exc
    except PyMongoError as exc:
        cleanup_images(image_paths, settings)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not create artifact.") from exc

    artifact = ArtifactIndexingService.from_settings(settings).synchronize_after_create(request.app.state.database, artifact)
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
    artifact_status: Annotated[str | None, Form(alias="status")] = None,
    custom_fields: Annotated[str | None, Form()] = None,
    remove_image_paths: Annotated[list[str] | None, Form()] = None,
    replace_images: Annotated[bool, Form()] = False,
    primary_image_path: Annotated[str | None, Form()] = None,
    primary_image_index: Annotated[int | None, Form()] = None,
    primary_image_filename: Annotated[str | None, Form()] = None,
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
    if "category" in fields:
        apply_category_default(fields)
    parsed_custom_fields = parse_custom_fields(custom_fields, partial=True)
    target_status = normalize_artifact_status(artifact_status, default=persisted_status(existing)) if artifact_status is not None else persisted_status(existing)

    existing_paths = list(existing.get("image_paths", []))
    requested_removals = parse_remove_image_paths(remove_image_paths)
    removed_paths = existing_paths if replace_images else select_paths_by_name_or_path(existing_paths, requested_removals)
    remaining_paths = [path for path in existing_paths if path not in set(removed_paths)]

    stored_images = await save_uploads(images, settings)
    new_paths = [image.image_path for image in stored_images]
    image_paths = remaining_paths + new_paths

    selected_primary = existing.get("primary_image_path")
    primary_selected_explicitly = False
    if primary_image_index is not None or primary_image_filename:
        selected_primary, primary_selected_explicitly = selected_upload_primary(
            stored_images,
            primary_image_index=primary_image_index,
            primary_image_filename=primary_image_filename,
        )
    elif primary_image_path and primary_image_path.strip():
        selected_primary = select_paths_by_name_or_path(image_paths, [primary_image_path.strip()])[0]
        primary_selected_explicitly = True

    if selected_primary and selected_primary not in image_paths:
        selected_primary = None
    if selected_primary is None and image_paths:
        if len(image_paths) == 1 and not existing.get("primary_image_path") and not primary_selected_explicitly:
            selected_primary = image_paths[0]
        else:
            cleanup_images(new_paths, settings)
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Choose a new main image before removing the current main image.",
            )
    validate_primary_membership(selected_primary, image_paths)

    updates = {
        **fields,
        "image_paths": image_paths,
        "primary_image_path": selected_primary,
        "status": target_status,
    }
    if parsed_custom_fields is not None:
        updates["custom_fields"] = parsed_custom_fields
    if primary_selected_explicitly:
        updates["primary_image_needs_review"] = False

    candidate_document = {**existing, **updates}
    should_validate_published = (
        target_status == "published"
        and (artifact_status is not None or bool(removed_paths) or replace_images or primary_selected_explicitly)
    )
    if should_validate_published:
        validate_publishable(candidate_document)
    ensure_category_if_needed(database, updates.get("category"))
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
    updated = ArtifactIndexingService.from_settings(settings).synchronize_after_update(database, existing, updated)
    return serialize_artifact(updated, request)


@router.delete("/{artifact_id}", response_model=DeleteResponse)
def delete_artifact(artifact_id: str, request: Request) -> DeleteResponse:
    database = request.app.state.database
    object_id, _ = get_existing_artifact_or_404(database, artifact_id)
    deleted = artifact_repository.delete_artifact(database, object_id)
    if deleted is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    cleanup_images(list(deleted.get("image_paths", [])), request.app.state.settings)
    ArtifactIndexingService.from_settings(request.app.state.settings).delete_artifact_vectors(artifact_id)
    return DeleteResponse(message="Artifact deleted successfully.")


@router.post("/{artifact_id}/images", response_model=ArtifactResponse)
async def add_artifact_images(
    artifact_id: str,
    request: Request,
    images: Annotated[list[UploadFile], File()],
    primary_image_index: Annotated[int | None, Form()] = None,
    primary_image_filename: Annotated[str | None, Form()] = None,
) -> ArtifactResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)
    existing_paths = list(existing.get("image_paths", []))
    stored_images = await save_uploads(images, settings)
    new_paths = [image.image_path for image in stored_images]
    image_paths = existing_paths + new_paths
    primary = existing.get("primary_image_path")
    primary_selected_explicitly = False
    if primary_image_index is not None or primary_image_filename:
        primary, primary_selected_explicitly = selected_upload_primary(
            stored_images,
            primary_image_index=primary_image_index,
            primary_image_filename=primary_image_filename,
        )
    elif primary is None and len(new_paths) == 1:
        primary = new_paths[0]
    elif primary is None and new_paths:
        cleanup_images(new_paths, settings)
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Select a main image before saving.")
    try:
        updates = {"image_paths": image_paths, "primary_image_path": primary}
        if primary_selected_explicitly:
            updates["primary_image_needs_review"] = False
        updated = artifact_repository.update_artifact(
            database,
            object_id,
            updates,
        )
    except PyMongoError as exc:
        cleanup_images(new_paths, settings)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not add images.") from exc
    updated = ArtifactIndexingService.from_settings(settings).synchronize_after_update(database, existing, updated)
    return serialize_artifact(updated, request)


@router.delete("/{artifact_id}/images/{image_name}", response_model=ArtifactResponse)
def remove_artifact_image(
    artifact_id: str,
    image_name: str,
    request: Request,
    replacement_primary_image_path: str | None = None,
) -> ArtifactResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)
    existing_paths = list(existing.get("image_paths", []))
    removed_paths = select_paths_by_name_or_path(existing_paths, [image_name])
    image_paths = [path for path in existing_paths if path not in set(removed_paths)]
    primary = existing.get("primary_image_path")
    if primary in removed_paths:
        if image_paths:
            if not replacement_primary_image_path:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                    detail="Choose a new main image before removing the current main image.",
                )
            primary = select_paths_by_name_or_path(image_paths, [replacement_primary_image_path])[0]
        else:
            if persisted_status(existing) == "published":
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                    detail="Published artifacts must keep at least one primary image.",
                )
            primary = None
    updates = {"image_paths": image_paths, "primary_image_path": primary}
    if replacement_primary_image_path:
        updates["primary_image_needs_review"] = False
    updated = artifact_repository.update_artifact(
        database,
        object_id,
        updates,
    )
    for removed_path in removed_paths:
        safe_delete_image(removed_path, settings)
    updated = ArtifactIndexingService.from_settings(settings).synchronize_after_update(database, existing, updated)
    return serialize_artifact(updated, request)


@router.patch("/{artifact_id}/primary-image", response_model=ArtifactResponse)
def set_primary_image(artifact_id: str, payload: PrimaryImageRequest, request: Request) -> ArtifactResponse:
    database = request.app.state.database
    object_id, existing = get_existing_artifact_or_404(database, artifact_id)
    image_paths = list(existing.get("image_paths", []))
    selected = select_paths_by_name_or_path(image_paths, [payload.image_path])[0]
    updated = artifact_repository.update_artifact(
        database,
        object_id,
        {"primary_image_path": selected, "primary_image_needs_review": False},
    )
    return serialize_artifact(updated, request)
