from __future__ import annotations

from fastapi import APIRouter, Request

from app.ai import model_manager as openclip_models
from app.vector import qdrant_manager as qdrant_vectors


router = APIRouter(prefix="/ai", tags=["AI"])


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
        "openclip": "not_loaded",
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
            response["openclip"] = "loaded"
            response["device"] = manager.actual_device
            response["embedding_dimension"] = manager.embedding_dimension

    if not qdrant_vectors.dependency_available():
        response["qdrant"] = "not_installed"
        response["collection_status"] = "unknown"
        response["status"] = "degraded"
        return response

    try:
        manager = qdrant_vectors.get_qdrant_manager(settings)
        manager.ping()
        response["qdrant"] = "connected"
        collection_status = manager.get_collection_status(expected_vector_size=response["embedding_dimension"])
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
