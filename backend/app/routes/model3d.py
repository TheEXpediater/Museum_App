from __future__ import annotations

from typing import Annotated

from bson import ObjectId
from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status

from app.auth.dependencies import require_admin
from app.repositories import reconstruction_repository as repo
from app.routes.artifacts import get_existing_artifact_or_404
from app.schemas.model3d import (
    Model3DBuildResponse,
    Model3DJobResponse,
    Model3DStateResponse,
    Model3DStatusResponse,
)
from app.services.artifact_validation import parse_image_path_list
from app.services.image_storage import image_url_for_path
from app.services.model3d import reconstruction_service as service
from app.services.model3d import states
from app.utils import to_object_id

router = APIRouter(
    prefix="/artifacts/{artifact_id}/3d",
    tags=["3D Reconstruction"],
    dependencies=[Depends(require_admin)],
)


def _state_response(database, artifact: dict, request: Request) -> Model3DStateResponse:
    state = service.get_state(database, artifact)
    model_url = None
    if state["status"] == states.READY and state.get("path"):
        model_url = image_url_for_path(str(request.base_url), state["path"])
    return Model3DStateResponse(**{k: v for k, v in state.items() if k != "path"}, model_url=model_url)


@router.get("", response_model=Model3DStateResponse)
def get_3d_state(artifact_id: str, request: Request) -> Model3DStateResponse:
    database = request.app.state.database
    _, artifact = get_existing_artifact_or_404(database, artifact_id)
    return _state_response(database, artifact, request)


@router.post("/images", response_model=Model3DStateResponse)
async def add_images(
    artifact_id: str,
    request: Request,
    reuse_image_paths: Annotated[str | None, Form()] = None,
    images: Annotated[list[UploadFile] | None, File()] = None,
) -> Model3DStateResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    _, artifact = get_existing_artifact_or_404(database, artifact_id)
    parsed_reuse_paths = parse_image_path_list(reuse_image_paths, partial=True, field_name="reuse_image_paths") or []
    service.add_images(database, settings, artifact, reuse_image_paths=parsed_reuse_paths, uploads=images or [])
    _, refreshed = get_existing_artifact_or_404(database, artifact_id)
    return _state_response(database, refreshed, request)


@router.delete("/images/{image_id}", response_model=Model3DStateResponse)
def delete_image(artifact_id: str, image_id: str, request: Request) -> Model3DStateResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    object_id, artifact = get_existing_artifact_or_404(database, artifact_id)
    image_object_id = to_object_id(image_id)
    if image_object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Reconstruction image was not found.")
    service.remove_image(database, settings, object_id, image_object_id)
    _, refreshed = get_existing_artifact_or_404(database, artifact_id)
    return _state_response(database, refreshed, request)


@router.delete("", response_model=Model3DStateResponse)
def delete_reconstruction(artifact_id: str, request: Request) -> Model3DStateResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    object_id, artifact = get_existing_artifact_or_404(database, artifact_id)
    service.delete_all(database, settings, object_id)
    _, refreshed = get_existing_artifact_or_404(database, artifact_id)
    return _state_response(database, refreshed, request)


@router.post("/preflight", response_model=Model3DStateResponse)
def run_preflight(artifact_id: str, request: Request) -> Model3DStateResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    _, artifact = get_existing_artifact_or_404(database, artifact_id)
    service.run_preflight(database, settings, artifact)
    _, refreshed = get_existing_artifact_or_404(database, artifact_id)
    return _state_response(database, refreshed, request)


@router.post("/build", response_model=Model3DBuildResponse, status_code=status.HTTP_202_ACCEPTED)
def build(artifact_id: str, request: Request) -> Model3DBuildResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    _, artifact = get_existing_artifact_or_404(database, artifact_id)
    job = service.start_build(database, settings, artifact)
    return Model3DBuildResponse(job_id=str(job["_id"]), status=job["status"])


@router.get("/status", response_model=Model3DStatusResponse)
def get_status(artifact_id: str, request: Request) -> Model3DStatusResponse:
    database = request.app.state.database
    object_id, artifact = get_existing_artifact_or_404(database, artifact_id)
    job = service.get_job_status(database, object_id)
    return Model3DStatusResponse(
        state=_state_response(database, artifact, request),
        job=Model3DJobResponse(**job) if job else None,
    )
