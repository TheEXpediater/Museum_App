from __future__ import annotations

import threading
import time
from pathlib import Path

from bson import ObjectId
from fastapi import HTTPException, status
from pymongo.database import Database

from app.config import Settings
from app.repositories import reconstruction_repository as repo
from app.services.model3d import ai_provider_service, coverage, states
from app.services.model3d.ai_provider import Ai3DProviderError, STATUS_FAILED, STATUS_SUCCEEDED
from app.services.model3d.ai_provider_service import Ai3DConfigurationError
from app.services.model3d.glb_validation import GlbValidationError, validate_external_glb
from app.services.model3d.reconstruction_service import ACTIVE_JOB_CONFLICT_MESSAGE, ReconstructionError
from app.services.model3d.reconstruction_worker import _fail as _preserve_and_fail
from app.utils import to_object_id, utc_now

AI_POLL_INTERVAL_SECONDS = 5
AI_GENERATION_TIMEOUT_SECONDS = 20 * 60

AI_GUIDANCE_NOTICE = (
    "AI-generated preview. Unseen portions may be estimated from the supplied photographs and "
    "may not exactly match the physical artifact."
)


def start_ai_build(
    database: Database, settings: Settings, artifact: dict, *, image_ids: list[str], visible_regions: list[str] | None = None
) -> dict:
    """Deliberate admin action, distinct from the COLMAP build gate: does not require
    states.BUILDABLE_STATES, because AI is specifically offered as an alternative when COLMAP's
    reconstruction is weak or the admin simply prefers it - any status is a valid starting point
    as long as there is at least one reconstruction photo to select from and no other job (COLMAP
    or AI) is already running (the same global one-worker-at-a-time rule as start_build()).
    """
    artifact_id: ObjectId = artifact["_id"]
    if repo.find_active_job(database):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail=ACTIVE_JOB_CONFLICT_MESSAGE)

    availability = ai_provider_service.detect_ai_availability(settings)
    if not availability.available:
        raise ReconstructionError(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=availability.message or "AI 3D preview is not available.",
        )

    if not image_ids:
        raise ReconstructionError(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Select at least one photo.")
    max_images = availability.max_images or 1
    if len(image_ids) > max_images:
        raise ReconstructionError(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Select at most {max_images} photo(s) - the configured AI provider does not accept more.",
        )

    images: list[dict] = []
    for image_id in image_ids:
        object_id = to_object_id(image_id)
        if object_id is None:
            raise ReconstructionError(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid photo selection.")
        document = repo.get_reconstruction_image(database, artifact_id, object_id)
        if document is None:
            raise ReconstructionError(status_code=status.HTTP_404_NOT_FOUND, detail="Selected photo was not found.")
        images.append(document)

    generation_method = (
        states.GENERATION_AI_LOCAL if availability.provider == "triposr" else states.GENERATION_AI_MULTIVIEW
    )

    state = repo.get_model_3d_state(artifact)
    target_version = int(state.get("version") or 0) + 1
    job = repo.create_job(
        database,
        artifact_id=artifact_id,
        target_version=target_version,
        source_image_count=len(images),
        generation_method=generation_method,
        initial_status=states.AI_QUEUED,
        initial_stage_message="Queued for AI 3D generation.",
    )
    supported_percent, inferred_percent, normalized_regions = coverage.estimate_coverage(visible_regions or [])
    job = repo.update_job(
        database, job["_id"],
        {
            "visible_regions": normalized_regions,
            "estimated_supported_percent": supported_percent,
            "estimated_inferred_percent": inferred_percent,
        },
    )
    # A fresh AI build supersedes any unreviewed draft, exactly like start_build() for COLMAP -
    # the previously *accepted* (published) model is untouched and stays visible to visitors.
    repo.update_model_3d_state(
        database, artifact_id,
        {
            "status": states.AI_QUEUED,
            "failure_message": None,
            "draft_version": None,
            "draft_path": None,
            "draft_sha256": None,
            "draft_size_bytes": None,
            "draft_created_at": None,
            "draft_generation_method": None,
        },
    )

    relative_image_paths = [document["relative_path"] for document in images]
    starter = _worker_thread_starter or _default_worker_starter
    starter(database, settings, artifact_id, job["_id"], relative_image_paths)
    return job


_worker_thread_starter = None


def _default_worker_starter(
    database: Database, settings: Settings, artifact_id: ObjectId, job_id: ObjectId, relative_image_paths: list[str]
) -> None:
    thread = threading.Thread(
        target=run,
        args=(database, settings, artifact_id, job_id, relative_image_paths),
        daemon=True,
        name=f"ai-3d-{artifact_id}",
    )
    thread.start()


def set_worker_starter(starter) -> None:
    """Test hook, mirrors reconstruction_service.set_worker_starter: lets tests run the AI
    pipeline (with the provider mocked) synchronously instead of racing a real thread."""
    global _worker_thread_starter
    _worker_thread_starter = starter


def _set_progress(database: Database, job_id: ObjectId, artifact_id: ObjectId, *, status_value: str, stage_message: str) -> None:
    repo.update_job(database, job_id, {"status": status_value, "stage_message": stage_message})
    repo.update_model_3d_state(database, artifact_id, {"status": status_value})


def run(
    database: Database,
    settings: Settings,
    artifact_id: ObjectId,
    job_id: ObjectId,
    relative_image_paths: list[str],
) -> None:
    """Runs one AI generation job end to end. Intended for a background thread - the caller
    (start_ai_build) already enforced the one-active-job-at-a-time rule before this starts."""
    job = repo.get_job(database, job_id)
    target_version = job["target_version"] if job else 1
    generation_method = (job or {}).get("generation_method") or states.GENERATION_AI_MULTIVIEW
    visible_regions = (job or {}).get("visible_regions") or []
    supported_percent = (job or {}).get("estimated_supported_percent")
    inferred_percent = (job or {}).get("estimated_inferred_percent")

    try:
        provider = ai_provider_service.get_provider(settings)
    except Ai3DConfigurationError as exc:
        _preserve_and_fail(database, job_id, artifact_id, message=str(exc))
        return

    image_paths = [settings.reconstruction_root_path / relative_path for relative_path in relative_image_paths]
    missing = [str(path) for path in image_paths if not path.is_file()]
    if missing:
        _preserve_and_fail(database, job_id, artifact_id, message="One or more selected source photos are missing on disk.")
        return

    generating_message = (
        "Generating the 3D preview locally." if generation_method == states.GENERATION_AI_LOCAL
        else "Sending photos to the AI 3D service."
    )
    _set_progress(database, job_id, artifact_id, status_value=states.AI_GENERATING, stage_message=generating_message)
    try:
        handle = provider.create_generation(image_paths)
    except (Ai3DProviderError, ValueError) as exc:
        _preserve_and_fail(database, job_id, artifact_id, message=f"AI 3D generation could not start: {exc}")
        return

    deadline = time.monotonic() + AI_GENERATION_TIMEOUT_SECONDS
    glb_url: str | None = None
    while True:
        time.sleep(AI_POLL_INTERVAL_SECONDS)
        try:
            result = provider.get_generation_status(handle.provider_task_id)
        except Ai3DProviderError as exc:
            _preserve_and_fail(database, job_id, artifact_id, message=f"AI 3D generation status check failed: {exc}")
            return

        if result.status == STATUS_SUCCEEDED:
            glb_url = result.glb_url
            break
        if result.status == STATUS_FAILED:
            _preserve_and_fail(database, job_id, artifact_id, message=result.error_message or "AI 3D generation failed.")
            return
        progress_text = f" ({result.progress}%)" if result.progress is not None else ""
        _set_progress(
            database, job_id, artifact_id,
            status_value=states.AI_GENERATING,
            stage_message=f"Generating with AI{progress_text}.",
        )
        if time.monotonic() > deadline:
            _preserve_and_fail(database, job_id, artifact_id, message="AI 3D generation timed out.")
            return

    if not glb_url:
        _preserve_and_fail(database, job_id, artifact_id, message="AI 3D generation finished without a downloadable result.")
        return

    _set_progress(database, job_id, artifact_id, status_value=states.AI_DOWNLOADING, stage_message="Downloading the generated 3D model.")
    staging_dir = settings.reconstruction_root_path / str(artifact_id) / "ai_staging"
    staging_path = staging_dir / f"model-v{target_version}.glb"
    try:
        provider.download_result(glb_url, staging_path)
    except Ai3DProviderError as exc:
        _preserve_and_fail(database, job_id, artifact_id, message=f"Downloading the AI 3D model failed: {exc}")
        return

    _set_progress(database, job_id, artifact_id, status_value=states.AI_VALIDATING, stage_message="Validating the generated 3D model.")
    try:
        validated = validate_external_glb(staging_path)
    except GlbValidationError as exc:
        _preserve_and_fail(database, job_id, artifact_id, message=f"AI 3D model failed validation: {exc}")
        return

    destination = settings.model_3d_root_path / str(artifact_id) / f"model-v{target_version}.glb"
    destination.parent.mkdir(parents=True, exist_ok=True)
    staging_path.replace(destination)
    relative_glb_path = destination.relative_to(settings.model_3d_root_path.parent.parent).as_posix()

    repo.update_job(database, job_id, {"status": states.PENDING_REVIEW, "stage_message": "AI 3D preview ready for review.", "finished_at": utc_now()})
    repo.update_model_3d_state(
        database, artifact_id,
        {
            "status": states.PENDING_REVIEW,
            "draft_version": target_version,
            "draft_path": relative_glb_path,
            "draft_sha256": validated.sha256,
            "draft_size_bytes": validated.size_bytes,
            "draft_created_at": utc_now(),
            "draft_generation_method": generation_method,
            "draft_visible_regions": visible_regions,
            "draft_estimated_supported_percent": supported_percent,
            "draft_estimated_inferred_percent": inferred_percent,
            # AI drafts are never auto-scored by the COLMAP quality gate (there are no
            # registered-image/sparse-point metrics for them) - the guidance notice below is
            # what Admin UI shows instead, and Accept/Reject is the actual quality gate.
            "quality_assessment": None,
            "quality_reasons": [],
            "failure_message": None,
            "guidance": [AI_GUIDANCE_NOTICE],
        },
    )
