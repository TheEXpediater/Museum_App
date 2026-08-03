from __future__ import annotations

from dataclasses import asdict

from fastapi import APIRouter, Depends, File, HTTPException, Query, Request, UploadFile, status
from fastapi.responses import JSONResponse

from app.auth.dependencies import require_admin
from app.ai import model_manager as openclip_models
from app.repositories import artifact_repository
from app.schemas.ai import (
    AiIndexAllResponse,
    AiIndexResultResponse,
    AiIndexStatusResponse,
    AiWarmupResponse,
    RecognitionResponse,
)
from app.services.artifact_indexing_service import ArtifactIndexingService
from app.services.artifact_recognition_service import (
    AI_UNAVAILABLE_MESSAGE,
    ArtifactRecognitionService,
    RecognitionInputError,
    RecognitionUnavailableError,
)
from app.services.openclip_warmup_service import (
    WARMUP_FAILED,
    WARMUP_IDLE,
    WARMUP_LOADED,
    WARMUP_LOADING,
    OpenCLIPWarmupService,
    get_openclip_warmup_service,
)
from app.utils import to_object_id
from app.vector import qdrant_manager as qdrant_vectors


router = APIRouter(prefix="/ai", tags=["AI"])


def _warmup_service(request: Request) -> OpenCLIPWarmupService:
    service = getattr(request.app.state, "openclip_warmup_service", None)
    if service is None:
        service = get_openclip_warmup_service(request.app.state.settings)
        request.app.state.openclip_warmup_service = service
    return service


def _warmup_response(snapshot) -> AiWarmupResponse:
    return AiWarmupResponse(**asdict(snapshot))


@router.get("/health")
def ai_health(request: Request) -> dict:
    settings = request.app.state.settings
    if not settings.ai_enabled:
        return {
            "status": "disabled",
            "ai_enabled": False,
            "openclip": "disabled",
            "model_name": settings.openclip_model_name,
            "pretrained": settings.openclip_pretrained,
            "device": None,
            "embedding_dimension": None,
            "qdrant": "disabled",
            "collection": settings.qdrant_collection,
            "collection_status": "disabled",
            "indexed_vectors": 0,
        }

    response = {
        "status": "healthy",
        "ai_enabled": True,
        "openclip": WARMUP_IDLE,
        "model_name": settings.openclip_model_name,
        "pretrained": settings.openclip_pretrained,
        "device": settings.openclip_device,
        "embedding_dimension": None,
        "qdrant": "unknown",
        "collection": settings.qdrant_collection,
        "collection_status": "unknown",
        "indexed_vectors": 0,
    }

    if not openclip_models.dependencies_available():
        response["openclip"] = "not_installed"
        response["status"] = "degraded"
    else:
        manager = openclip_models.get_model_manager(settings)
        if manager.is_loaded:
            response["openclip"] = WARMUP_LOADED
            response["device"] = manager.actual_device
            response["embedding_dimension"] = manager.embedding_dimension
        else:
            warmup_status = _warmup_service(request).status()
            if warmup_status.state in {WARMUP_LOADING, WARMUP_FAILED}:
                response["openclip"] = warmup_status.state
                if warmup_status.state == WARMUP_FAILED:
                    response["status"] = "degraded"
                    response["message"] = warmup_status.error or warmup_status.message

    if not qdrant_vectors.dependency_available():
        response["qdrant"] = "not_installed"
        response["collection_status"] = "unknown"
        response["status"] = "degraded"
        return response

    try:
        manager = qdrant_vectors.get_qdrant_manager(settings)
        manager.ping()
        response["qdrant"] = "connected"
        expected_dimension = response["embedding_dimension"] if response["openclip"] == WARMUP_LOADED else None
        collection_status = manager.get_collection_status(expected_vector_size=expected_dimension)
        response["collection_status"] = collection_status.status
        response["indexed_vectors"] = collection_status.points_count or 0
        if collection_status.vector_size is not None:
            response["collection_vector_size"] = collection_status.vector_size
        if collection_status.distance is not None:
            response["collection_distance"] = collection_status.distance
        if collection_status.message:
            response["message"] = collection_status.message
        if not collection_status.ready:
            response["status"] = "degraded"
    except qdrant_vectors.CollectionCompatibilityError as exc:
        response["qdrant"] = "connected"
        response["collection_status"] = "incompatible"
        response["message"] = str(exc)
        response["status"] = "degraded"
    except qdrant_vectors.QdrantSetupError as exc:
        response["qdrant"] = "unavailable"
        response["collection_status"] = "unknown"
        response["message"] = str(exc)
        response["status"] = "degraded"

    return response


@router.post("/warmup", response_model=AiWarmupResponse, dependencies=[Depends(require_admin)])
def warmup_openclip(request: Request):
    snapshot = _warmup_response(_warmup_service(request).start())
    status_code = status.HTTP_202_ACCEPTED if snapshot.state == WARMUP_LOADING else status.HTTP_200_OK
    return JSONResponse(status_code=status_code, content=snapshot.model_dump(mode="json"))


@router.get("/warmup/status", response_model=AiWarmupResponse, dependencies=[Depends(require_admin)])
def warmup_openclip_status(request: Request) -> AiWarmupResponse:
    return _warmup_response(_warmup_service(request).status())


@router.post("/recognize", response_model=RecognitionResponse)
async def recognize_artifact(
    request: Request,
    image: UploadFile = File(...),
    limit: int | None = Query(default=None, ge=1, le=20),
) -> RecognitionResponse:
    max_bytes = request.app.state.settings.max_image_size_mb * 1024 * 1024
    image_bytes = await image.read(max_bytes + 1)
    await image.close()
    service = ArtifactRecognitionService.from_settings(request.app.state.settings)
    try:
        return service.recognize(
            request.app.state.database,
            image_bytes=image_bytes,
            content_type=image.content_type,
            base_url=str(request.base_url),
            limit=limit,
        )
    except RecognitionInputError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc
    except RecognitionUnavailableError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=AI_UNAVAILABLE_MESSAGE) from exc


@router.post(
    "/index/artifacts/{artifact_id}",
    response_model=AiIndexResultResponse,
    dependencies=[Depends(require_admin)],
)
def index_artifact(artifact_id: str, request: Request) -> AiIndexResultResponse:
    object_id = to_object_id(artifact_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    artifact = artifact_repository.get_artifact(request.app.state.database, object_id)
    if artifact is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    result = ArtifactIndexingService.from_settings(request.app.state.settings).index_artifact(
        request.app.state.database,
        artifact,
    )
    return AiIndexResultResponse(
        artifact_id=result.artifact_id,
        ai_index_status=result.ai_index_status,
        total_images=result.total_images,
        indexed_images=result.indexed_images,
        failed_images=result.failed_images,
        skipped_images=result.skipped_images,
        messages=result.messages,
        errors=result.errors,
    )


@router.post("/index/all", response_model=AiIndexAllResponse, dependencies=[Depends(require_admin)])
def index_all_artifacts(request: Request) -> AiIndexAllResponse:
    result = ArtifactIndexingService.from_settings(request.app.state.settings).index_all(request.app.state.database)
    return AiIndexAllResponse(**result)


@router.post("/index/failed", response_model=AiIndexAllResponse, dependencies=[Depends(require_admin)])
def retry_failed_indexes(request: Request) -> AiIndexAllResponse:
    result = ArtifactIndexingService.from_settings(request.app.state.settings).index_by_status(
        request.app.state.database,
        ["failed", "partial"],
    )
    return AiIndexAllResponse(**result)


@router.post("/index/rebuild", response_model=AiIndexAllResponse, dependencies=[Depends(require_admin)])
def rebuild_artifact_index(request: Request) -> AiIndexAllResponse:
    settings = request.app.state.settings
    if not settings.ai_enabled:
        result = ArtifactIndexingService.from_settings(settings).index_all(request.app.state.database)
        return AiIndexAllResponse(**result)
    qdrant_vectors.get_qdrant_manager(settings).delete_collection_if_exists()
    result = ArtifactIndexingService.from_settings(settings).index_all(request.app.state.database)
    return AiIndexAllResponse(**result)


@router.get("/index/status", response_model=AiIndexStatusResponse, dependencies=[Depends(require_admin)])
def index_status(request: Request) -> AiIndexStatusResponse:
    return build_index_status(request)


def build_index_status(request: Request) -> AiIndexStatusResponse:
    settings = request.app.state.settings
    database = request.app.state.database
    openclip_status = "disabled"
    qdrant_status = "disabled"
    collection_status = "disabled"
    collection_vector_size = None
    collection_distance = None
    indexed_vectors = 0
    message = None

    if settings.ai_enabled:
        openclip_status = "not_installed"
        qdrant_status = "not_installed"
        collection_status = "unknown"
        if openclip_models.dependencies_available():
            manager = openclip_models.get_model_manager(settings)
            if manager.is_loaded:
                openclip_status = WARMUP_LOADED
            else:
                warmup_status = _warmup_service(request).status()
                openclip_status = warmup_status.state if warmup_status.state in {
                    WARMUP_LOADING,
                    WARMUP_FAILED,
                } else WARMUP_IDLE
        if qdrant_vectors.dependency_available():
            try:
                manager = qdrant_vectors.get_qdrant_manager(settings)
                manager.ping()
                qdrant_status = "connected"
                qdrant_collection = manager.get_collection_status()
                collection_status = qdrant_collection.status
                collection_vector_size = qdrant_collection.vector_size
                collection_distance = qdrant_collection.distance
                indexed_vectors = qdrant_collection.points_count or 0
                message = qdrant_collection.message
            except qdrant_vectors.QdrantSetupError as exc:
                qdrant_status = "unavailable"
                message = str(exc)

    return AiIndexStatusResponse(
        total_artifacts=artifact_repository.count_artifacts(database),
        total_images=artifact_repository.count_total_images(database),
        indexed_artifacts=artifact_repository.count_ai_status(database, ["indexed"]),
        pending_artifacts=artifact_repository.count_ai_status(database, ["pending", "not_indexed"]),
        failed_artifacts=artifact_repository.count_ai_status(database, ["failed"]),
        partial_artifacts=artifact_repository.count_ai_status(database, ["partial"]),
        not_indexed_artifacts=artifact_repository.count_ai_status(database, ["not_indexed"]),
        indexed_vectors=indexed_vectors,
        ai_enabled=settings.ai_enabled,
        openclip=openclip_status,
        qdrant=qdrant_status,
        collection=settings.qdrant_collection,
        collection_status=collection_status,
        collection_vector_size=collection_vector_size,
        collection_distance=collection_distance,
        message=message,
    )
