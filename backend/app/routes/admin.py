from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, Request

from app.auth.dependencies import require_admin
from app.ai import model_manager as openclip_models
from app.repositories import artifact_repository
from app.schemas.admin import DashboardRecentArtifact, DashboardSummaryResponse
from app.services.image_storage import image_url_for_path
from app.vector import qdrant_manager as qdrant_vectors


router = APIRouter(prefix="/admin", tags=["Admin"], dependencies=[Depends(require_admin)])


@router.get("/dashboard", response_model=DashboardSummaryResponse)
def dashboard(request: Request) -> DashboardSummaryResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    database_status = database_status_for(request)
    uploads_status = "available" if settings.upload_path.exists() and settings.upload_path.is_dir() else "unavailable"
    ai_status = "disabled"
    indexed_vectors = 0

    if settings.ai_enabled:
        ai_status = "healthy"
        if not openclip_models.dependencies_available():
            ai_status = "degraded"
        if qdrant_vectors.dependency_available():
            try:
                manager = qdrant_vectors.get_qdrant_manager(settings)
                manager.ping()
                indexed_vectors = manager.count_vectors()
            except qdrant_vectors.QdrantSetupError:
                ai_status = "degraded"
        else:
            ai_status = "degraded"

    recent = [
        serialize_recent_artifact(item, str(request.base_url))
        for item in artifact_repository.list_recent_artifacts(database, limit=5)
    ]
    return DashboardSummaryResponse(
        total_artifacts=artifact_repository.count_artifacts(database),
        total_images=artifact_repository.count_total_images(database),
        total_categories=artifact_repository.count_categories(database),
        indexed_artifacts=artifact_repository.count_ai_status(database, ["indexed"]),
        pending_artifacts=artifact_repository.count_ai_status(database, ["pending", "not_indexed", "partial"]),
        failed_artifacts=artifact_repository.count_ai_status(database, ["failed"]),
        indexed_vectors=indexed_vectors,
        ai_status=ai_status,
        database_status=database_status,
        uploads_status=uploads_status,
        recent_artifacts=recent,
    )


def database_status_for(request: Request) -> str:
    try:
        request.app.state.database.command("ping")
        return "connected"
    except Exception:
        return "connected" if request.app.state.external_database else "unavailable"


def serialize_recent_artifact(document: dict, base_url: str) -> DashboardRecentArtifact:
    created_at = document.get("created_at")
    return DashboardRecentArtifact(
        id=str(document["_id"]),
        artifact_code=document["artifact_code"],
        name=document["name"],
        category=document["category"],
        primary_image_url=image_url_for_path(base_url, document.get("primary_image_path")),
        ai_index_status=document.get("ai_index_status"),
        created_at=created_at.isoformat() if isinstance(created_at, datetime) else str(created_at),
    )
