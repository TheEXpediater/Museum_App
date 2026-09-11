from __future__ import annotations

from typing import Any

from bson import ObjectId
from pymongo import ASCENDING, DESCENDING
from pymongo.collection import Collection
from pymongo.database import Database

from app.repositories import artifact_repository
from app.services.model3d import states
from app.utils import utc_now

DEFAULT_MODEL_3D_STATE: dict[str, Any] = {
    "status": states.NONE,
    # Published/accepted model - what the visitor API exposes.
    "version": 0,
    "path": None,
    "sha256": None,
    "size_bytes": None,
    "created_at": None,
    # How the published model was produced (states.GENERATION_COLMAP / GENERATION_AI_MULTIVIEW).
    "generation_method": None,
    # Draft model awaiting admin review - never exposed to visitors. Populated when a build
    # reaches PENDING_REVIEW; cleared on Accept (folded into the published fields above) or
    # Reject (discarded).
    "draft_version": None,
    "draft_path": None,
    "draft_sha256": None,
    "draft_size_bytes": None,
    "draft_created_at": None,
    "draft_generation_method": None,
    "source_image_count": 0,
    "registered_image_count": None,
    "registered_image_ratio": None,
    "sparse_point_count": None,
    "mean_reprojection_error": None,
    # Coarse pass/fail verdict from quality.assess_reconstruction_quality(), plus the specific
    # reasons - set by preflight (sparse-only) and refined by the worker (sparse + mesh) once a
    # draft exists. Never blocks status transitions by itself; see quality.py for why.
    "quality_assessment": None,
    "quality_reasons": [],
    "failure_message": None,
    "guidance": [],
    # Estimated coverage for the published/draft AI model (see quality.py's ESTIMATED_COVERAGE_
    # REGIONS and ai_generation_service.start_ai_build). Never a claimed model confidence value -
    # always a heuristic derived from which of the 6 major regions the admin marked as visible in
    # the generation image. None/empty means "coverage estimate unavailable", not 0%.
    "visible_regions": [],
    "estimated_supported_percent": None,
    "estimated_inferred_percent": None,
    "draft_visible_regions": [],
    "draft_estimated_supported_percent": None,
    "draft_estimated_inferred_percent": None,
}


def images_collection(database: Database) -> Collection:
    return database.reconstruction_images


def jobs_collection(database: Database) -> Collection:
    return database.reconstruction_jobs


def get_model_3d_state(artifact: dict[str, Any]) -> dict[str, Any]:
    return {**DEFAULT_MODEL_3D_STATE, **(artifact.get("model_3d") or {})}


def update_model_3d_state(database: Database, artifact_id: ObjectId, updates: dict[str, Any]) -> dict | None:
    current = artifact_repository.get_artifact(database, artifact_id) or {}
    merged = {**get_model_3d_state(current), **updates}
    return artifact_repository.update_artifact(database, artifact_id, {"model_3d": merged})


def add_reconstruction_image(
    database: Database,
    *,
    artifact_id: ObjectId,
    relative_path: str,
    origin: str,
    original_filename: str | None,
    width: int,
    height: int,
    digest: str,
) -> dict:
    document = {
        "artifact_id": artifact_id,
        "relative_path": relative_path,
        "origin": origin,
        "original_filename": original_filename,
        "width": width,
        "height": height,
        "digest": digest,
        "created_at": utc_now(),
    }
    result = images_collection(database).insert_one(document)
    document["_id"] = result.inserted_id
    return document


def list_reconstruction_images(database: Database, artifact_id: ObjectId) -> list[dict]:
    return list(images_collection(database).find({"artifact_id": artifact_id}).sort([("created_at", ASCENDING)]))


def get_reconstruction_image(database: Database, artifact_id: ObjectId, image_id: ObjectId) -> dict | None:
    return images_collection(database).find_one({"_id": image_id, "artifact_id": artifact_id})


def delete_reconstruction_image(database: Database, artifact_id: ObjectId, image_id: ObjectId) -> dict | None:
    return images_collection(database).find_one_and_delete({"_id": image_id, "artifact_id": artifact_id})


def delete_all_reconstruction_images(database: Database, artifact_id: ObjectId) -> list[dict]:
    documents = list_reconstruction_images(database, artifact_id)
    images_collection(database).delete_many({"artifact_id": artifact_id})
    return documents


def image_digest_exists(database: Database, artifact_id: ObjectId, digest: str) -> bool:
    return images_collection(database).find_one({"artifact_id": artifact_id, "digest": digest}) is not None


def create_job(
    database: Database,
    *,
    artifact_id: ObjectId,
    target_version: int,
    source_image_count: int,
    generation_method: str = states.GENERATION_COLMAP,
    initial_status: str = states.QUEUED,
    initial_stage_message: str = "Queued for reconstruction.",
) -> dict:
    now = utc_now()
    document = {
        "artifact_id": artifact_id,
        "status": initial_status,
        "stage_message": initial_stage_message,
        "target_version": target_version,
        "source_image_count": source_image_count,
        "generation_method": generation_method,
        "registered_image_count": None,
        "registered_image_ratio": None,
        "sparse_point_count": None,
        "mean_reprojection_error": None,
        "error": None,
        "log_path": None,
        "created_at": now,
        "updated_at": now,
        "started_at": None,
        "finished_at": None,
    }
    result = jobs_collection(database).insert_one(document)
    document["_id"] = result.inserted_id
    return document


def update_job(database: Database, job_id: ObjectId, updates: dict[str, Any]) -> dict | None:
    updates = {**updates, "updated_at": utc_now()}
    jobs_collection(database).update_one({"_id": job_id}, {"$set": updates})
    return jobs_collection(database).find_one({"_id": job_id})


def get_job(database: Database, job_id: ObjectId) -> dict | None:
    return jobs_collection(database).find_one({"_id": job_id})


def get_latest_job_for_artifact(database: Database, artifact_id: ObjectId) -> dict | None:
    return jobs_collection(database).find_one({"artifact_id": artifact_id}, sort=[("created_at", DESCENDING)])


def find_active_job(database: Database) -> dict | None:
    return jobs_collection(database).find_one({"status": {"$in": list(states.ACTIVE_JOB_STATES)}})


def find_active_job_for_artifact(database: Database, artifact_id: ObjectId) -> dict | None:
    return jobs_collection(database).find_one({"artifact_id": artifact_id, "status": {"$in": list(states.ACTIVE_JOB_STATES)}})


def reconcile_abandoned_jobs(database: Database) -> list[dict]:
    """Mark any job left in an active-running state as interrupted.

    Called once at application startup: if the process restarted while a job was running, no
    worker thread survived to keep progressing it, so pretending it is still running would be
    misleading to the admin UI.
    """
    abandoned = list(jobs_collection(database).find({"status": {"$in": list(states.ACTIVE_JOB_STATES)}}))
    for job in abandoned:
        jobs_collection(database).update_one(
            {"_id": job["_id"]},
            {"$set": {"status": states.INTERRUPTED, "stage_message": "Interrupted by a backend restart.", "updated_at": utc_now()}},
        )
        artifact = artifact_repository.get_artifact(database, job["artifact_id"])
        if artifact is not None and get_model_3d_state(artifact)["status"] in states.ACTIVE_JOB_STATES:
            update_model_3d_state(
                database,
                job["artifact_id"],
                {"status": states.INTERRUPTED, "failure_message": "Interrupted by a backend restart."},
            )
    return abandoned
